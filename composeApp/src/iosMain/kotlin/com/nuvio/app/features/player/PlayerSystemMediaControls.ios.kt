package com.nuvio.app.features.player

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformSystemMediaControls(
    title: String?,
    subtitle: String?,
    controller: PlayerEngineController?,
    snapshot: PlayerPlaybackSnapshot,
    enabled: Boolean,
) {
    title
    subtitle
    controller
    snapshot
    enabled
}
