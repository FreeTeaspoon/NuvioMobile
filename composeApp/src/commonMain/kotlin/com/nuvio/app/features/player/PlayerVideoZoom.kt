package com.nuvio.app.features.player

import kotlin.math.pow
import kotlin.math.roundToInt

data class PlayerVideoZoomState(val zoom: Float = 0f) {
    fun normalized(): PlayerVideoZoomState = copy(zoom = clampPlayerVideoZoom(zoom))
}

internal const val PlayerVideoZoomMin = -2f
internal const val PlayerVideoZoomMax = 2f
internal const val PlayerVideoZoomStep = 0.05f

internal interface VideoZoomController {
    fun setVideoZoom(state: PlayerVideoZoomState)
}

internal fun PlayerEngineController.setVideoZoom(state: PlayerVideoZoomState) {
    (this as? VideoZoomController)?.setVideoZoom(state.normalized())
}

internal fun clampPlayerVideoZoom(zoom: Float): Float =
    if (zoom.isFinite()) zoom.coerceIn(PlayerVideoZoomMin, PlayerVideoZoomMax) else 0f

internal fun stepPlayerVideoZoom(zoom: Float, direction: Int): Float =
    clampPlayerVideoZoom(zoom + PlayerVideoZoomStep * direction)

// MPV's video-zoom uses powers of two. Media3 and the label use the same scale.
internal fun playerVideoZoomScale(zoom: Float): Float = 2f.pow(clampPlayerVideoZoom(zoom))

internal fun formatPlayerVideoZoomLabel(zoom: Float): String {
    val hundredths = (playerVideoZoomScale(zoom) * 100f).roundToInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}x"
}

internal expect object VideoZoomStorage {
    fun load(): String?
    fun save(payload: String)
}
