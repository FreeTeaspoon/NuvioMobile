package com.nuvio.app.features.player

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object RememberedVideoZoomRepository {
    private var hasLoaded = false
    private var zooms: Map<String, Float> = emptyMap()

    fun onProfileChanged() {
        hasLoaded = false
        zooms = emptyMap()
    }

    fun clearLocalState() {
        hasLoaded = false
        zooms = emptyMap()
    }

    fun zoomFor(contentKey: String?): Float? {
        ensureLoaded()
        val normalizedKey = contentKey?.takeIf { it.isNotBlank() } ?: return null
        return zooms[normalizedKey]
    }

    fun saveZoom(contentKey: String?, zoom: Float) {
        ensureLoaded()
        val normalizedKey = contentKey?.takeIf { it.isNotBlank() } ?: return
        zooms = rememberedVideoZoomsAfterSave(zooms, normalizedKey, zoom)
        PlayerSettingsStorage.saveRememberedVideoZooms(encodeRememberedVideoZooms(zooms))
    }

    private fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        zooms = decodeRememberedVideoZooms(PlayerSettingsStorage.loadRememberedVideoZooms())
    }
}

internal fun rememberedVideoZoomContentKey(parentMetaType: String, parentMetaId: String): String? =
    rememberedAudioContentKey(parentMetaType, parentMetaId)

internal fun decodeRememberedVideoZooms(raw: String?): Map<String, Float> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        rememberedVideoZoomJson.decodeFromString(
            MapSerializer(String.serializer(), Float.serializer()),
            raw,
        ).mapValues { (_, zoom) -> clampPlayerVideoZoom(zoom) }
    }.getOrDefault(emptyMap())
}

internal fun rememberedVideoZoomsAfterSave(
    current: Map<String, Float>,
    contentKey: String?,
    zoom: Float,
): Map<String, Float> {
    val normalizedKey = contentKey?.takeIf { it.isNotBlank() } ?: return current
    return current + (normalizedKey to clampPlayerVideoZoom(zoom))
}

private fun encodeRememberedVideoZooms(zooms: Map<String, Float>): String =
    rememberedVideoZoomJson.encodeToString(
        MapSerializer(String.serializer(), Float.serializer()),
        zooms,
    )

private val rememberedVideoZoomJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
