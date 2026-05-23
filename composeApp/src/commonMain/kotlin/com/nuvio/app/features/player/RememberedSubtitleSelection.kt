package com.nuvio.app.features.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RememberedSubtitleSelection(
    val kind: RememberedSubtitleKind,
    val trackId: String? = null,
    val addonId: String? = null,
    val url: String? = null,
    val language: String? = null,
    val label: String? = null,
    val index: Int = -1,
)

@Serializable
enum class RememberedSubtitleKind {
    BuiltIn,
    Addon,
}

internal object RememberedSubtitleSelectionRepository {
    private var hasLoaded = false
    private var selections: Map<String, RememberedSubtitleSelection> = emptyMap()

    fun onProfileChanged() {
        hasLoaded = false
        selections = emptyMap()
    }

    fun clearLocalState() {
        hasLoaded = false
        selections = emptyMap()
    }

    fun selectionFor(contentKeys: List<String?>): RememberedSubtitleSelection? {
        ensureLoaded()
        return contentKeys
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .firstNotNullOfOrNull { selections[it] }
    }

    fun saveBuiltInSelection(contentKeys: List<String?>, track: SubtitleTrack) {
        saveSelection(
            contentKeys = contentKeys,
            selection = RememberedSubtitleSelection(
                kind = RememberedSubtitleKind.BuiltIn,
                trackId = track.id.takeIf { it.isNotBlank() },
                language = track.language?.takeIf { it.isNotBlank() },
                label = track.label.takeIf { it.isNotBlank() },
                index = track.index,
            ),
        )
    }

    fun saveAddonSelection(contentKeys: List<String?>, addon: AddonSubtitle) {
        saveSelection(
            contentKeys = contentKeys,
            selection = RememberedSubtitleSelection(
                kind = RememberedSubtitleKind.Addon,
                addonId = addon.id.takeIf { it.isNotBlank() },
                url = addon.url.takeIf { it.isNotBlank() },
                language = addon.language.takeIf { it.isNotBlank() },
                label = addon.display.takeIf { it.isNotBlank() },
            ),
        )
    }

    private fun saveSelection(contentKeys: List<String?>, selection: RememberedSubtitleSelection) {
        ensureLoaded()
        val keys = contentKeys.mapNotNull { it?.takeIf(String::isNotBlank) }.distinct()
        if (keys.isEmpty()) return
        selections = selections + keys.associateWith { selection }
        PlayerSettingsStorage.saveRememberedSubtitleSelections(rememberedSubtitleJson.encodeToString(selections))
    }

    private fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        selections = decodeRememberedSubtitleSelections(PlayerSettingsStorage.loadRememberedSubtitleSelections())
    }
}

internal fun rememberedSubtitleContentKeys(
    parentMetaType: String,
    parentMetaId: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    videoId: String?,
): List<String?> {
    val titleKey = rememberedAudioContentKey(parentMetaType, parentMetaId)
    val episodeKey = rememberedSubtitleEpisodeContentKey(
        parentKey = titleKey,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        videoId = videoId,
    )
    return listOf(episodeKey, titleKey)
}

internal fun resolveRememberedSubtitleTrackIndex(
    tracks: List<SubtitleTrack>,
    selection: RememberedSubtitleSelection?,
): Int {
    if (selection == null || selection.kind != RememberedSubtitleKind.BuiltIn || tracks.isEmpty()) return -1

    val rememberedId = selection.trackId?.takeIf { it.isNotBlank() }
    if (rememberedId != null) {
        tracks.firstOrNull { it.id == rememberedId }?.let { return it.index }
    }

    val rememberedLanguage = selection.language.normalizedSubtitleMatchValue()
    val rememberedLabel = selection.label.normalizedSubtitleMatchValue()
    if (rememberedLanguage != null || rememberedLabel != null) {
        tracks.firstOrNull { track ->
            track.language.normalizedSubtitleMatchValue() == rememberedLanguage &&
                track.label.normalizedSubtitleMatchValue() == rememberedLabel
        }?.let { return it.index }
    }

    return tracks.firstOrNull { it.index == selection.index }?.index ?: -1
}

internal fun resolveRememberedAddonSubtitle(
    addons: List<AddonSubtitle>,
    selection: RememberedSubtitleSelection?,
): AddonSubtitle? {
    if (selection == null || selection.kind != RememberedSubtitleKind.Addon || addons.isEmpty()) return null

    val rememberedAddonId = selection.addonId?.takeIf { it.isNotBlank() }
    val rememberedUrl = selection.url?.takeIf { it.isNotBlank() }
    if (rememberedAddonId != null || rememberedUrl != null) {
        addons.firstOrNull { addon ->
            (rememberedAddonId != null && addon.id == rememberedAddonId) ||
                (rememberedUrl != null && addon.url == rememberedUrl)
        }?.let { return it }
    }

    val rememberedLanguage = selection.language.normalizedSubtitleMatchValue()
    val rememberedLabel = selection.label.normalizedSubtitleMatchValue()
    return addons.firstOrNull { addon ->
        addon.language.normalizedSubtitleMatchValue() == rememberedLanguage &&
            addon.display.normalizedSubtitleMatchValue() == rememberedLabel
    }
}

private fun rememberedSubtitleEpisodeContentKey(
    parentKey: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
    videoId: String?,
): String? {
    val key = parentKey ?: return null
    val normalizedVideoId = videoId?.trim()?.takeIf { it.isNotBlank() }
    if (normalizedVideoId != null) return "$key|video|$normalizedVideoId"
    val season = seasonNumber ?: return null
    val episode = episodeNumber ?: return null
    return "$key|s$season|e$episode"
}

private fun String?.normalizedSubtitleMatchValue(): String? =
    this
        ?.trim()
        ?.lowercase()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotBlank() }

private fun decodeRememberedSubtitleSelections(raw: String?): Map<String, RememberedSubtitleSelection> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        rememberedSubtitleJson.decodeFromString(
            MapSerializer(String.serializer(), RememberedSubtitleSelection.serializer()),
            raw,
        )
    }.getOrDefault(emptyMap())
}

private val rememberedSubtitleJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
