package com.nuvio.app.features.player

import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

/** Scale only the video surface so controls and subtitles keep their original size. */
@OptIn(UnstableApi::class)
internal fun PlayerView.applyVideoZoom(zoom: Float) {
    val surface = videoSurfaceView ?: return
    // Allow the video to fill the letterbox area, then clip at the player bounds.
    (surface.parent as? ViewGroup)?.clipChildren = false
    clipChildren = true
    surface.applyCenteredVideoZoom(zoom)
}

internal fun View.applyCenteredVideoZoom(zoom: Float) {
    pivotX = width / 2f
    pivotY = height / 2f
    scaleX = playerVideoZoomScale(zoom)
    scaleY = scaleX
    translationX = 0f
    translationY = 0f
    removeOnLayoutChangeListener(centerVideoZoomOnLayout)
    addOnLayoutChangeListener(centerVideoZoomOnLayout)
}

private val centerVideoZoomOnLayout = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
    view.pivotX = view.width / 2f
    view.pivotY = view.height / 2f
}
