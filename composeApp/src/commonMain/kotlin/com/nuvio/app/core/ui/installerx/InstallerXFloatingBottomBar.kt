// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2025-2026 InstallerX Revived contributors
//
// Portions of this file are derived from weishu/KernelSU
// (https://github.com/tiann/KernelSU)
// Copyright (C) KernelSU contributors
// Licensed under GPL-3.0
package com.nuvio.app.core.ui.installerx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }
val LocalFloatingBottomBarPressProgress = staticCompositionLocalOf { { 0f } }

expect class FloatingBottomBarBackdrop

internal expect class FloatingBottomBarVisualState

internal expect val FloatingBottomBarShape: Shape

@Composable
expect fun rememberFloatingBottomBarBackdrop(): FloatingBottomBarBackdrop

expect fun Modifier.floatingBottomBarBackdropLayer(
    backdrop: FloatingBottomBarBackdrop,
): Modifier

@Composable
internal expect fun rememberFloatingBottomBarVisualState(
    backdrop: FloatingBottomBarBackdrop,
): FloatingBottomBarVisualState

internal expect fun Modifier.floatingBottomBarContainerEffect(
    state: FloatingBottomBarVisualState,
    isBlurEnabled: Boolean,
    isInLightTheme: Boolean,
    containerColor: Color,
    blurRadius: Float,
    lensRadius: Float,
    pressProgress: () -> Float,
): Modifier

internal expect fun Modifier.floatingBottomBarTabsEffect(
    state: FloatingBottomBarVisualState,
    isBlurEnabled: Boolean,
    containerColor: Color,
    blurRadius: Float,
    lensRadius: Float,
    translationX: Float,
    pressProgress: () -> Float,
): Modifier

internal expect fun Modifier.floatingBottomBarIndicatorEffect(
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
): Modifier

@Composable
fun RowScope.FloatingBottomBarItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalFloatingBottomBarTabScale.current
    Column(
        modifier
            .clip(FloatingBottomBarShape)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val scale = scale()
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
fun FloatingBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    backdrop: FloatingBottomBarBackdrop,
    tabsCount: Int,
    isBlurEnabled: Boolean = true,
    isInLightTheme: Boolean,
    accentColor: Color,
    containerColor: Color,
    indicatorRestColor: Color,
    indicatorPressedOverlayColor: Color,
    pressedIndicatorScrimColor: Color,
    highlightColor: Color,
    content: @Composable RowScope.() -> Unit
) {
    val visualState = rememberFloatingBottomBarVisualState(backdrop)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val blurRadius = with(density) { 18.dp.toPx() }
    val lensRadius = with(density) { 24.dp.toPx() }

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val panelOffset by remember(density) {
        derivedStateOf {
            if (totalWidthPx == 0f) 0f else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
    }

    val selectedValue = selectedIndex().coerceIn(0, tabsCount - 1)
    var currentIndex by remember { mutableIntStateOf(selectedValue) }

    class DampedDragAnimationHolder {
        var instance: DampedDragAnimation? = null
    }

    val holder = remember { DampedDragAnimationHolder() }

    val dampedDragAnimation = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedValue.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                val anim = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false

                val currentValue = anim.value
                val indicatorX = currentValue * tabWidthPx
                val padding = with(density) { 4.dp.toPx() }
                val globalTouchX = if (isLtr) {
                    val touchX = indicatorX + offset.x
                    padding + touchX
                } else {
                    val touchX = totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                    touchX
                }
                globalTouchX in 0f..totalWidthPx
            },
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                currentIndex = targetIndex
                animateToValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            }
        ).also { holder.instance = it }
    }

    LaunchedEffect(selectedValue, dampedDragAnimation) {
        currentIndex = selectedValue
        dampedDragAnimation.animateToValue(selectedValue.toFloat())
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
            dampedDragAnimation.animateToValue(index.toFloat())
            onSelected(index)
        }
    }

    val interactiveHighlight = if (isBlurEnabled && isInteractiveHighlightSupported()) {
        remember(animationScope, tabWidthPx) {
            InteractiveHighlight(
                animationScope = animationScope,
                highlightColor = highlightColor,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }
    } else {
        null
    }

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart
    ) {
        CompositionLocalProvider(
            LocalFloatingBottomBarPressProgress provides { dampedDragAnimation.pressProgress }
        ) {
            Row(
                Modifier
                    .onGloballyPositioned { coords ->
                        totalWidthPx = coords.size.width.toFloat()
                        val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                        tabWidthPx = contentWidthPx / tabsCount
                    }
                    .graphicsLayer { translationX = panelOffset }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .floatingBottomBarContainerEffect(
                        state = visualState,
                        isBlurEnabled = isBlurEnabled,
                        isInLightTheme = isInLightTheme,
                        containerColor = containerColor,
                        blurRadius = blurRadius,
                        lensRadius = lensRadius,
                        pressProgress = { dampedDragAnimation.pressProgress },
                    )
                    .then(if (isBlurEnabled && interactiveHighlight != null) interactiveHighlight.modifier else Modifier)
                    .height(64.dp)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        CompositionLocalProvider(
            LocalFloatingBottomBarTabScale provides {
                if (isBlurEnabled) lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                else 1f
            },
            LocalFloatingBottomBarPressProgress provides { 0f },
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0.001f)
                    .floatingBottomBarTabsEffect(
                        state = visualState,
                        isBlurEnabled = isBlurEnabled,
                        containerColor = containerColor,
                        blurRadius = blurRadius,
                        lensRadius = lensRadius,
                        translationX = panelOffset,
                        pressProgress = { dampedDragAnimation.pressProgress },
                    )
                    .then(if (isBlurEnabled && interactiveHighlight != null) interactiveHighlight.modifier else Modifier)
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        if (tabWidthPx > 0f) {
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        val contentWidth = totalWidthPx - with(density) { 8.dp.toPx() }
                        val singleTabWidth = contentWidth / tabsCount

                        val progressOffset = dampedDragAnimation.value * singleTabWidth

                        translationX = if (isLtr) {
                            progressOffset + panelOffset
                        } else {
                            -progressOffset + panelOffset
                        }
                    }
                    .then(if (isBlurEnabled && interactiveHighlight != null) interactiveHighlight.gestureModifier else Modifier)
                    .then(dampedDragAnimation.modifier)
                    .floatingBottomBarIndicatorEffect(
                        state = visualState,
                        isBlurEnabled = isBlurEnabled,
                        isInLightTheme = isInLightTheme,
                        indicatorRestColor = indicatorRestColor,
                        indicatorPressedOverlayColor = indicatorPressedOverlayColor,
                        pressedIndicatorScrimColor = pressedIndicatorScrimColor,
                        pressProgress = { dampedDragAnimation.pressProgress },
                        scaleX = { dampedDragAnimation.scaleX },
                        scaleY = { dampedDragAnimation.scaleY },
                        velocity = { dampedDragAnimation.velocity },
                    )
                    .height(56.dp)
                    .width(with(density) { ((totalWidthPx - 8.dp.toPx()) / tabsCount).toDp() })
            )
        }
    }
}
