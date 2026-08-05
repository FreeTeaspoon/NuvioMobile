@file:kotlin.jvm.JvmName("AndroidPlaystorePlayerEngineKt")

package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Play Store builds use the upstream shared Media3/libmpv player path. */
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
    initialPositionMs: Long?,
    initialPositionRequestKey: String?,
    resizeMode: PlayerResizeMode,
    useNativeController: Boolean,
    onInitialPositionHandled: (key: String, handled: Boolean) -> Unit,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
) {
    PlatformMedia3PlayerSurface(
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
        initialPositionMs = initialPositionMs,
        initialPositionRequestKey = initialPositionRequestKey,
        resizeMode = resizeMode,
        useNativeController = useNativeController,
        onInitialPositionHandled = onInitialPositionHandled,
        onControllerReady = onControllerReady,
        onSnapshot = onSnapshot,
        onError = onError,
    )
}
