package com.nuvio.app.features.player

import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUserDefaults

internal actual object PlayerTrackPreferenceStorage {
    private const val subtitleTypeKey = "subtitle_type"
    private const val subtitleLanguageKey = "subtitle_language"
    private const val subtitleNameKey = "subtitle_name"
    private const val subtitleTrackIdKey = "subtitle_track_id"
    private const val addonSubtitleIdKey = "addon_subtitle_id"
    private const val addonSubtitleUrlKey = "addon_subtitle_url"
    private const val addonSubtitleAddonNameKey = "addon_subtitle_addon_name"
    private const val audioLanguageKey = "audio_language"
    private const val audioNameKey = "audio_name"
    private const val audioTrackIdKey = "audio_track_id"
    private const val subtitleDelayMsKey = "subtitle_delay_ms"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    actual fun load(contentId: String): PersistedPlayerTrackPreference? {
        val id = contentId.normalizedStorageId() ?: return null
        val preference = PersistedPlayerTrackPreference(
            subtitleType = loadString(subtitleTypeKey, id),
            subtitleLanguage = loadString(subtitleLanguageKey, id),
            subtitleName = loadString(subtitleNameKey, id),
            subtitleTrackId = loadString(subtitleTrackIdKey, id),
            addonSubtitleId = loadString(addonSubtitleIdKey, id),
            addonSubtitleUrl = loadString(addonSubtitleUrlKey, id),
            addonSubtitleAddonName = loadString(addonSubtitleAddonNameKey, id),
            audioLanguage = loadString(audioLanguageKey, id),
            audioName = loadString(audioNameKey, id),
            audioTrackId = loadString(audioTrackIdKey, id),
        )
        return preference.takeIf {
            listOf(
                it.subtitleType,
                it.subtitleLanguage,
                it.subtitleName,
                it.subtitleTrackId,
                it.addonSubtitleId,
                it.addonSubtitleUrl,
                it.addonSubtitleAddonName,
                it.audioLanguage,
                it.audioName,
                it.audioTrackId,
            ).any { value -> !value.isNullOrBlank() }
        }
    }

    actual fun save(contentId: String, preference: PersistedPlayerTrackPreference) {
        val id = contentId.normalizedStorageId() ?: return
        saveOptionalString(subtitleTypeKey, id, preference.subtitleType)
        saveOptionalString(subtitleLanguageKey, id, preference.subtitleLanguage)
        saveOptionalString(subtitleNameKey, id, preference.subtitleName)
        saveOptionalString(subtitleTrackIdKey, id, preference.subtitleTrackId)
        saveOptionalString(addonSubtitleIdKey, id, preference.addonSubtitleId)
        saveOptionalString(addonSubtitleUrlKey, id, preference.addonSubtitleUrl)
        saveOptionalString(addonSubtitleAddonNameKey, id, preference.addonSubtitleAddonName)
        saveOptionalString(audioLanguageKey, id, preference.audioLanguage)
        saveOptionalString(audioNameKey, id, preference.audioName)
        saveOptionalString(audioTrackIdKey, id, preference.audioTrackId)
    }

    actual fun loadSubtitleDelayMs(videoId: String): Int? {
        val id = videoId.normalizedStorageId() ?: return null
        val defaults = NSUserDefaults.standardUserDefaults
        val key = scopedKey(subtitleDelayMsKey, id)
        return if (defaults.objectForKey(key) != null) {
            defaults.integerForKey(key).toInt()
        } else {
            null
        }
    }

    actual fun saveSubtitleDelayMs(videoId: String, delayMs: Int) {
        val id = videoId.normalizedStorageId() ?: return
        NSUserDefaults.standardUserDefaults.setInteger(
            delayMs.coerceIn(SUBTITLE_DELAY_MIN_MS, SUBTITLE_DELAY_MAX_MS).toLong(),
            forKey = scopedKey(subtitleDelayMsKey, id),
        )
    }

    actual fun loadPayload(): String? {
        val defaults = NSUserDefaults.standardUserDefaults
        val profileSuffix = ProfileScopedKey.of("")
        val payload = buildJsonObject {
            defaults.dictionaryRepresentation().forEach { (rawKey, value) ->
                val key = rawKey as? String ?: return@forEach
                if (!key.endsWith(profileSuffix)) return@forEach
                when (value) {
                    is String -> put(key, JsonPrimitive(value))
                    is NSString -> put(key, JsonPrimitive(value.toString()))
                    is NSNumber -> put(key, JsonPrimitive(value.intValue))
                }
            }
        }
        return payload.takeIf { it.isNotEmpty() }?.let(json::encodeToString)
    }

    actual fun savePayload(payload: String) {
        val defaults = NSUserDefaults.standardUserDefaults
        val profileSuffix = ProfileScopedKey.of("")
        val parsed = runCatching { json.decodeFromString<JsonObject>(payload) }.getOrNull()
        defaults.dictionaryRepresentation().keys
            .mapNotNull { it as? String }
            .filter { it.endsWith(profileSuffix) }
            .forEach(defaults::removeObjectForKey)
        parsed?.forEach { (key, value) ->
            if (!key.endsWith(profileSuffix)) return@forEach
            val primitive = value.jsonPrimitive
            primitive.intOrNull?.let { defaults.setInteger(it.toLong(), forKey = key) }
                ?: primitive.contentOrNull?.let { defaults.setObject(it, forKey = key) }
        }
    }

    private fun loadString(field: String, contentId: String): String? =
        NSUserDefaults.standardUserDefaults
            .stringForKey(scopedKey(field, contentId))
            ?.takeIf { it.isNotBlank() }

    private fun saveOptionalString(field: String, contentId: String, value: String?) {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = scopedKey(field, contentId)
        if (value.isNullOrBlank()) {
            defaults.removeObjectForKey(key)
        } else {
            defaults.setObject(value, forKey = key)
        }
    }

    private fun scopedKey(field: String, contentId: String): String =
        ProfileScopedKey.of("$field|$contentId")

    private fun String.normalizedStorageId(): String? =
        trim().takeIf { it.isNotBlank() }
}
