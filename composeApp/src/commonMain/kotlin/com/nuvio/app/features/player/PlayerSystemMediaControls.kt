package com.nuvio.app.features.player

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformSystemMediaControls(
    title: String?,
    subtitle: String?,
    artworkUrl: String?,
    controller: PlayerEngineController?,
    snapshot: PlayerPlaybackSnapshot,
    enabled: Boolean,
)
