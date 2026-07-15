package com.nuvio.app.features.downloads

import com.nuvio.app.features.streams.StreamItem
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

object DownloadsRepository {
    private const val progressPublishIntervalMs = 500L

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val activeHandles = mutableMapOf<String, DownloadsTaskHandle>()
    private var hasLoaded = false
    private var nextDownloadOrdinal = 0L

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun clearLocalState() {
        activeHandles.values.forEach(DownloadsTaskHandle::cancel)
        activeHandles.clear()
        hasLoaded = false
        _uiState.value = DownloadsUiState(
            autoOpenOnOffline = _uiState.value.autoOpenOnOffline,
        )
        notifyLiveStatusPlatform()
    }

    fun setAutoOpenOnOffline(enabled: Boolean) {
        ensureLoaded()
        _uiState.update { state ->
            state.copy(autoOpenOnOffline = enabled)
        }
        DownloadsStorage.saveAutoOpenOnOffline(enabled)
    }

    fun findPlayableDownloadByVideoId(videoId: String?): DownloadItem? {
        ensureLoaded()
        val normalizedVideoId = videoId?.trim().orEmpty()
        if (normalizedVideoId.isBlank()) return null
        return _uiState.value.items.firstOrNull { item ->
            item.videoId == normalizedVideoId && item.hasPlayableLocalFile()
        }
    }

    fun findPlayableDownload(
        parentMetaId: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        videoId: String? = null,
    ): DownloadItem? {
        ensureLoaded()
        val items = _uiState.value.items
        val normalizedParentMetaId = parentMetaId.trim()

        findPlayableDownloadByVideoId(videoId)?.let { return it }

        return if (seasonNumber != null && episodeNumber != null) {
            items.firstOrNull { item ->
                item.parentMetaId == normalizedParentMetaId &&
                    item.seasonNumber == seasonNumber &&
                    item.episodeNumber == episodeNumber &&
                    item.hasPlayableLocalFile()
            }
        } else {
            items.firstOrNull { item ->
                item.parentMetaId == normalizedParentMetaId &&
                    item.seasonNumber == null &&
                    item.episodeNumber == null &&
                    item.hasPlayableLocalFile()
            }
        }
    }

    fun playableLocalFileUri(item: DownloadItem): String? {
        ensureLoaded()
        if (item.status != DownloadStatus.Completed) return null
        val resolvedUri = DownloadsPlatformDownloader.resolveLocalFileUri(
            localFileUri = item.localFileUri,
            destinationFileName = item.fileName,
        ) ?: return null

        if (resolvedUri != item.localFileUri) {
            mutateItem(item.id) { current ->
                if (current.fileName == item.fileName) {
                    current.copy(
                        localFileUri = resolvedUri,
                        updatedAtEpochMs = DownloadsClock.nowEpochMs(),
                    )
                } else {
                    current
                }
            }
        }

        return resolvedUri
    }

