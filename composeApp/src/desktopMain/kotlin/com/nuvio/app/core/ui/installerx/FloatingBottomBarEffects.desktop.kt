package com.nuvio.app.core.ui.installerx

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.fastCoerceIn

actual class FloatingBottomBarBackdrop

internal actual class FloatingBottomBarVisualState

internal actual val FloatingBottomBarShape: Shape = RoundedCornerShape(percent = 50)

@Composable
actual fun rememberFloatingBottomBarBackdrop(): FloatingBottomBarBackdrop =
    remember { FloatingBottomBarBackdrop() }

actual fun Modifier.floatingBottomBarBackdropLayer(
    backdrop: FloatingBottomBarBackdrop,
): Modifier = this

@Composable
internal actual fun rememberFloatingBottomBarVisualState(
    backdrop: FloatingBottomBarBackdrop,
): FloatingBottomBarVisualState = remember { FloatingBottomBarVisualState() }

internal actual fun Modifier.floatingBottomBarContainerEffect(
    state: FloatingBottomBarVisualState,
    isBlurEnabled: Boolean,
    isInLightTheme: Boolean,
    containerColor: Color,
    blurRadius: Float,
    lensRadius: Float,
    pressProgress: () -> Float,
): Modifier = clip(FloatingBottomBarShape).background(containerColor)

internal actual fun Modifier.floatingBottomBarTabsEffect(
    state: FloatingBottomBarVisualState,
    isBlurEnabled: Boolean,
    containerColor: Color,
    blurRadius: Float,
    lensRadius: Float,
    pressProgress: () -> Float,
): Modifier = clip(FloatingBottomBarShape).background(containerColor)

internal actual fun Modifier.floatingBottomBarIndicatorEffect(
    state: FloatingBottomBarVisualState,
    isBlurEnabled: Boolean,
    isInLightTheme: Boolean,
    indicatorRestColor: Color,
    indicatorPressedOverlayColor: Color,
    pressedIndicatorScrimColor: Color,
    pressProgress: () -> Float,
    scaleX: () -> Float,
    scaleY: () -> Float,
    velocity: () -> Float,
): Modifier = graphicsLayer {
    if (isBlurEnabled) {
        this.scaleX = scaleX()
        this.scaleY = scaleY()
        val currentVelocity = velocity() / 10f
        this.scaleX /= 1f - (currentVelocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
        this.scaleY *= 1f - (currentVelocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
    }
}.clip(FloatingBottomBarShape).background(
    if (isInLightTheme) Color.Black.copy(0.1f) else indicatorRestColor,
)
