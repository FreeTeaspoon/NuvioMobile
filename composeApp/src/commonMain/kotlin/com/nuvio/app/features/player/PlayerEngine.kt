package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.max

interface PlayerEngineController {
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekBy(offsetMs: Long)
    fun retry()
    fun setPlaybackSpeed(speed: Float)
    fun getAudioTracks(): List<AudioTrack>
    fun getSubtitleTracks(): List<SubtitleTrack>
    fun selectAudioTrack(index: Int)
    fun selectSubtitleTrack(index: Int)
    fun setSubtitleUri(url: String)
    fun setSubtitleUri(url: String, onLoaded: (Boolean) -> Unit) {
        setSubtitleUri(url)
        onLoaded(true)
    }
    fun clearExternalSubtitle()
    fun clearExternalSubtitleAndSelect(trackIndex: Int)
    fun setVideoZoom(state: PlayerVideoZoomState) {}
    fun applySubtitleStyle(style: SubtitleStyleState) {}
    fun setSubtitleDelayMs(delayMs: Int) {}
    fun configureIosVideoOutput(settings: PlayerSettingsUiState) {}
}

internal fun sanitizePlaybackHeaders(headers: Map<String, String>?): Map<String, String> {
    val rawHeaders = headers ?: return emptyMap()
    if (rawHeaders.isEmpty()) return emptyMap()

    val sanitized = LinkedHashMap<String, String>(rawHeaders.size)
    rawHeaders.forEach { (rawKey, rawValue) ->
        val key = rawKey.trim()
        val value = rawValue.trim()
        if (key.isEmpty() || value.isEmpty()) return@forEach
        if (key.equals("Range", ignoreCase = true)) return@forEach
        sanitized[key] = value
    }
    return sanitized
}

internal fun sanitizePlaybackResponseHeaders(headers: Map<String, String>?): Map<String, String> {
    val rawHeaders = headers ?: return emptyMap()
    if (rawHeaders.isEmpty()) return emptyMap()

    val sanitized = LinkedHashMap<String, String>(rawHeaders.size)
    rawHeaders.forEach { (rawKey, rawValue) ->
        val key = rawKey.trim()
        val value = rawValue.trim()
        if (key.isEmpty() || value.isEmpty()) return@forEach
        sanitized[key] = value
    }
    return sanitized
}

internal fun inferPlaybackMimeType(
    sourceUrl: String,
    responseHeaders: Map<String, String>? = emptyMap(),
    sourceFilename: String? = null,
): String? {
    val headers = responseHeaders.orEmpty()
    val contentType = headers.valueForHeader("Content-Type")
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }
        ?.takeIf { it !in genericPlaybackContentTypes }
    if (contentType != null) return contentType

    return listOfNotNull(
        sourceFilename,
        filenameFromContentDisposition(headers.valueForHeader("Content-Disposition")),
        sourceUrl.substringBefore('?').substringBefore('#'),
    ).firstNotNullOfOrNull(::inferPlaybackMimeTypeFromName)
}

internal fun shouldPreferMpvForPlaybackSource(
    sourceUrl: String,
    responseHeaders: Map<String, String>? = emptyMap(),
    sourceFilename: String? = null,
): Boolean =
    isDavLikePlaybackSource(sourceUrl = sourceUrl, sourceFilename = sourceFilename) &&
        isMatroskaPlaybackSource(
            sourceUrl = sourceUrl,
            responseHeaders = responseHeaders,
            sourceFilename = sourceFilename,
        )

internal fun shouldFallbackToMpvForPlaybackError(
    sourceUrl: String,
    responseHeaders: Map<String, String>? = emptyMap(),
    sourceFilename: String? = null,
    errorMessage: String?,
): Boolean =
    isMedia3MatroskaVarintLengthMaskError(errorMessage) &&
        isDavLikePlaybackSource(sourceUrl = sourceUrl, sourceFilename = sourceFilename) &&
        isMatroskaPlaybackSource(
            sourceUrl = sourceUrl,
            responseHeaders = responseHeaders,
            sourceFilename = sourceFilename,
        )

internal fun shouldTreatMedia3VarintFailureAsEnded(
    sourceUrl: String,
    responseHeaders: Map<String, String>? = emptyMap(),
    sourceFilename: String? = null,
    errorMessage: String?,
    snapshot: PlayerPlaybackSnapshot,
): Boolean {
    if (!shouldFallbackToMpvForPlaybackError(
            sourceUrl = sourceUrl,
            responseHeaders = responseHeaders,
            sourceFilename = sourceFilename,
            errorMessage = errorMessage,
        )
    ) {
        return false
    }

    val durationMs = snapshot.durationMs.takeIf { it > 0L } ?: return false
    val positionMs = snapshot.positionMs.coerceIn(0L, durationMs)
    val remainingMs = durationMs - positionMs
    val progress = positionMs.toDouble() / durationMs.toDouble()
    return remainingMs <= Media3VarintTailCompletionRemainingMs ||
        progress >= Media3VarintTailCompletionProgress
}

