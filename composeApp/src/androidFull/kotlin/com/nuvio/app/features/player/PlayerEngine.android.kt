@file:kotlin.jvm.JvmName("AndroidFullPlayerEngineKt")

package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
actual fun PlatformPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    sourceHeaders: Map<String, String>,
    sourceResponseHeaders: Map<String, String>,
    sourceFilename: String?,
    sourceVideoSize: Long?,
    useYoutubeChunkedPlayback: Boolean,
    modifier: Modifier,
    playWhenReady: Boolean,
    resizeMode: PlayerResizeMode,
    useNativeController: Boolean,
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

    val useMpv = playerSettings.playerEngine == PlayerEngineType.MPV || fallbackToMpv
    LaunchedEffect(fallbackToMpv) {
        if (fallbackToMpv) onError(null)
    }

    if (useMpv) {
        AndroidMpvPlayerSurface(
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            sourceHeaders = sourceHeaders,
            modifier = modifier,
            playWhenReady = playWhenReady,
            resizeMode = resizeMode,
            onControllerReady = onControllerReady,
            onSnapshot = onSnapshot,
            onError = onError,
        )
    } else {
        val shouldFallbackOnMedia3SourceError = shouldFallbackToMpvForDavSource(
            sourceUrl = sourceUrl,
            sourceFilename = sourceFilename,
        )
        AndroidMedia3PlayerSurface(
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            sourceHeaders = sourceHeaders,
            sourceResponseHeaders = sourceResponseHeaders,
            sourceFilename = sourceFilename,
            sourceVideoSize = sourceVideoSize,
            useYoutubeChunkedPlayback = useYoutubeChunkedPlayback,
            modifier = modifier,
            playWhenReady = playWhenReady,
            resizeMode = resizeMode,
            useNativeController = useNativeController,
            onControllerReady = onControllerReady,
            onSnapshot = onSnapshot,
            onError = { message ->
                if (message != null && shouldFallbackOnMedia3SourceError) {
                    fallbackToMpv = true
                } else {
                    onError(message)
                }
            },
        )
    }
}

private fun shouldFallbackToMpvForDavSource(
    sourceUrl: String,
    sourceFilename: String?,
): Boolean {
    val normalized = "$sourceUrl ${sourceFilename.orEmpty()}".lowercase()
    if (normalized.contains("nzbdav") ||
        normalized.contains("altmount") ||
        normalized.contains("webdav") ||
        normalized.contains("usenet")
    ) {
        return true
    }

    val hasUrlUserInfo = Regex("""^[a-z][a-z0-9+.-]*://[^/@]+:[^/@]+@""")
        .containsMatchIn(sourceUrl.lowercase())
    if (hasUrlUserInfo) return true

    val path = sourceUrl.substringBefore('?').substringBefore('#')
    val lastPathSegment = path.substringAfterLast('/')
    val urlLooksExtensionless = !lastPathSegment.contains('.')
    val filenameHasVideoExtension = sourceFilename
        ?.lowercase()
        ?.let { filename ->
            listOf(".mkv", ".mk3d", ".webm", ".mp4", ".m4v", ".mov", ".avi", ".ts", ".m2ts", ".mts", ".mpg", ".mpeg", ".flv")
                .any(filename::contains)
        }
        ?: false

    return urlLooksExtensionless && filenameHasVideoExtension
}
