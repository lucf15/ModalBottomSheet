/*
 * Copyright (C) 2024 The Android Open Source Project
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

@file:OptIn(ExperimentalMaterial3Api::class)

package it.lucf15.compose.bottomsheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import it.lucf15.compose.bottomsheet.SheetValue.Expanded
import it.lucf15.compose.bottomsheet.SheetValue.Hidden
import it.lucf15.compose.bottomsheet.SheetValue.PartiallyExpanded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import androidx.compose.material3.R as MaterialR

@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    nestedScrollableState: ScrollableState? = null,
    shape: Shape = androidx.compose.material3.BottomSheetDefaults.ExpandedShape,
    containerColor: Color = androidx.compose.material3.BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = androidx.compose.material3.BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    showMotion: FiniteAnimationSpec<Float> = BottomSheetAnimationSpec,
    hideMotion: FiniteAnimationSpec<Float> = BottomSheetAnimationSpec,
    anchoredDraggableMotionSpec: FiniteAnimationSpec<Float> = BottomSheetAnimationSpec,
    content: @Composable ColumnScope.() -> Unit,
) {

    SideEffect {
        sheetState.showMotionSpec = showMotion
        sheetState.hideMotionSpec = hideMotion
        sheetState.anchoredDraggableMotionSpec = anchoredDraggableMotionSpec
    }

    val scope = rememberCoroutineScope()
    val animateToDismiss: () -> Unit = {
        if (sheetState.anchoredDraggableState.confirmValueChange(Hidden)) {
            scope
                .launch { sheetState.hide() }
                .invokeOnCompletion {
                    if (!sheetState.isVisible) {
                        onDismissRequest()
                    }
                }
        }
    }
    val settleToDismiss: (velocity: Float) -> Unit = {
        scope
            .launch { sheetState.settle(it) }
            .invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
    }

    val predictiveBackProgress = remember { Animatable(initialValue = 0f) }

    ModalBottomSheetDialog(
        properties = properties,
        contentColor = contentColor,
        onDismissRequest = {
            if (sheetState.currentValue == Expanded && sheetState.hasPartiallyExpandedState) {
                // Smoothly animate away predictive back transformations since we are not fully
                // dismissing. We don't need to do this in the else below because we want to
                // preserve the predictive back transformations (scale) during the hide animation.
                scope.launch { predictiveBackProgress.animateTo(0f) }
                scope.launch { sheetState.partialExpand() }
            } else { // Is expanded without collapsed state or is collapsed.
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
            }
        },
        predictiveBackProgress = predictiveBackProgress,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { isTraversalGroup = true }) {
            Scrim(
                color = scrimColor,
                onDismissRequest = animateToDismiss,
                visible = sheetState.targetValue != Hidden,
                alpha = { sheetState.scrimAlpha },
            )
            ModalBottomSheetContent(
                predictiveBackProgress = predictiveBackProgress,
                scope = scope,
                animateToDismiss = animateToDismiss,
                settleToDismiss = settleToDismiss,
                nestedScrollableState = nestedScrollableState,
                modifier = modifier,
                sheetState = sheetState,
                sheetMaxWidth = sheetMaxWidth,
                sheetGesturesEnabled = sheetGesturesEnabled,
                shape = shape,
                containerColor = containerColor,
                contentColor = contentColor,
                tonalElevation = tonalElevation,
                dragHandle = dragHandle,
                contentWindowInsets = contentWindowInsets,
                content = content
            )
        }
    }
    if (sheetState.hasExpandedState) {
        LaunchedEffect(sheetState) { sheetState.show() }
    }
}

@Composable
internal fun BoxScope.ModalBottomSheetContent(
    predictiveBackProgress: Animatable<Float, AnimationVector1D>,
    scope: CoroutineScope,
    animateToDismiss: () -> Unit,
    settleToDismiss: (velocity: Float) -> Unit,
    nestedScrollableState: ScrollableState? = null,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape,
    containerColor: Color,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = androidx.compose.material3.BottomSheetDefaults.Elevation,
    dragHandle: @Composable (() -> Unit)?,
    contentWindowInsets: @Composable () -> WindowInsets,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bottomSheetPaneTitle = getString(string = Strings.BottomSheetPaneTitle)

    Surface(
        modifier =
            modifier
                .align(Alignment.TopCenter)
                .widthIn(max = sheetMaxWidth)
                .fillMaxWidth()
                .then(
                    if (sheetGesturesEnabled)
                        Modifier.nestedScroll(
                            remember(sheetState) {
                                ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection(
                                    sheetState = sheetState,
                                    nestedScrollableState = nestedScrollableState,
                                    orientation = Orientation.Vertical,
                                    onFling = { settleToDismiss(it) }
                                )
                            }
                        )
                    else Modifier
                )
                .draggableAnchors(sheetState.anchoredDraggableState, Orientation.Vertical) { sheetSize, constraints ->
                    val fullHeight = constraints.maxHeight.toFloat()
                    val newAnchors = DraggableAnchors {
                        Hidden at fullHeight
                        if (sheetSize.height > (fullHeight / 2) && !sheetState.skipPartiallyExpanded) {
                            PartiallyExpanded at fullHeight / 2f
                        }
                        if (sheetSize.height != 0) {
                            Expanded at max(0f, fullHeight - sheetSize.height)
                        }
                    }
                    val newTarget =
                        when (sheetState.anchoredDraggableState.targetValue) {
                            Hidden -> Hidden
                            PartiallyExpanded -> {
                                val hasPartiallyExpandedState = newAnchors.hasAnchorFor(PartiallyExpanded)
                                val newTarget = if (hasPartiallyExpandedState) PartiallyExpanded
                                else if (newAnchors.hasAnchorFor(Expanded)) Expanded else Hidden
                                newTarget
                            }

                            Expanded -> if (newAnchors.hasAnchorFor(Expanded)) Expanded else Hidden
                        }
                    return@draggableAnchors newAnchors to newTarget
                }
                .draggable(
                    state = sheetState.anchoredDraggableState.draggableState,
                    orientation = Orientation.Vertical,
                    enabled = sheetGesturesEnabled && sheetState.isVisible,
                    startDragImmediately = sheetState.isAnimationRunning,
                    onDragStopped = { settleToDismiss(it) }
                )
                .semantics {
                    paneTitle = bottomSheetPaneTitle
                    traversalIndex = 0f
                }
                .consumeWindowInsets(WindowInsets(top = sheetState.offset.toInt().coerceAtLeast(0)))
                .graphicsLayer {
                    val sheetOffset = sheetState.anchoredDraggableState.offset
                    val sheetHeight = size.height
                    if (!sheetOffset.isNaN() && !sheetHeight.isNaN() && sheetHeight != 0f) {
                        val progress = predictiveBackProgress.value
                        scaleX = calculatePredictiveBackScaleX(progress)
                        scaleY = calculatePredictiveBackScaleY(progress)
                        transformOrigin =
                            TransformOrigin(0.5f, (sheetOffset + sheetHeight) / sheetHeight)
                    }
                },
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(contentWindowInsets())
                .graphicsLayer {
                    val progress = predictiveBackProgress.value
                    val predictiveBackScaleX = calculatePredictiveBackScaleX(progress)
                    val predictiveBackScaleY = calculatePredictiveBackScaleY(progress)

                    // Preserve the original aspect ratio and alignment of the child content.
                    scaleY =
                        if (predictiveBackScaleY != 0f) predictiveBackScaleX / predictiveBackScaleY
                        else 1f
                    transformOrigin = PredictiveBackChildTransformOrigin
                }
        ) {
            val collapseActionLabel = getString(Strings.BottomSheetPartialExpandDescription)
            val dismissActionLabel = getString(Strings.BottomSheetDismissDescription)
            val expandActionLabel = getString(Strings.BottomSheetExpandDescription)
            if (dragHandle != null) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .semantics(
                            mergeDescendants = true
                        ) {
                            // Provides semantics to interact with the bottomsheet based on its
                            // current value.
                            with(sheetState) {
                                dismiss(dismissActionLabel) {
                                    animateToDismiss()
                                    true
                                }
                                if (currentValue == PartiallyExpanded) {
                                    expand(expandActionLabel) {
                                        if (anchoredDraggableState.confirmValueChange(Expanded)) {
                                            scope.launch { sheetState.expand() }
                                        }
                                        true
                                    }
                                } else if (hasPartiallyExpandedState) {
                                    collapse(collapseActionLabel) {
                                        if (
                                            anchoredDraggableState.confirmValueChange(PartiallyExpanded)
                                        ) {
                                            scope.launch { partialExpand() }
                                        }
                                        true
                                    }
                                }
                            }
                        }
                ) {
                    dragHandle()
                }
            }
            content()
        }
    }
}

private fun GraphicsLayerScope.calculatePredictiveBackScaleX(progress: Float): Float {
    val width = size.width
    return if (width.isNaN() || width == 0f) {
        1f
    } else {
        1f - lerp(0f, min(PredictiveBackMaxScaleXDistance.toPx(), width), progress) / width
    }
}

private fun GraphicsLayerScope.calculatePredictiveBackScaleY(progress: Float): Float {
    val height = size.height
    return if (height.isNaN() || height == 0f) {
        1f
    } else {
        1f - lerp(0f, min(PredictiveBackMaxScaleYDistance.toPx(), height), progress) / height
    }
}


/** Default values for [ModalBottomSheet] */
@Immutable
object ModalBottomSheetDefaults {

