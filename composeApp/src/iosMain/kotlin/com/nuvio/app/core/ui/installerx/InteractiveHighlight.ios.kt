package com.nuvio.app.core.ui.installerx

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope

actual class InteractiveHighlight actual constructor(
    animationScope: CoroutineScope,
    highlightColor: Color,
    position: (size: Size, offset: Offset) -> Offset
) {
    actual val modifier: Modifier = Modifier
    actual val gestureModifier: Modifier = Modifier
}

actual fun isInteractiveHighlightSupported(): Boolean = false
