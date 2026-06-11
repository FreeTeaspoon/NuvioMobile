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
    fun setMuted(muted: Boolean) {}
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

enum class PlayerControlsAction {
    ToggleChrome,
    RevealLockedOverlay,
    Back,
    TogglePlayback,
    KeyboardTogglePlayback,
    SeekBack,
    KeyboardSeekBack,
    SeekForward,
    KeyboardSeekForward,
    ResizeMode,
    Speed,
    Subtitles,
    Audio,
    Sources,
    Episodes,
    OpenExternalPlayer,
    SubmitIntro,
    LockToggle,
    VideoSettings,
    DoubleTapSeekBack,
    DoubleTapSeekForward,
}

data class PlayerControlsState(
    val title: String = "",
    val episodeText: String = "",
    val streamTitle: String = "",
    val providerName: String = "",
    val pauseOverlayWatchingLabel: String = "You're watching",
    val pauseOverlayLogo: String? = null,
    val pauseOverlayEpisodeInfo: String = "",
    val pauseOverlayEpisodeTitle: String = "",
    val pauseOverlayDescription: String = "",
    val resizeModeLabel: String = "Fit",
    val playbackSpeedLabel: String = "1x",
    val subtitlesLabel: String = "Subs",
    val audioLabel: String = "Audio",
    val sourcesLabel: String = "Sources",
    val episodesLabel: String = "Episodes",
    val externalPlayerLabel: String = "External",
    val playLabel: String = "Play",
    val pauseLabel: String = "Pause",
    val closeLabel: String = "Close player",
    val lockLabel: String = "Lock player controls",
    val unlockLabel: String = "Unlock player controls",
    val submitIntroLabel: String = "Submit Intro",
    val videoSettingsLabel: String = "Video settings",
    val tapToUnlockLabel: String = "Tap to unlock",
    val playbackErrorTitle: String = "Playback error",
    val playbackErrorMessage: String = "",
    val playbackErrorActionLabel: String = "Go back",
    val sourcesPanelTitle: String = "Sources",
    val episodesPanelTitle: String = "Episodes",
    val streamsPanelTitle: String = "Streams",
    val allFilterLabel: String = "All",
    val reloadLabel: String = "Reload",
    val backLabel: String = "Back",
    val panelCloseLabel: String = "Close",
    val cancelLabel: String = "Cancel",
    val playingLabel: String = "Playing",
    val noStreamsLabel: String = "No streams found",
    val noEpisodesLabel: String = "No episodes available",
    val submitIntroPanelTitle: String = "Submit Timestamps",
    val submitIntroSegmentTypeLabel: String = "SEGMENT TYPE",
    val submitIntroSegmentIntroLabel: String = "Intro",
    val submitIntroSegmentRecapLabel: String = "Recap",
    val submitIntroSegmentOutroLabel: String = "Outro",
    val submitIntroStartTimeLabel: String = "START TIME (MM:SS)",
    val submitIntroEndTimeLabel: String = "END TIME (MM:SS)",
    val submitIntroCaptureLabel: String = "Capture",
    val submitIntroSubmitLabel: String = "Submit",
    val p2pConsentTitle: String = "P2P Streaming",
    val p2pConsentBody: String = "",
    val p2pConsentEnableLabel: String = "Enable P2P",
    val p2pConsentCancelLabel: String = "Cancel",
    val subtitlesPanelTitle: String = "Subtitles",
    val subtitleBuiltInTabLabel: String = "Built-in",
    val subtitleAddonsTabLabel: String = "Addons",
    val subtitleStyleTabLabel: String = "Style",
    val noneLabel: String = "None",
    val fetchSubtitlesLabel: String = "Tap to fetch subtitles",
    val subtitleDelayLabel: String = "Subtitle Delay",
    val resetLabel: String = "Reset",
    val autoSyncLabel: String = "Auto Sync",
    val reloadSmallLabel: String = "Reload",
    val captureLineLabel: String = "Capture",
    val selectAddonSubtitleFirstLabel: String = "Select an addon subtitle first",
    val loadingSubtitleLinesLabel: String = "Loading subtitle lines...",
    val fontSizeLabel: String = "Font Size",
    val outlineLabel: String = "Outline",
    val boldLabel: String = "Bold",
    val bottomOffsetLabel: String = "Bottom Offset",
    val colorLabel: String = "Color",
    val textOpacityLabel: String = "Text Opacity",
    val outlineColorLabel: String = "Outline Color",
    val resetDefaultsLabel: String = "Reset Defaults",
    val onLabel: String = "On",
    val offLabel: String = "Off",
    val themeAccentColor: String = "#2f6fed",
    val themeAccentStrongColor: String = "#3c7bff",
    val themeOnAccentColor: String = "#ffffff",
    val themeFocusColor: String = "#9ecaff",
    val themeSelectedSurfaceColor: String = "#26384f",
    val themeSelectedSurfaceHoverColor: String = "#2d4565",
    val themeSelectedRingColor: String = "rgba(47, 111, 237, .35)",
    val themeTimelineFillColor: String = "#ffffff",
    val themeTimelineTrackColor: String = "rgba(255, 255, 255, .28)",
    val themeBufferingColor: String = "#ffffff",
    val themeBufferingTrackColor: String = "rgba(255, 255, 255, .28)",
    val themeControlForegroundColor: String = "#ffffff",
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isLocked: Boolean = false,
    val lockedOverlayVisible: Boolean = false,
    val controlsVisible: Boolean = true,
    val parentalWarnings: List<ParentalWarning> = emptyList(),
    val showParentalGuide: Boolean = false,
    val showOpeningOverlay: Boolean = false,
    val openingArtwork: String? = null,
    val openingLogo: String? = null,
    val openingTitle: String = "",
    val openingMessage: String? = null,
    val openingProgress: Float? = null,
    val skipPromptVisible: Boolean = false,
    val skipPromptLabel: String = "Skip",
    val skipPromptStartMs: Long = 0L,
    val skipPromptEndMs: Long = 0L,
    val skipPromptDismissed: Boolean = false,
    val nextEpisodeVisible: Boolean = false,
    val nextEpisodeHeaderLabel: String = "Next episode",
    val nextEpisodeTitle: String = "",
    val nextEpisodeThumbnail: String = "",
    val nextEpisodeStatus: String = "",
    val nextEpisodeActionLabel: String = "Play",
    val nextEpisodePlayable: Boolean = false,
    val showSubmitIntro: Boolean = false,
    val showVideoSettings: Boolean = false,
    val showSources: Boolean = false,
    val showEpisodes: Boolean = false,
    val showExternalPlayer: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val sourceIsLoading: Boolean = false,
    val sourceFilters: List<PlayerControlFilterItem> = emptyList(),
    val sourceItems: List<PlayerControlSourceItem> = emptyList(),
    val episodeItems: List<PlayerControlEpisodeItem> = emptyList(),
    val episodeSeasons: List<PlayerControlSeasonItem> = emptyList(),
    val episodeStreamsVisible: Boolean = false,
    val episodeStreamsIsLoading: Boolean = false,
    val selectedEpisodeLabel: String = "",
    val episodeStreamFilters: List<PlayerControlFilterItem> = emptyList(),
    val episodeStreamItems: List<PlayerControlSourceItem> = emptyList(),
    val submitIntroSegmentType: String = "intro",
    val submitIntroStartTime: String = "00:00",
    val submitIntroEndTime: String = "00:00",
    val isSubmitIntroSubmitting: Boolean = false,
    val submitIntroStatusMessage: String = "",
    val showP2pConsent: Boolean = false,
    val subtitleActiveTab: String = "BuiltIn",
    val addonSubtitleItems: List<PlayerControlAddonSubtitleItem> = emptyList(),
    val isLoadingAddonSubtitles: Boolean = false,
    val selectedAddonSubtitleId: String = "",
    val useCustomSubtitles: Boolean = false,
    val subtitleStyle: SubtitleStyleState = SubtitleStyleState.DEFAULT,
    val subtitleDelayMs: Int = 0,
    val hasSelectedAddonSubtitle: Boolean = false,
    val subtitleAutoSyncCapturedPositionMs: Long = -1L,
    val subtitleAutoSyncCues: List<PlayerControlSubtitleCueItem> = emptyList(),
    val subtitleAutoSyncIsLoading: Boolean = false,
    val subtitleAutoSyncErrorMessage: String = "",
    val closeModalsToken: Long = 0L,
)