    fun enqueueFromStream(
        contentType: String,
        videoId: String,
        parentMetaId: String,
        parentMetaType: String,
        title: String,
        logo: String?,
        poster: String?,
        background: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        episodeTitle: String?,
        episodeThumbnail: String?,
        stream: StreamItem,
    ): DownloadEnqueueResult {
        ensureLoaded()

        val sourceUrl = stream.playableDirectUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return DownloadEnqueueResult.MissingUrl

        if (!sourceUrl.isSupportedDownloadUrl()) {
            return DownloadEnqueueResult.UnsupportedFormat
        }

        val now = DownloadsClock.nowEpochMs()
        val logicalKey = buildLogicalKey(
            parentMetaId = parentMetaId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        )

        var replacedExisting = false
        val currentItems = _uiState.value.items.toMutableList()
        val existing = currentItems.firstOrNull { it.logicalContentKey == logicalKey }
        if (existing != null) {
            replacedExisting = true
            activeHandles.remove(existing.id)?.cancel()
            DownloadsPlatformDownloader.removeFile(playableLocalFileUri(existing) ?: existing.localFileUri)
            DownloadsPlatformDownloader.removePartialFile(existing.fileName)
            currentItems.removeAll { it.id == existing.id }
        }

        val downloadId = nextDownloadId(now)
        val fileName = buildFileName(
            title = title,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            fallbackTitle = stream.streamLabel,
            sourceUrl = sourceUrl,
            nowEpochMs = now,
        )

        val item = DownloadItem(
            id = downloadId,
            contentType = contentType,
            parentMetaId = parentMetaId,
            parentMetaType = parentMetaType,
            videoId = videoId,
            title = title,
            logo = logo,
            poster = poster,
            background = background,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            episodeThumbnail = episodeThumbnail,
            streamTitle = stream.streamLabel,
            streamSubtitle = stream.streamSubtitle,
            providerName = stream.addonName,
            providerAddonId = stream.addonId,
            sourceUrl = sourceUrl,
            sourceHeaders = sanitizeRequestHeaders(stream.behaviorHints.proxyHeaders?.request),
            sourceResponseHeaders = sanitizeResponseHeaders(stream.behaviorHints.proxyHeaders?.response),
            localFileUri = null,
            fileName = fileName,
            status = DownloadStatus.Downloading,
            downloadedBytes = 0L,
            totalBytes = null,
            errorMessage = null,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )

        currentItems.add(0, item)
        publish(currentItems)
        persist()
        startDownload(item)

        return if (replacedExisting) {
            DownloadEnqueueResult.Replaced
        } else {
            DownloadEnqueueResult.Started
        }
    }

    fun pauseDownload(downloadId: String) {
        ensureLoaded()
        val item = _uiState.value.items.firstOrNull { it.id == downloadId } ?: return
        if (item.status != DownloadStatus.Downloading) return

        activeHandles.remove(downloadId)?.cancel()
        mutateItem(downloadId) { current ->
            current.copy(
                status = DownloadStatus.Paused,
                updatedAtEpochMs = DownloadsClock.nowEpochMs(),
                errorMessage = null,
            )
        }
    }

    fun pauseActiveDownloads() {
        ensureLoaded()
        _uiState.value.items
            .filter { it.status == DownloadStatus.Downloading }
            .map { it.id }
            .forEach(::pauseDownload)
    }

    fun resumeDownload(downloadId: String) {
        ensureLoaded()
        val item = _uiState.value.items.firstOrNull { it.id == downloadId } ?: return
        if (item.status != DownloadStatus.Paused && item.status != DownloadStatus.Failed) return

        val reset = item.copy(
            status = DownloadStatus.Downloading,
            errorMessage = null,
            localFileUri = null,
            updatedAtEpochMs = DownloadsClock.nowEpochMs(),
        )

        replaceItem(reset)
        persist()
        startDownload(reset)
    }

    fun retryDownload(downloadId: String) {
        resumeDownload(downloadId)
    }

    fun cancelDownload(downloadId: String) {
        ensureLoaded()
        val item = _uiState.value.items.firstOrNull { it.id == downloadId } ?: return

        activeHandles.remove(downloadId)?.cancel()
        DownloadsPlatformDownloader.removeFile(playableLocalFileUri(item) ?: item.localFileUri)
        DownloadsPlatformDownloader.removePartialFile(item.fileName)

        publish(_uiState.value.items.filterNot { it.id == downloadId })
        persist()
    }

