@file:kotlin.jvm.JvmName("AndroidFullPlayerEngineKt")

package com.nuvio.app.features.player

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val TAG = "NuvioPlayerEngine"

@Composable
actual fun PlatformPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    sourceHeaders: Map<String, String>,
    sourceResponseHeaders: Map<String, String>,
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
    val playerSettings by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    var fallbackToMpv by remember(sourceUrl, sourceFilename) {
        mutableStateOf(false)
    }
    var fallbackPositionMs by remember(sourceUrl, sourceFilename) {
        mutableStateOf(0L)
    }
    var latestMedia3Snapshot by remember(sourceUrl, sourceFilename) {
        mutableStateOf(PlayerPlaybackSnapshot())
    }
    val preferMpvForSource = remember(sourceUrl, sourceResponseHeaders, sourceFilename) {
        shouldPreferMpvForPlaybackSource(
            sourceUrl = sourceUrl,
            responseHeaders = sourceResponseHeaders,
            sourceFilename = sourceFilename,
        )
    }

    val engineSelectionReason = when {
        playerSettings.playerEngine == PlayerEngineType.MPV -> "MPV_USER_SELECTED"
        fallbackToMpv -> "MPV_FALLBACK"
        preferMpvForSource -> "MPV_DAV_SOURCE"
        else -> "MEDIA3"
    }

    val useMpv = playerSettings.playerEngine == PlayerEngineType.MPV ||
        fallbackToMpv ||
        preferMpvForSource

    LaunchedEffect(engineSelectionReason) {
        Log.d(TAG, "Selected player engine: $engineSelectionReason")
    }
    LaunchedEffect(fallbackToMpv) {
        if (fallbackToMpv) onError(null)
    }

    if (useMpv) {
        AndroidMpvPlayerSurface(
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            sourceHeaders = sourceHeaders,
            initialPositionMs = if (fallbackToMpv) fallbackPositionMs else initialPositionMs,
            modifier = modifier,
            playWhenReady = playWhenReady,
            resizeMode = resizeMode,
            onControllerReady = onControllerReady,
            onSnapshot = onSnapshot,
            onError = onError,
        )
    } else {
        AndroidMedia3PlayerSurface(
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            sourceHeaders = sourceHeaders,
            sourceResponseHeaders = sourceResponseHeaders,
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
            onSnapshot = { snapshot ->
                latestMedia3Snapshot = snapshot
                onSnapshot(snapshot)
            },
            onError = onError,
            onRecoverableSourceError = { message, snapshot ->
                if (shouldFallbackToMpvForPlaybackError(
                        sourceUrl = sourceUrl,
                        responseHeaders = sourceResponseHeaders,
                        sourceFilename = sourceFilename,
                        errorMessage = message,
                    )
                ) {
                    fallbackPositionMs = maxOf(snapshot.positionMs, latestMedia3Snapshot.positionMs)
                        .coerceAtLeast(0L)
                    fallbackToMpv = true
                    true
                } else {
                    false
                }
            },
        )
    }
}