    /** Properties used to customize the behavior of a [ModalBottomSheet]. */
    val properties: ModalBottomSheetProperties =
        ModalBottomSheetProperties()
}

/**
 * Create and [remember] a [SheetState] for [ModalBottomSheet].
 *
 * @param skipPartiallyExpanded Whether the partially expanded state, if the sheet is tall enough,
 *   should be skipped. If true, the sheet will always expand to the [Expanded] state and move to
 *   the [Hidden] state when hiding the sheet, either programmatically or by user interaction.
 * @param confirmValueChange Optional callback invoked to confirm or veto a pending state change.
 */
@Composable
fun rememberModalBottomSheetState(
    skipPartiallyExpanded: Boolean = false,
    confirmValueChange: (SheetValue) -> Boolean = { true },
) =
    rememberSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
        confirmValueChange = confirmValueChange,
        initialValue = Hidden,
    )

@Composable
private fun Scrim(color: Color, onDismissRequest: () -> Unit, visible: Boolean, alpha: () -> Float) {
    if (color.isSpecified) {
        val closeSheet = getString(Strings.CloseSheet)
        val dismissSheet =
            if (visible) {
                Modifier
                    .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } }
                    .semantics(mergeDescendants = true) {
                        traversalIndex = 1f
                        contentDescription = closeSheet
                        onClick {
                            onDismissRequest()
                            true
                        }
                    }
            } else {
                Modifier
            }
        Canvas(
            Modifier
                .fillMaxSize()
                .then(dismissSheet)
        ) {
            drawRect(color = color, alpha = alpha().coerceIn(0f, 1f))
        }
    }
}

private val PredictiveBackMaxScaleXDistance = 48.dp
private val PredictiveBackMaxScaleYDistance = 24.dp
private val PredictiveBackChildTransformOrigin = TransformOrigin(0.5f, 0f)

@Composable
@ReadOnlyComposable
internal fun getString(string: Strings): String {
    LocalConfiguration.current
    val resources = LocalContext.current.resources
    return resources.getString(string.value)
}


@JvmInline
@Immutable
internal value class Strings(val value: Int) {
    companion object {
        inline val BottomSheetPaneTitle
            get() = Strings(MaterialR.string.m3c_bottom_sheet_pane_title)

        inline val BottomSheetPartialExpandDescription
            get() = Strings(MaterialR.string.m3c_bottom_sheet_collapse_description)

        inline val BottomSheetDismissDescription
            get() = Strings(MaterialR.string.m3c_bottom_sheet_dismiss_description)

        inline val BottomSheetExpandDescription
            get() = Strings(MaterialR.string.m3c_bottom_sheet_expand_description)

        inline val CloseSheet
            get() = Strings(R.string.close_sheet)
    }
}

internal val BottomSheetAnimationSpec: FiniteAnimationSpec<Float> = tween(durationMillis = 300, easing = FastOutSlowInEasing)