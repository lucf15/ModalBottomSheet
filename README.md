# Compose Modal Bottom Sheet (Android only)

A custom implementation of Material 3's `ModalBottomSheet` that fixes critical issues with nested
scrolling and IME (keyboard) padding:

1. **[Issue #353304855](https://issuetracker.google.com/issues/353304855)**: Fixed inconsistent
   nested scroll handling
    - The official implementation incorrectly handles nested scroll events when the bottom sheet
      contains scrollable components like `LazyColumn`
    - The sheet attempts to dismiss prematurely, even when the inner content hasn't been fully
      scrolled to the top

2. **[Issue #289824811](https://issuetracker.google.com/issues/289824811)**: Customizable IME
   padding behavior
    - The official implementation applies `imePadding()` to the entire sheet, causing unwanted
      bottom padding when the keyboard appears
    - This library uses `windowInsetsPadding()` instead, giving developers full control over padding
      behavior

## Installation

### Step 1: Add JitPack repository

Add the JitPack repository to your `settings.gradle.kts` (or root `build.gradle.kts` if using older Gradle):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2: Add the dependency

#### Gradle (Kotlin DSL)

Add the dependency to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("it.lucf15.compose:bottomsheet:1.0.1")
}
```

## Requirements

- **Minimum SDK**: 21
- **Compile SDK**: 35

## API Reference

### ModalBottomSheet

```kotlin
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
)
```

## Migration from Official ModalBottomSheet

Migrating from the official Material 3 `ModalBottomSheet` is straightforward:

1. Update your import:

```kotlin
// Before
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState

// After
import it.lucf15.compose.bottomsheet.ModalBottomSheet
import it.lucf15.compose.bottomsheet.rememberModalBottomSheetState
```

2. If you have scrollable content, pass the scroll state:

```kotlin
val scrollState = rememberLazyListState()

ModalBottomSheet(
    // ... other parameters
    nestedScrollableState = scrollState // Add this
) {
    LazyColumn(state = scrollState) {
        // Your content
    }
}
```

3. If you want to control IME padding:

```kotlin
ModalBottomSheet(
    // ... other parameters
) {
    Box(Modifier.imePadding()) { // Add imePadding when you need it
        // Your content
    }
}
```

## How It Works

### Nested Scroll Fix

The library implements a custom `ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection` that
properly coordinates scroll events between the bottom sheet and nested scrollable content. It
ensures:

- The inner scrollable component receives scroll events first
- The bottom sheet only responds to dismiss gestures when the inner content is at the top
- Smooth fling gestures are preserved across both components

### Ime

Instead of applying `Modifier.imePadding()` unconditionally on the root ModalBottomSheet, the
library lets users place it where needed.

## License

```
Copyright 2025 lucf15

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```