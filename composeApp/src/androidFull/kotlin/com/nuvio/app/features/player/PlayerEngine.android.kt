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

/**
 * Full builds keep the fork's bundled MPV/AAR surface, while the common Media3
 * implementation remains the upstream player path. Keeping this adapter small
 * makes the flavor-specific difference explicit and keeps future upstream
 * player refactors out of the full-only MPV implementation.
 */
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
    val playerSettings by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val playerSourceKey = listOf(
        sourceUrl,
        sourceAudioUrl.orEmpty(),
        sanitizePlaybackHeaders(sourceHeaders),
        sanitizePlaybackResponseHeaders(sourceResponseHeaders),
        streamType.orEmpty(),
        sourceFilename.orEmpty(),
        sourceVideoSize ?: 0L,
        useYoutubeChunkedPlayback,
        initialPositionRequestKey.orEmpty(),
    )
    var fallbackToMpv by remember(playerSourceKey) { mutableStateOf(false) }
    var fallbackPositionMs by remember(playerSourceKey) { mutableStateOf(0L) }
    var latestMedia3Snapshot by remember(playerSourceKey) {
        mutableStateOf(PlayerPlaybackSnapshot())
    }

    val preferMpvForSource = remember(sourceUrl, sourceResponseHeaders, sourceFilename) {
        shouldPreferMpvForPlaybackSource(
            sourceUrl = sourceUrl,
            responseHeaders = sourceResponseHeaders,
            sourceFilename = sourceFilename,
        )
    }
    val useMpv = playerSettings.androidPlaybackEngine == AndroidPlaybackEngine.Libmpv ||
        playerSettings.playerEngine == PlayerEngineType.MPV ||
        fallbackToMpv ||
        preferMpvForSource

    LaunchedEffect(fallbackToMpv) {
        if (fallbackToMpv) onError(null)
    }

    if (useMpv) {
        LaunchedEffect(initialPositionRequestKey) {
            initialPositionRequestKey?.let { key ->
                onInitialPositionHandled(key, false)
            }
        }
        AndroidMpvPlayerSurface(
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            sourceHeaders = sourceHeaders,
            externalSubtitles = externalSubtitles,
            initialPositionMs = if (fallbackToMpv) {
                fallbackPositionMs
            } else {
                initialPositionMs ?: 0L
            },
            modifier = modifier,
            playWhenReady = playWhenReady,
            resizeMode = resizeMode,
            videoOutput = playerSettings.androidLibmpvVideoOutput,
            hardwareDecodingEnabled = playerSettings.androidLibmpvHardwareDecodingEnabled,
            yuv420pEnabled = playerSettings.androidLibmpvYuv420pEnabled,
            onControllerReady = onControllerReady,
            onSnapshot = onSnapshot,
            onError = onError,
        )
    } else {
        ExoPlayerSurface(
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
            onSnapshot = { snapshot ->
                latestMedia3Snapshot = snapshot
                onSnapshot(snapshot)
            },
            onError = { message ->
                if (
                    message != null &&
                    shouldTreatMedia3VarintFailureAsEnded(
                        sourceUrl = sourceUrl,
                        responseHeaders = sourceResponseHeaders,
                        sourceFilename = sourceFilename,
                        errorMessage = message,
                        snapshot = latestMedia3Snapshot,
                    )
                ) {
                    onSnapshot(latestMedia3Snapshot.asEndedPlaybackSnapshot())
                } else if (
                    message != null &&
                    playerSettings.androidPlaybackEngine == AndroidPlaybackEngine.Auto
                ) {
                    fallbackPositionMs = latestMedia3Snapshot.positionMs.coerceAtLeast(0L)
                    fallbackToMpv = true
                    Log.w(TAG, "Media3 failed; falling back to bundled libmpv: $message")
                } else {
                    onError(message)
                }
            },
        )
    }
}
