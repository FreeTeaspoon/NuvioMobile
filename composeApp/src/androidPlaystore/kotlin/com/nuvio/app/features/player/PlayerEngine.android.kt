@file:kotlin.jvm.JvmName("AndroidPlaystorePlayerEngineKt")

package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    sourceHeaders: Map<String, String>,
    sourceResponseHeaders: Map<String, String>,
    externalSubtitles: List<com.nuvio.app.features.streams.StreamSubtitle>,
    streamType: String?,
    sourceFilename: String?,
    sourceVideoSize: Long?,
    useYoutubeChunkedPlayback: Boolean,
    modifier: Modifier,
    playWhenReady: Boolean,
    resizeMode: PlayerResizeMode,
    initialPositionMs: Long,
    useNativeController: Boolean,
    playerControlsState: PlayerControlsState,
    onPlayerControlsAction: (PlayerControlsAction) -> Boolean,
    onPlayerControlsEvent: (String, Double) -> Boolean,
    onPlayerControlsScrubChange: (Long) -> Boolean,
    onPlayerControlsScrubFinished: (Long) -> Boolean,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
) {
    AndroidMedia3PlayerSurface(
        sourceUrl = sourceUrl,
        sourceAudioUrl = sourceAudioUrl,
        sourceHeaders = sourceHeaders,
        sourceResponseHeaders = sourceResponseHeaders,
        externalSubtitles = externalSubtitles,
        streamType = streamType,
        sourceFilename = sourceFilename,
        sourceVideoSize = sourceVideoSize,
        useYoutubeChunkedPlayback = useYoutubeChunkedPlayback,
        modifier = modifier,
        playWhenReady = playWhenReady,
        resizeMode = resizeMode,
        initialPositionMs = initialPositionMs,
        useNativeController = useNativeController,
        playerControlsState = playerControlsState,
        onPlayerControlsAction = onPlayerControlsAction,
        onPlayerControlsEvent = onPlayerControlsEvent,
        onPlayerControlsScrubChange = onPlayerControlsScrubChange,
        onPlayerControlsScrubFinished = onPlayerControlsScrubFinished,
        onControllerReady = onControllerReady,
        onSnapshot = onSnapshot,
        onError = onError,
    )
}
