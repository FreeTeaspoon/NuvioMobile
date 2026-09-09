package com.nuvio.app.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith

private const val NavigationDurationMillis = 300
private const val FadeThroughThresholdMillis = 105 // 35% of the transition.
private val NavigationEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

// Google's full-screen surface motion specification:
// https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back#full-screen-surfaces
// Both screens are transparent at 35%; the incoming screen only fades in afterward.
private fun fullScreenTransition(): ContentTransform =
    (fadeIn(
        animationSpec = tween(
            durationMillis = NavigationDurationMillis - FadeThroughThresholdMillis,
            delayMillis = FadeThroughThresholdMillis,
            easing = NavigationEasing,
        ),
    ) + scaleIn(
        animationSpec = tween(NavigationDurationMillis, easing = NavigationEasing),
        initialScale = 1.1f,
    )) togetherWith (fadeOut(
        animationSpec = tween(FadeThroughThresholdMillis, easing = NavigationEasing),
    ) + scaleOut(
        animationSpec = tween(NavigationDurationMillis, easing = NavigationEasing),
        targetScale = 0.9f,
    ))

internal fun nuvioPushTransition(): ContentTransform = fullScreenTransition()

// NavDisplay seeks this same transform during the gesture and reverses it on cancellation.
internal fun nuvioPopTransition(): ContentTransform = fullScreenTransition()
