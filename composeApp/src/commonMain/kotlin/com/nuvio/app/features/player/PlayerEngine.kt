package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    fun clearExternalSubtitle()
    fun clearExternalSubtitleAndSelect(trackIndex: Int)
    fun applySubtitleStyle(style: SubtitleStyleState) {}
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