    private fun loadFromDisk() {
        hasLoaded = true
        val autoOpenOnOffline = DownloadsStorage.loadAutoOpenOnOffline() ?: true
        val payload = DownloadsStorage.loadPayload().orEmpty().trim()

        var shouldPersistNormalized = false
        val decoded = if (payload.isEmpty()) {
            emptyList()
        } else {
            DownloadsCodec.decodeItems(payload)
        }
        val normalized = decoded
            .map { item ->
                val statusNormalized = if (item.status == DownloadStatus.Downloading) {
                    item.copy(
                        status = DownloadStatus.Paused,
                        errorMessage = null,
                    )
                } else {
                    item
                }

                val recoveredMetadataNormalized = normalizeRecoveredEpisodeMetadata(statusNormalized)
                val localUriNormalized = normalizeCompletedLocalFileUri(recoveredMetadataNormalized)
                if (localUriNormalized != item) {
                    shouldPersistNormalized = true
                }
                localUriNormalized
            }
        val recovered = recoverMissingLocalFiles(normalized)
        val items = normalized + recovered

        _uiState.value = DownloadsUiState(
            items = items,
            autoOpenOnOffline = autoOpenOnOffline,
        )
        notifyLiveStatusPlatform()
        if (shouldPersistNormalized || recovered.isNotEmpty()) {
            persist()
        }
    }

    private fun startDownload(item: DownloadItem) {
        val request = DownloadPlatformRequest(
            sourceUrl = item.sourceUrl,
            sourceHeaders = item.sourceHeaders,
            destinationFileName = item.fileName,
        )
        var lastPublishedProgressAtEpochMs = Long.MIN_VALUE
        var lastPublishedDownloadedBytes = -1L
        var lastPublishedTotalBytes: Long? = null

        val handle = DownloadsPlatformDownloader.start(
            request = request,
            onProgress = { downloadedBytes, totalBytes ->
                val normalizedDownloadedBytes = downloadedBytes.coerceAtLeast(0L)
                val normalizedTotalBytes = totalBytes?.takeIf { it > 0L }
                val now = DownloadsClock.nowEpochMs()
                val isFirstUpdate = lastPublishedDownloadedBytes < 0L
                val restarted = normalizedDownloadedBytes < lastPublishedDownloadedBytes
                val totalChanged = normalizedTotalBytes != lastPublishedTotalBytes
                val reachedEnd = normalizedTotalBytes != null &&
                    normalizedDownloadedBytes >= normalizedTotalBytes
                val elapsedMs = if (lastPublishedProgressAtEpochMs == Long.MIN_VALUE) {
                    Long.MAX_VALUE
                } else {
                    now - lastPublishedProgressAtEpochMs
                }

                if (
                    !isFirstUpdate &&
                    !restarted &&
                    !totalChanged &&
                    !reachedEnd &&
                    elapsedMs in 0 until progressPublishIntervalMs
                ) {
                    return@start
                }

                lastPublishedProgressAtEpochMs = now
                lastPublishedDownloadedBytes = normalizedDownloadedBytes
                lastPublishedTotalBytes = normalizedTotalBytes
                mutateItem(item.id) { current ->
                    if (current.status != DownloadStatus.Downloading) {
                        current
                    } else {
                        current.copy(
                            downloadedBytes = normalizedDownloadedBytes,
                            totalBytes = normalizedTotalBytes,
                            updatedAtEpochMs = now,
                            errorMessage = null,
                        )
                    }
                }
            },
            onSuccess = { localFileUri, totalBytes ->
                activeHandles.remove(item.id)
                mutateItem(item.id) { current ->
                    current.copy(
                        status = DownloadStatus.Completed,
                        localFileUri = localFileUri,
                        downloadedBytes = if (totalBytes != null && totalBytes > 0L) {
                            totalBytes
                        } else {
                            current.downloadedBytes
                        },
                        totalBytes = totalBytes?.takeIf { it > 0L } ?: current.totalBytes,
                        errorMessage = null,
                        updatedAtEpochMs = DownloadsClock.nowEpochMs(),
                    )
                }
            },
            onFailure = { message ->
                activeHandles.remove(item.id)
                mutateItem(item.id) { current ->
                    if (current.status != DownloadStatus.Downloading) {
                        current
                    } else {
                        current.copy(
                            status = DownloadStatus.Failed,
                            errorMessage = message.ifBlank { runBlocking { getString(Res.string.download_failed) } },
                            updatedAtEpochMs = DownloadsClock.nowEpochMs(),
                        )
                    }
                }
            },
        )

        activeHandles[item.id] = handle
    }

