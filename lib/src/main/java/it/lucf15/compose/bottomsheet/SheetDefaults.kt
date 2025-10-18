/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.lucf15.compose.bottomsheet

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import it.lucf15.compose.bottomsheet.SheetState.Companion.Saver
import it.lucf15.compose.bottomsheet.SheetValue.Expanded
import it.lucf15.compose.bottomsheet.SheetValue.Hidden
import it.lucf15.compose.bottomsheet.SheetValue.PartiallyExpanded
import kotlinx.coroutines.CancellationException

/**
 * State of a sheet composable, such as [ModalBottomSheet]
 *
 * Contains states relating to its swipe position as well as animations between state values.
 *
 * @param skipPartiallyExpanded Whether the partially expanded state, if the sheet is large enough,
 *   should be skipped. If true, the sheet will always expand to the [Expanded] state and move to
 *   the [Hidden] state if available when hiding the sheet, either programmatically or by user
 *   interaction.
 * @param positionalThreshold The positional threshold, in px, to be used when calculating the
 *   target state while a drag is in progress and when settling after the drag ends. This is the
 *   distance from the start of a transition. It will be, depending on the direction of the
 *   interaction, added or subtracted from/to the origin offset. It should always be a positive
 *   value.
 * @param velocityThreshold The velocity threshold (in px per second) that the end velocity has to
 *   exceed in order to animate to the next state, even if the [positionalThreshold] has not been
 *   reached.
 * @param initialValue The initial value of the state.
 * @param confirmValueChange Optional callback invoked to confirm or veto a pending state change.
 * @param skipHiddenState Whether the hidden state should be skipped. If true, the sheet will always
 *   expand to the [Expanded] state and move to the [PartiallyExpanded] if available, either
 *   programmatically or by user interaction.
 */
