package com.nuvio.app.features.player

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformSystemMediaControls(
    title: String?,
    subtitle: String?,
    artworkUrl: String?,
    controller: PlayerEngineController?,
    snapshot: PlayerPlaybackSnapshot,
    enabled: Boolean,
) {
    title
    subtitle
    artworkUrl
    controller
    snapshot
    enabled
}