    private fun mutateItem(downloadId: String, transform: (DownloadItem) -> DownloadItem) {
        var changed = false
        val updated = _uiState.value.items.map { item ->
            if (item.id == downloadId) {
                changed = true
                transform(item)
            } else {
                item
            }
        }

        if (changed) {
            publish(updated)
            persist()
        }
    }

    private fun replaceItem(item: DownloadItem) {
        val updated = _uiState.value.items.map { existing ->
            if (existing.id == item.id) item else existing
        }
        publish(updated)
    }

    private fun publish(items: List<DownloadItem>) {
        _uiState.value = DownloadsUiState(
            items = items,
            autoOpenOnOffline = _uiState.value.autoOpenOnOffline,
        )
        notifyLiveStatusPlatform()
    }

    private fun notifyLiveStatusPlatform() {
        runCatching {
            DownloadsLiveStatusPlatform.onItemsChanged(_uiState.value.items)
        }
    }

    private fun persist() {
        DownloadsStorage.savePayload(
            DownloadsCodec.encodeItems(_uiState.value.items),
        )
    }

    private fun nextDownloadId(nowEpochMs: Long): String {
        nextDownloadOrdinal += 1L
        return buildString {
            append(nowEpochMs.toString(36))
            append('_')
            append(nextDownloadOrdinal.toString(36))
        }
    }

    private fun normalizeCompletedLocalFileUri(item: DownloadItem): DownloadItem {
        if (item.status != DownloadStatus.Completed) return item
        val resolvedUri = DownloadsPlatformDownloader.resolveLocalFileUri(
            localFileUri = item.localFileUri,
            destinationFileName = item.fileName,
        ) ?: return item
        return if (resolvedUri != item.localFileUri) {
            item.copy(localFileUri = resolvedUri)
        } else {
            item
        }
    }

    private fun normalizeRecoveredEpisodeMetadata(item: DownloadItem): DownloadItem {
        if (item.seasonNumber != null || item.episodeNumber != null) return item
        if (!item.id.startsWith("recovered_") && item.contentType != RecoveredDownloadContentType) return item
        val metadata = item.fileName.recoveredDownloadMetadata() as? RecoveredDownloadMetadata.Episode
            ?: return item

        return item.copy(
            contentType = RecoveredEpisodeContentType,
            parentMetaId = metadata.parentMetaId,
            parentMetaType = RecoveredEpisodeContentType,
            videoId = metadata.videoId,
            title = metadata.showTitle,
            seasonNumber = metadata.seasonNumber,
            episodeNumber = metadata.episodeNumber,
            episodeTitle = metadata.episodeTitle,
            streamTitle = metadata.displayTitle,
        )
    }

    private fun recoverMissingLocalFiles(existingItems: List<DownloadItem>): List<DownloadItem> {
        val knownFileNames = existingItems
            .map { it.fileName }
            .filter { it.isNotBlank() }
            .toSet()
        val knownLocalUris = existingItems
            .mapNotNull { item ->
                DownloadsPlatformDownloader.resolveLocalFileUri(
                    localFileUri = item.localFileUri,
                    destinationFileName = item.fileName,
                )
            }
            .toSet()

        return DownloadsPlatformDownloader.listCompletedFiles()
            .filterNot { file -> file.fileName in knownFileNames || file.localFileUri in knownLocalUris }
            .map(::buildRecoveredDownloadItem)
    }

    private fun DownloadItem.hasPlayableLocalFile(): Boolean =
        status == DownloadStatus.Completed &&
            DownloadsPlatformDownloader.resolveLocalFileUri(
                localFileUri = localFileUri,
                destinationFileName = fileName,
            ) != null
}

