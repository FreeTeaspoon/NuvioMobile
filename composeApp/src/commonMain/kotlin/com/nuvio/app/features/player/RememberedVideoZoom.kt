package com.nuvio.app.features.player

import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object RememberedVideoZoomRepository {
    private var loadedProfileId: Int? = null
    private var zooms: Map<String, Float> = emptyMap()

    fun zoomFor(contentKey: String?): Float? {
        ensureLoaded()
        val normalizedKey = contentKey?.takeIf { it.isNotBlank() } ?: return null
        return zooms[normalizedKey]
    }

    fun saveZoom(contentKey: String?, zoom: Float) {
        ensureLoaded()
        val normalizedKey = contentKey?.takeIf { it.isNotBlank() } ?: return
        zooms = rememberedVideoZoomsAfterSave(zooms, normalizedKey, zoom)
        VideoZoomStorage.save(encodeRememberedVideoZooms(zooms))
    }

    private fun ensureLoaded() {
        val profileId = ProfileRepository.activeProfileId
        if (loadedProfileId == profileId) return
        loadedProfileId = profileId
        zooms = decodeRememberedVideoZooms(VideoZoomStorage.load())
    }
}

internal fun rememberedVideoZoomContentKey(parentMetaType: String, parentMetaId: String): String? {
    val type = parentMetaType.trim().lowercase().takeIf { it.isNotBlank() } ?: return null
    val id = parentMetaId.trim().takeIf { it.isNotBlank() } ?: return null
    return "$type|$id"
}

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
