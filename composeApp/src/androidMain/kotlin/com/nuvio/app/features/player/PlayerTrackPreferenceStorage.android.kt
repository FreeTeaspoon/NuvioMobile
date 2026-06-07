package com.nuvio.app.features.player

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

internal actual object PlayerTrackPreferenceStorage {
    private const val preferencesName = "nuvio_player_track_preferences"
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

    private var preferences: SharedPreferences? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

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
        preferences?.edit()?.apply {
            putOptionalString(subtitleTypeKey, id, preference.subtitleType)
            putOptionalString(subtitleLanguageKey, id, preference.subtitleLanguage)
            putOptionalString(subtitleNameKey, id, preference.subtitleName)
            putOptionalString(subtitleTrackIdKey, id, preference.subtitleTrackId)
            putOptionalString(addonSubtitleIdKey, id, preference.addonSubtitleId)
            putOptionalString(addonSubtitleUrlKey, id, preference.addonSubtitleUrl)
            putOptionalString(addonSubtitleAddonNameKey, id, preference.addonSubtitleAddonName)
            putOptionalString(audioLanguageKey, id, preference.audioLanguage)
            putOptionalString(audioNameKey, id, preference.audioName)
            putOptionalString(audioTrackIdKey, id, preference.audioTrackId)
        }?.apply()
    }

    actual fun loadSubtitleDelayMs(videoId: String): Int? {
        val id = videoId.normalizedStorageId() ?: return null
        val key = scopedKey(subtitleDelayMsKey, id)
        return preferences?.let { prefs ->
            if (prefs.contains(key)) prefs.getInt(key, 0) else null
        }
    }

    actual fun saveSubtitleDelayMs(videoId: String, delayMs: Int) {
        val id = videoId.normalizedStorageId() ?: return
        preferences
            ?.edit()
            ?.putInt(scopedKey(subtitleDelayMsKey, id), delayMs.coerceIn(SUBTITLE_DELAY_MIN_MS, SUBTITLE_DELAY_MAX_MS))
            ?.apply()
    }

    actual fun loadPayload(): String? {
        val prefs = preferences ?: return null
        val profileSuffix = ProfileScopedKey.of("")
        val payload = buildJsonObject {
            prefs.all.forEach { (key, value) ->
                if (!key.endsWith(profileSuffix)) return@forEach
                when (value) {
                    is String -> put(key, JsonPrimitive(value))
                    is Int -> put(key, JsonPrimitive(value))
                }
            }
        }
        return payload.takeIf { it.isNotEmpty() }?.let(json::encodeToString)
    }

    actual fun savePayload(payload: String) {
        val prefs = preferences ?: return
        val profileSuffix = ProfileScopedKey.of("")
        val parsed = runCatching { json.decodeFromString<JsonObject>(payload) }.getOrNull()
        prefs.edit().apply {
            prefs.all.keys
                .filter { it.endsWith(profileSuffix) }
                .forEach(::remove)
            parsed?.forEach { (key, value) ->
                if (!key.endsWith(profileSuffix)) return@forEach
                val primitive = value.jsonPrimitive
                primitive.intOrNull?.let { putInt(key, it) }
                    ?: primitive.contentOrNull?.let { putString(key, it) }
            }
        }.apply()
    }

    private fun loadString(field: String, contentId: String): String? =
        preferences
            ?.getString(scopedKey(field, contentId), null)
            ?.takeIf { it.isNotBlank() }

    private fun SharedPreferences.Editor.putOptionalString(field: String, contentId: String, value: String?) {
        val key = scopedKey(field, contentId)
        if (value.isNullOrBlank()) {
            remove(key)
        } else {
            putString(key, value)
        }
    }

    private fun scopedKey(field: String, contentId: String): String =
        ProfileScopedKey.of("$field|$contentId")

    private fun String.normalizedStorageId(): String? =
        trim().takeIf { it.isNotBlank() }
}