@Stable
class SheetState(
    internal val skipPartiallyExpanded: Boolean,
    positionalThreshold: (Float) -> Float,
    velocityThreshold: () -> Float,
    initialValue: SheetValue = Hidden,
    internal val confirmValueChange: (SheetValue) -> Boolean = { true },
    internal val skipHiddenState: Boolean = false,
) {

    init {
        if (skipPartiallyExpanded) {
            require(initialValue != PartiallyExpanded) {
                "The initial value must not be set to PartiallyExpanded if skipPartiallyExpanded " +
                        "is set to true."
            }
        }
        if (skipHiddenState) {
            require(initialValue != Hidden) {
                "The initial value must not be set to Hidden if skipHiddenState is set to true."
            }
        }
    }

    /**
     * The current value of the state.
     *
     * If no swipe or animation is in progress, this corresponds to the state the bottom sheet is
     * currently in. If a swipe or an animation is in progress, this corresponds the state the sheet
     * was in before the swipe or animation started.
     */
    val currentValue: SheetValue
        get() = anchoredDraggableState.currentValue

    /**
     * The target value of the bottom sheet state.
     *
     * If a swipe is in progress, this is the value that the sheet would animate to if the swipe
     * finishes. If an animation is running, this is the target value of that animation. Finally, if
     * no swipe or animation is in progress, this is the same as the [currentValue].
     */
    val targetValue: SheetValue
        get() = anchoredDraggableState.targetValue

    /** Whether the modal bottom sheet is visible. */
    val isVisible: Boolean
        get() = anchoredDraggableState.currentValue != Hidden

    /**
     * Whether an expanding or collapsing sheet animation is currently in progress.
     *
     * See [expand], [partialExpand], [show] or [hide] for more information.
     */
    val isAnimationRunning: Boolean
        get() = anchoredDraggableState.isAnimationRunning

    /**
     * Require the current offset (in pixels) of the bottom sheet.
     *
     * The offset will be initialized during the first measurement phase of the provided sheet
     * content.
     *
     * These are the phases: Composition { -> Effects } -> Layout { Measurement -> Placement } ->
     * Drawing
     *
     * During the first composition, an [IllegalStateException] is thrown. In subsequent
     * compositions, the offset will be derived from the anchors of the previous pass. Always prefer
     * accessing the offset from a LaunchedEffect as it will be scheduled to be executed the next
     * frame, after layout.
     *
     * @throws IllegalStateException If the offset has not been initialized yet
     */
    fun requireOffset(): Float = anchoredDraggableState.requireOffset()

    /** Whether the sheet has an expanded state defined. */
    val hasExpandedState: Boolean
        get() = anchoredDraggableState.anchors.hasAnchorFor(Expanded)

    /** Whether the modal bottom sheet has a partially expanded state defined. */
    val hasPartiallyExpandedState: Boolean
        get() = anchoredDraggableState.anchors.hasAnchorFor(PartiallyExpanded)

    /**
     * If [confirmValueChange] returns true, fully expand the bottom sheet with animation and
     * suspend until it is fully expanded or animation has been cancelled.
     *
     * @throws [CancellationException] if the animation is interrupted
     */
    suspend fun expand() {
        if (confirmValueChange(Expanded)) animateTo(Expanded, showMotionSpec)
    }

    /**
     * If [confirmValueChange] returns true, animate the bottom sheet and suspend until it is
     * partially expanded or animation has been cancelled.
     *
     * @throws [CancellationException] if the animation is interrupted
     * @throws [IllegalStateException] if [skipPartiallyExpanded] is set to true
     */
    suspend fun partialExpand() {
        check(!skipPartiallyExpanded) {
            "Attempted to animate to partial expanded when skipPartiallyExpanded was enabled. Set" +
                    " skipPartiallyExpanded to false to use this function."
        }
        if (confirmValueChange(PartiallyExpanded)) animateTo(PartiallyExpanded, showMotionSpec)
    }

    /**
     * If [confirmValueChange] returns true, expand the bottom sheet with animation and suspend
     * until it is [PartiallyExpanded] if defined, else [Expanded].
     *
     * @throws [CancellationException] if the animation is interrupted
     */
    suspend fun show() {
        val targetValue =
            when {
                hasPartiallyExpandedState -> PartiallyExpanded
                else -> Expanded
            }
        if (confirmValueChange(targetValue)) animateTo(targetValue, showMotionSpec)
    }

    /**
     * If [confirmValueChange] returns true, hide the bottom sheet with animation and suspend until
     * it is fully hidden or animation has been cancelled.
     *
     * @throws [CancellationException] if the animation is interrupted
     */
    suspend fun hide() {
        check(!skipHiddenState) {
            "Attempted to animate to hidden when skipHiddenState was enabled. Set skipHiddenState" +
                    " to false to use this function."
        }
        if (confirmValueChange(Hidden)) animateTo(Hidden, hideMotionSpec)
    }

    /**
     * Animate to a [targetValue]. If the [targetValue] is not in the set of anchors, the
     * [currentValue] will be updated to the [targetValue] without updating the offset.
     *
     * @param targetValue The target value of the animation
     * @param animationSpec an [AnimationSpec]
     * @param velocity an initial velocity for the animation
     * @throws CancellationException if the interaction interrupted by another interaction like a
     *   gesture interaction or another programmatic interaction like a [animateTo] or [snapTo]
     *   call.
     */
    internal suspend fun animateTo(
        targetValue: SheetValue,
        animationSpec: FiniteAnimationSpec<Float>,
        velocity: Float = anchoredDraggableState.lastVelocity,
    ) {
        anchoredDraggableState.anchoredDrag(targetValue = targetValue) { anchors, latestTarget ->
            val targetOffset = anchors.positionOf(latestTarget)
            if (!targetOffset.isNaN()) {
                var prev = if (offset.isNaN()) 0f else offset
                animate(prev, targetOffset, velocity, animationSpec) { value, velocity ->
                    // Our onDrag coerces the value within the bounds, but an animation may
                    // overshoot, for example a spring animation or an overshooting interpolator
                    // We respect the user's intention and allow the overshoot, but still use
                    // DraggableState's drag for its mutex.
                    dragTo(value, velocity)
                    prev = value
                }
            }
        }
    }

    /**
     * Snap to a [targetValue] without any animation.
     *
     * @param targetValue The target value of the animation
     * @throws CancellationException if the interaction interrupted by another interaction like a
     *   gesture interaction or another programmatic interaction like a [animateTo] or [snapTo]
     *   call.
     */
    internal suspend fun snapTo(targetValue: SheetValue) {
        anchoredDraggableState.snapTo(targetValue)
    }

    /**
     * Find the closest anchor taking into account the velocity and settle at it with an animation.
     */
    internal suspend fun settle(velocity: Float) {
        anchoredDraggableState.settle(velocity)
    }

    internal var anchoredDraggableMotionSpec: AnimationSpec<Float> = BottomSheetAnimationSpec

    internal var anchoredDraggableState =
        AnchoredDraggableState(
            initialValue = initialValue,
            animationSpec = { anchoredDraggableMotionSpec },
            confirmValueChange = confirmValueChange,
            positionalThreshold = positionalThreshold,
            velocityThreshold = velocityThreshold,
        )

    internal val offset: Float
        get() = anchoredDraggableState.offset

    internal var showMotionSpec: FiniteAnimationSpec<Float> = BottomSheetAnimationSpec
    internal var hideMotionSpec: FiniteAnimationSpec<Float> = BottomSheetAnimationSpec

    val scrimAlpha: Float
        get() {
            val hiddenOffset = anchoredDraggableState.anchors.positionOf(Hidden)
            val expandedOffset = anchoredDraggableState.anchors.positionOf(Expanded)

            if (hiddenOffset.isNaN() || expandedOffset.isNaN() || offset.isNaN()) return 0f

            val range = expandedOffset - hiddenOffset
            if (range == 0f) return 1f

            return ((offset - hiddenOffset) / range).coerceIn(0f, 1f)
        }

    companion object {
        /** The default [Saver] implementation for [SheetState]. */
        fun Saver(
            skipPartiallyExpanded: Boolean,
            positionalThreshold: (Float) -> Float,
            velocityThreshold: () -> Float,
            confirmValueChange: (SheetValue) -> Boolean,
            skipHiddenState: Boolean,
        ) =
            androidx.compose.runtime.saveable.Saver<SheetState, SheetValue>(
                save = { it.currentValue },
                restore = { savedValue ->
                    SheetState(
                        skipPartiallyExpanded,
                        positionalThreshold,
                        velocityThreshold,
                        savedValue,
                        confirmValueChange,
                        skipHiddenState,
                    )
                }
            )
    }
}

