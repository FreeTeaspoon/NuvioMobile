package com.nuvio.app.features.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RememberedAudioSelection(
    val trackId: String? = null,
    val language: String? = null,
    val label: String? = null,
    val index: Int = -1,
)

internal object RememberedAudioSelectionRepository {
    private var hasLoaded = false
    private var selections: Map<String, RememberedAudioSelection> = emptyMap()

    fun onProfileChanged() {
        hasLoaded = false
        selections = emptyMap()
    }

    fun clearLocalState() {
        hasLoaded = false
        selections = emptyMap()
    }

    fun selectionFor(contentKey: String?): RememberedAudioSelection? {
        ensureLoaded()
        val normalizedKey = contentKey?.takeIf { it.isNotBlank() } ?: return null
        return selections[normalizedKey]
    }

    fun saveSelection(contentKey: String?, track: AudioTrack) {
        ensureLoaded()
        val normalizedKey = contentKey?.takeIf { it.isNotBlank() } ?: return
        val selection = RememberedAudioSelection(
            trackId = track.id.takeIf { it.isNotBlank() },
            language = track.language?.takeIf { it.isNotBlank() },
            label = track.label.takeIf { it.isNotBlank() },
            index = track.index,
        )
        selections = selections + (normalizedKey to selection)
        PlayerSettingsStorage.saveRememberedAudioSelections(rememberedAudioJson.encodeToString(selections))
    }

    private fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        selections = decodeRememberedAudioSelections(PlayerSettingsStorage.loadRememberedAudioSelections())
    }
}

internal fun rememberedAudioContentKey(parentMetaType: String, parentMetaId: String): String? {
    val type = parentMetaType.trim().lowercase().takeIf { it.isNotBlank() } ?: return null
    val id = parentMetaId.trim().takeIf { it.isNotBlank() } ?: return null
    return "$type|$id"
}

internal fun resolveRememberedAudioTrackIndex(
    tracks: List<AudioTrack>,
    selection: RememberedAudioSelection?,
): Int {
    if (selection == null || tracks.isEmpty()) return -1

    val rememberedId = selection.trackId?.takeIf { it.isNotBlank() }
    if (rememberedId != null) {
        tracks.firstOrNull { it.id == rememberedId }?.let { return it.index }
    }

    val rememberedLanguage = selection.language.normalizedAudioMatchValue()
    val rememberedLabel = selection.label.normalizedAudioMatchValue()
    if (rememberedLanguage != null || rememberedLabel != null) {
        tracks.firstOrNull { track ->
            track.language.normalizedAudioMatchValue() == rememberedLanguage &&
                track.label.normalizedAudioMatchValue() == rememberedLabel
        }?.let { return it.index }
    }

    return tracks.firstOrNull { it.index == selection.index }?.index ?: -1
}

private fun String?.normalizedAudioMatchValue(): String? =
    this
        ?.trim()
        ?.lowercase()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotBlank() }

private fun decodeRememberedAudioSelections(raw: String?): Map<String, RememberedAudioSelection> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        rememberedAudioJson.decodeFromString(
            MapSerializer(String.serializer(), RememberedAudioSelection.serializer()),
            raw,
        )
    }.getOrDefault(emptyMap())
}

private val rememberedAudioJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
