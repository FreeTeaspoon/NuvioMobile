package com.nuvio.app.core.ui.installerx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import androidx.compose.ui.unit.dp

actual class FloatingBottomBarBackdrop internal constructor(
    internal val value: LayerBackdrop,
)

internal actual class FloatingBottomBarVisualState internal constructor(
    internal val backdrop: Backdrop,
    internal val tabsBackdrop: LayerBackdrop,
    internal val combinedBackdrop: Backdrop,
)

internal actual val FloatingBottomBarShape: Shape = ContinuousCapsule

@Composable
actual fun rememberFloatingBottomBarBackdrop(): FloatingBottomBarBackdrop {
    val backdrop = rememberLayerBackdrop()
    return remember(backdrop) { FloatingBottomBarBackdrop(backdrop) }
}

actual fun Modifier.floatingBottomBarBackdropLayer(
    backdrop: FloatingBottomBarBackdrop,
): Modifier = layerBackdrop(backdrop.value)

@Composable
internal actual fun rememberFloatingBottomBarVisualState(
    backdrop: FloatingBottomBarBackdrop,
): FloatingBottomBarVisualState {
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop.value, tabsBackdrop)
    return remember(backdrop, tabsBackdrop, combinedBackdrop) {
        FloatingBottomBarVisualState(
            backdrop = backdrop.value,
            tabsBackdrop = tabsBackdrop,
            combinedBackdrop = combinedBackdrop,
        )
    }
}

internal actual fun Modifier.floatingBottomBarContainerEffect(
    state: FloatingBottomBarVisualState,
    isBlurEnabled: Boolean,
    isInLightTheme: Boolean,
    containerColor: Color,
    blurRadius: Float,
    lensRadius: Float,
    pressProgress: () -> Float,
): Modifier = drawBackdrop(
    backdrop = state.backdrop,
    shape = { FloatingBottomBarShape },
    effects = {
        if (isBlurEnabled) {
            vibrancy()
            blur(blurRadius)
            lens(lensRadius, lensRadius)
        }
    },
    highlight = {
        Highlight.Default.copy(alpha = if (isBlurEnabled) 1f else 0f)
    },
    shadow = {
        Shadow.Default.copy(
            color = Color.Black.copy(if (isInLightTheme) 0.1f else 0.2f),
        )
    },
    layerBlock = {
        if (isBlurEnabled) {
            val progress = pressProgress()
            val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
            scaleX = scale
            scaleY = scale
        }
    },
    onDrawSurface = { drawRect(containerColor) },
)

internal actual fun Modifier.floatingBottomBarTabsEffect(
    state: FloatingBottomBarVisualState,
    isBlurEnabled: Boolean,
    containerColor: Color,
    blurRadius: Float,
    lensRadius: Float,
    pressProgress: () -> Float,
): Modifier = layerBackdrop(state.tabsBackdrop).drawBackdrop(
    backdrop = state.backdrop,
    shape = { FloatingBottomBarShape },
    effects = {
        if (isBlurEnabled) {
            val progress = pressProgress()
            vibrancy()
            blur(blurRadius)
            lens(lensRadius * progress, lensRadius * progress)
        }
    },
    highlight = {
        Highlight.Default.copy(alpha = if (isBlurEnabled) pressProgress() else 0f)
    },
    onDrawSurface = { drawRect(containerColor) },
)

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
): Modifier = drawBackdrop(
    backdrop = state.combinedBackdrop,
    shape = { FloatingBottomBarShape },
    effects = {
        if (isBlurEnabled) {
            val progress = pressProgress()
            lens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress, true)
        }
    },
    highlight = {
        Highlight.Default.copy(alpha = if (isBlurEnabled) pressProgress() else 0f)
    },
    shadow = {
        Shadow(alpha = if (isBlurEnabled) pressProgress() else 0f)
    },
    innerShadow = {
        InnerShadow(
            radius = 8f.dp * pressProgress(),
            alpha = if (isBlurEnabled) pressProgress() else 0f,
        )
    },
    layerBlock = {
        if (isBlurEnabled) {
            this.scaleX = scaleX()
            this.scaleY = scaleY()
            val currentVelocity = velocity() / 10f
            this.scaleX /= 1f - (currentVelocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
            this.scaleY *= 1f - (currentVelocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
        }
    },
    onDrawSurface = {
        val progress = if (isBlurEnabled) pressProgress() else 0f
        drawRect(
            color = if (isInLightTheme) Color.Black.copy(0.1f) else indicatorRestColor,
            alpha = 1f - progress,
        )
        drawRect(color = indicatorPressedOverlayColor, alpha = progress)
        if (progress > 0f) {
            drawRect(color = pressedIndicatorScrimColor, alpha = progress)
        }
    },
)