/** Possible values of [SheetState]. */
enum class SheetValue {
    /** The sheet is not visible. */
    Hidden,

    /** The sheet is visible at full height. */
    Expanded,

    /** The sheet is partially visible. */
    PartiallyExpanded,
}

/** Contains the default values used by [ModalBottomSheet] and [BottomSheetScaffold]. */
@Stable
object BottomSheetDefaults {
    val SheetMaxWidth = 640.dp

    /** Default insets to be used and consumed by the [ModalBottomSheet]'s content. */
    val windowInsets: WindowInsets
        @Composable
        get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Top)

    internal val VelocityThreshold = 125.dp
}

@Suppress("FunctionName")
internal fun ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection(
    sheetState: SheetState,
    orientation: Orientation,
    nestedScrollableState: ScrollableState?,
    onFling: (velocity: Float) -> Unit,
): NestedScrollConnection = object : NestedScrollConnection {

    val isChildAtTop: Boolean
        get() = nestedScrollableState == null || !nestedScrollableState.canScrollBackward

    var wasChildAtTop: Boolean = isChildAtTop

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.toFloat()
        return if (delta < 0 && source == NestedScrollSource.UserInput) {
            sheetState.anchoredDraggableState.dispatchRawDelta(delta).toOffset()
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val delta = available.toFloat()
        return if (source == NestedScrollSource.UserInput) {
            if (delta > 0) {
                if (wasChildAtTop) {
                    sheetState.anchoredDraggableState.dispatchRawDelta(delta).toOffset()
                } else {
                    Offset.Zero
                }
            } else {
                wasChildAtTop = isChildAtTop
                sheetState.anchoredDraggableState.dispatchRawDelta(delta).toOffset()
            }
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val toFling = available.toFloat()
        val currentOffset = sheetState.requireOffset()
        val minAnchor = sheetState.anchoredDraggableState.anchors.minAnchor()
        return if (toFling < 0 && currentOffset > minAnchor) {
            onFling(toFling)
            // since we go to the anchor with tween settling, consume all for the best UX
            available
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        wasChildAtTop = isChildAtTop
        val toFling = available.toFloat()
        if (isChildAtTop) {
            onFling(toFling)
        }
        return available
    }

    private fun Float.toOffset(): Offset =
        Offset(
            x = if (orientation == Orientation.Horizontal) this else 0f,
            y = if (orientation == Orientation.Vertical) this else 0f
        )

    @JvmName("velocityToFloat")
    private fun Velocity.toFloat() = if (orientation == Orientation.Horizontal) x else y

    @JvmName("offsetToFloat")
    private fun Offset.toFloat(): Float = if (orientation == Orientation.Horizontal) x else y
}

@Composable
internal fun rememberSheetState(
    skipPartiallyExpanded: Boolean = false,
    confirmValueChange: (SheetValue) -> Boolean = { true },
    initialValue: SheetValue = Hidden,
    skipHiddenState: Boolean = false,
    positionalThreshold: (Float) -> Float = { it * 0.5f },
    velocityThreshold: Dp = BottomSheetDefaults.VelocityThreshold,
): SheetState {
    val density = LocalDensity.current
    val velocityThresholdToPx = { with(density) { velocityThreshold.toPx() } }
    return rememberSaveable(
        skipPartiallyExpanded,
        confirmValueChange,
        skipHiddenState,
        saver = SheetState.Saver(
            skipPartiallyExpanded = skipPartiallyExpanded,
            positionalThreshold = positionalThreshold,
            velocityThreshold = velocityThresholdToPx,
            confirmValueChange = confirmValueChange,
            skipHiddenState = skipHiddenState,
        )
    ) {
        SheetState(
            skipPartiallyExpanded,
            positionalThreshold,
            velocityThresholdToPx,
            initialValue,
            confirmValueChange,
            skipHiddenState,
        )
    }
}