internal fun PlayerPlaybackSnapshot.asEndedPlaybackSnapshot(): PlayerPlaybackSnapshot {
    val normalizedDurationMs = durationMs.coerceAtLeast(0L)
    val normalizedPositionMs = if (normalizedDurationMs > 0L) {
        normalizedDurationMs
    } else {
        positionMs.coerceAtLeast(0L)
    }
    return copy(
        isLoading = false,
        isPlaying = false,
        isEnded = true,
        positionMs = normalizedPositionMs,
        bufferedPositionMs = max(bufferedPositionMs.coerceAtLeast(0L), normalizedPositionMs),
    )
}

private const val Media3VarintLengthMaskMessage = "No valid varint length mask found"
private const val Media3VarintTailCompletionRemainingMs = 10_000L
private const val Media3VarintTailCompletionProgress = 0.995

private fun isMedia3MatroskaVarintLengthMaskError(message: String?): Boolean =
    message?.contains(Media3VarintLengthMaskMessage, ignoreCase = true) == true

private fun isDavLikePlaybackSource(
    sourceUrl: String,
    sourceFilename: String?,
): Boolean {
    val normalized = "$sourceUrl ${sourceFilename.orEmpty()}".lowercase()
    if (listOf("nzbdav", "altmount", "webdav", "usenet").any(normalized::contains)) {
        return true
    }

    if (Regex("""^[a-z][a-z0-9+.-]*://[^/@]+:[^/@]+@""")
            .containsMatchIn(sourceUrl.lowercase())
    ) {
        return true
    }

    return sourceUrlLooksExtensionless(sourceUrl) && sourceFilename.hasPlayableVideoExtension()
}

private fun isMatroskaPlaybackSource(
    sourceUrl: String,
    responseHeaders: Map<String, String>?,
    sourceFilename: String?,
): Boolean {
    val mimeType = inferPlaybackMimeType(
        sourceUrl = sourceUrl,
        responseHeaders = responseHeaders,
        sourceFilename = sourceFilename,
    )?.lowercase()
    return mimeType == "video/x-matroska" || mimeType == "video/webm"
}

private fun sourceUrlLooksExtensionless(sourceUrl: String): Boolean {
    val path = sourceUrl.substringBefore('?').substringBefore('#')
    val lastPathSegment = path.substringAfterLast('/')
    return lastPathSegment.isNotBlank() && !lastPathSegment.contains('.')
}

private fun String?.hasPlayableVideoExtension(): Boolean {
    val value = this?.lowercase() ?: return false
    return listOf(
        ".mkv",
        ".mk3d",
        ".webm",
        ".mp4",
        ".m4v",
        ".mov",
        ".avi",
        ".ts",
        ".m2ts",
        ".mts",
        ".mpg",
        ".mpeg",
        ".flv",
    ).any(value::contains)
}

private val genericPlaybackContentTypes = setOf(
    "application/octet-stream",
    "binary/octet-stream",
    "application/download",
    "application/force-download",
)

private fun Map<String, String>.valueForHeader(name: String): String? =
    entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.value

private fun filenameFromContentDisposition(contentDisposition: String?): String? {
    if (contentDisposition.isNullOrBlank()) return null
    val parts = contentDisposition.split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() }

    parts.firstOrNull { it.startsWith("filename*=", ignoreCase = true) }
        ?.substringAfter('=')
        ?.trim()
        ?.trim('"')
        ?.let { value -> value.substringAfter("''", missingDelimiterValue = value) }
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    return parts.firstOrNull { it.startsWith("filename=", ignoreCase = true) }
        ?.substringAfter('=')
        ?.trim()
        ?.trim('"')
        ?.takeIf { it.isNotBlank() }
}

private fun inferPlaybackMimeTypeFromName(name: String): String? {
    val extension = name.substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/', missingDelimiterValue = name)
        .substringAfterLast('\\', missingDelimiterValue = name)
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .trim()

    return when (extension) {
        "mkv", "mk3d" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mp4", "m4v" -> "video/mp4"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "ts", "m2ts", "mts" -> "video/mp2t"
        "mpg", "mpeg" -> "video/mpeg"
        "flv" -> "video/x-flv"
        "m3u8" -> "application/x-mpegURL"
        "mpd" -> "application/dash+xml"
        else -> null
    }
}

@Composable
expect fun PlatformPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String? = null,
    sourceHeaders: Map<String, String> = emptyMap(),
    sourceResponseHeaders: Map<String, String> = emptyMap(),
    sourceFilename: String? = null,
    sourceVideoSize: Long? = null,
    useYoutubeChunkedPlayback: Boolean = false,
    modifier: Modifier = Modifier,
    playWhenReady: Boolean = true,
    resizeMode: PlayerResizeMode = PlayerResizeMode.Fit,
    useNativeController: Boolean = false,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
)

@Composable
expect fun PlatformSystemMediaControls(
    title: String?,
    subtitle: String?,
    artworkUrl: String?,
    controller: PlayerEngineController?,
    snapshot: PlayerPlaybackSnapshot,
    enabled: Boolean,
)