private fun buildRecoveredDownloadItem(file: LocalDownloadFile): DownloadItem {
    val timestamp = file.lastModifiedEpochMs.takeIf { it > 0L } ?: DownloadsClock.nowEpochMs()
    val metadata = file.fileName.recoveredDownloadMetadata()
    val stableKey = file.fileName.sanitizeFileName().ifBlank { "file" }

    return DownloadItem(
        id = "recovered_${stableKey.take(80)}_${timestamp.toString(36)}",
        contentType = metadata.contentType,
        parentMetaId = metadata.parentMetaId,
        parentMetaType = metadata.contentType,
        videoId = metadata.videoId,
        title = metadata.title,
        seasonNumber = metadata.seasonNumber,
        episodeNumber = metadata.episodeNumber,
        episodeTitle = metadata.episodeTitle,
        streamTitle = metadata.displayTitle,
        providerName = "Local file",
        sourceUrl = file.localFileUri,
        localFileUri = file.localFileUri,
        fileName = file.fileName,
        status = DownloadStatus.Completed,
        downloadedBytes = file.sizeBytes,
        totalBytes = file.sizeBytes,
        createdAtEpochMs = timestamp,
        updatedAtEpochMs = timestamp,
    )
}

private const val RecoveredDownloadContentType = "local"
private const val RecoveredEpisodeContentType = "series"
private const val RecoveredMovieContentType = "movie"

private sealed class RecoveredDownloadMetadata {
    abstract val contentType: String
    abstract val parentMetaId: String
    abstract val videoId: String
    abstract val title: String
    abstract val displayTitle: String
    open val seasonNumber: Int? = null
    open val episodeNumber: Int? = null
    open val episodeTitle: String? = null

    data class Movie(
        override val title: String,
        private val stableKey: String,
    ) : RecoveredDownloadMetadata() {
        override val contentType: String = RecoveredMovieContentType
        override val parentMetaId: String = "recovered:movie:$stableKey"
        override val videoId: String = "recovered:movie:$stableKey"
        override val displayTitle: String = title
    }

    data class Episode(
        val showTitle: String,
        override val seasonNumber: Int,
        override val episodeNumber: Int,
        override val episodeTitle: String?,
        private val showKey: String,
        private val fileKey: String,
        override val displayTitle: String,
    ) : RecoveredDownloadMetadata() {
        override val contentType: String = RecoveredEpisodeContentType
        override val parentMetaId: String = "recovered:series:$showKey"
        override val videoId: String = "recovered:episode:$fileKey"
        override val title: String = showTitle
    }
}

@Serializable
private data class StoredDownloadsPayload(
    val items: List<DownloadItem> = emptyList(),
)

private object DownloadsCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun decodeItems(payload: String): List<DownloadItem> =
        runCatching {
            json.decodeFromString<StoredDownloadsPayload>(payload).items
        }.getOrDefault(emptyList())

    fun encodeItems(items: Collection<DownloadItem>): String =
        json.encodeToString(
            StoredDownloadsPayload(
                items = items.toList(),
            ),
        )
}

private fun sanitizeRequestHeaders(headers: Map<String, String>?): Map<String, String> =
    headers
        .orEmpty()
        .mapNotNull { (key, value) ->
            val normalizedKey = key.trim()
            val normalizedValue = value.trim()
            if (
                normalizedKey.isBlank() ||
                normalizedValue.isBlank() ||
                normalizedKey.equals("Accept-Encoding", ignoreCase = true) ||
                normalizedKey.equals("Range", ignoreCase = true)
            ) {
                null
            } else {
                normalizedKey to normalizedValue
            }
        }
        .toMap()

private fun sanitizeResponseHeaders(headers: Map<String, String>?): Map<String, String> =
    headers
        .orEmpty()
        .mapNotNull { (key, value) ->
            val normalizedKey = key.trim()
            val normalizedValue = value.trim()
            if (normalizedKey.isBlank() || normalizedValue.isBlank()) {
                null
            } else {
                normalizedKey to normalizedValue
            }
        }
        .toMap()

