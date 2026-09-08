package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun PlayerScreenRuntime.BindVideoZoom() {
    LaunchedEffect(videoZoomContentKey) {
        videoZoom = RememberedVideoZoomRepository.zoomFor(videoZoomContentKey) ?: 0f
    }
    LaunchedEffect(playerController, videoZoomState) {
        playerController?.setVideoZoom(videoZoomState)
    }
}

internal fun PlayerScreenRuntime.applyVideoZoomState(state: PlayerVideoZoomState) {
    videoZoom = state.normalized().zoom
    playerController?.setVideoZoom(videoZoomState)
}

internal fun PlayerScreenRuntime.openVideoZoomModal() {
    showVideoZoomModal = true
    showAudioModal = false
    showSubtitleModal = false
    showSpeedModal = false
    showVideoSettingsModal = false
    controlsVisible = true
}

internal fun PlayerScreenRuntime.resetVideoZoom() {
    applyVideoZoomState(PlayerVideoZoomState())
}

internal fun PlayerScreenRuntime.setVideoZoomAsDefault() {
    RememberedVideoZoomRepository.saveZoom(videoZoomContentKey, videoZoomState.zoom)
}