data class PlayerControlFilterItem(
    val id: String = "",
    val label: String = "",
    val isSelected: Boolean = false,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
)

data class PlayerControlSeasonItem(
    val season: Int = 0,
    val label: String = "",
    val isSelected: Boolean = false,
)

data class PlayerControlSourceItem(
    val index: Int = 0,
    val filterId: String = "",
    val label: String = "",
    val subtitle: String = "",
    val addonName: String = "",
    val isCurrent: Boolean = false,
    val isEnabled: Boolean = true,
)

data class PlayerControlEpisodeItem(
    val index: Int = 0,
    val id: String = "",
    val title: String = "",
    val code: String = "",
    val overview: String = "",
    val thumbnail: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val isCurrent: Boolean = false,
    val isWatched: Boolean = false,
)

data class PlayerControlAddonSubtitleItem(
    val index: Int = 0,
    val id: String = "",
    val display: String = "",
    val languageLabel: String = "",
    val addonName: String = "",
    val isSelected: Boolean = false,
)

data class PlayerControlSubtitleCueItem(
    val index: Int = 0,
    val timeMs: Long = 0L,
    val timeLabel: String = "",
    val text: String = "",
)

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
    initialPositionMs: Long = 0L,
    useNativeController: Boolean = false,
    playerControlsState: PlayerControlsState = PlayerControlsState(),
    onPlayerControlsAction: (PlayerControlsAction) -> Boolean = { false },
    onPlayerControlsEvent: (String, Double) -> Boolean = { _, _ -> false },
    onPlayerControlsScrubChange: (Long) -> Boolean = { false },
    onPlayerControlsScrubFinished: (Long) -> Boolean = { false },
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