private fun buildLogicalKey(
    parentMetaId: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
): String = if (seasonNumber != null && episodeNumber != null) {
    "${parentMetaId.trim()}|$seasonNumber|$episodeNumber"
} else {
    "${parentMetaId.trim()}|movie"
}

private fun buildFileName(
    title: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    fallbackTitle: String,
    sourceUrl: String,
    nowEpochMs: Long,
): String {
    val baseTitle = if (seasonNumber != null && episodeNumber != null) {
        buildString {
            append(title)
            append(" S")
            append(seasonNumber.toString().padStart(2, '0'))
            append('E')
            append(episodeNumber.toString().padStart(2, '0'))
            if (!episodeTitle.isNullOrBlank()) {
                append(' ')
                append(episodeTitle)
            }
        }
    } else {
        title.ifBlank { fallbackTitle }
    }

    val extension = sourceUrl.fileExtensionFromUrl()
    return buildString {
        append(baseTitle.sanitizeFileName().ifBlank { "download" }.take(92))
        append('_')
        append(nowEpochMs.toString(36))
        append('.')
        append(extension)
    }
}

private fun String.recoveredDownloadMetadata(): RecoveredDownloadMetadata {
    val displayName = recoveredDisplayNameFromFileName()
    val stableKey = displayName.sanitizeFileName().ifBlank { "file" }
    val episodeMatch = episodeCodeRegex.find(displayName)
    if (episodeMatch != null) {
        val showTitle = displayName.substring(0, episodeMatch.range.first).trim()
        val episodeTitle = displayName.substring(episodeMatch.range.last + 1).trim().ifBlank { null }
        val seasonNumber = episodeMatch.groupValues.getOrNull(1)?.toIntOrNull()
        val episodeNumber = episodeMatch.groupValues.getOrNull(2)?.toIntOrNull()
        if (showTitle.isNotBlank() && seasonNumber != null && episodeNumber != null) {
            return RecoveredDownloadMetadata.Episode(
                showTitle = showTitle,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                showKey = showTitle.sanitizeFileName().ifBlank { "show" },
                fileKey = stableKey,
                displayTitle = displayName,
            )
        }
    }

    return RecoveredDownloadMetadata.Movie(
        title = displayName,
        stableKey = stableKey,
    )
}

private val episodeCodeRegex = Regex("""(?i)\bS(\d{1,2})E(\d{1,3})\b""")

private fun String.recoveredDisplayNameFromFileName(): String {
    val withoutExtension = substringBeforeLast('.', missingDelimiterValue = this)
    val withoutGeneratedSuffix = withoutExtension.replace(Regex("_[0-9a-z]{6,}$"), "")
    return withoutGeneratedSuffix
        .replace('_', ' ')
        .trim()
        .ifBlank { "Recovered download" }
}

private fun String.sanitizeFileName(): String =
    trim().replace(Regex("[^A-Za-z0-9._ -]"), "_")

private fun String.fileExtensionFromUrl(): String {
    val withoutQuery = substringBefore('?').substringBefore('#')
    val suffix = withoutQuery.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .trim()

    return if (suffix.length in 2..5 && suffix.all { it.isLetterOrDigit() }) {
        suffix
    } else {
        "mp4"
    }
}

private fun String.isSupportedDownloadUrl(): Boolean {
    val normalized = trim().lowercase()
    if (normalized.startsWith("magnet:")) return false
    if (normalized.endsWith(".m3u8") || normalized.contains(".m3u8?")) return false
    if (normalized.endsWith(".mpd") || normalized.contains(".mpd?")) return false
    if (normalized.endsWith(".torrent") || normalized.contains(".torrent?")) return false
    return normalized.startsWith("http://") || normalized.startsWith("https://")
}
