package it.lucf15.compose.bottomsheet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import it.lucf15.compose.bottomsheet.ui.theme.SampleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isVisible by rememberSaveable { mutableStateOf(false) }
            val scrollState = rememberLazyListState()
            val sheetState = rememberModalBottomSheetState()
            SampleTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeContentPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = { isVisible = true }) {
                        Text("Open bottom sheet")
                    }
                    if (isVisible) {
                        Sheet(
                            onDismiss = { isVisible = false },
                            state = sheetState,
                            contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top),
                            nestedScrollableState = scrollState
                        ) {
                            LazyColumn(
                                state = scrollState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Bottom).asPaddingValues()
                            ) {
                                items(20) {
                                    Box(
                                        modifier = Modifier
                                            .height(100.dp)
                                            .fillMaxWidth()
                                            .background(Color.LightGray.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "Index = $it")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sheet(
    onDismiss: () -> Unit,
    state: SheetState,
    nestedScrollableState: ScrollableState? = null,
    contentWindowInsets: WindowInsets,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        scrimColor = Color.Black.copy(alpha = 0.4f),
        sheetState = state,
        nestedScrollableState = nestedScrollableState,
        shape = RectangleShape,
        sheetGesturesEnabled = true,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 16.dp)
                    .size(50.dp, 4.dp)
                    .background(Color.LightGray, CircleShape)
            )
        },
        containerColor = Color.White,
        contentWindowInsets = { contentWindowInsets },
        onDismissRequest = onDismiss,
        content = content,
    )
}