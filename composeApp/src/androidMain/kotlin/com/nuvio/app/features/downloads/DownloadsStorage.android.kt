package com.nuvio.app.features.downloads

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object DownloadsStorage {
    private const val preferencesName = "nuvio_downloads"
    private const val payloadKey = "downloads_payload"
    private const val autoOpenOnOfflineKey = "auto_open_downloads_on_offline"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(payloadKey), null)

    actual fun savePayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(payloadKey), payload)
            ?.apply()
    }

    actual fun loadAutoOpenOnOffline(): Boolean? {
        val key = ProfileScopedKey.of(autoOpenOnOfflineKey)
        val prefs = preferences ?: return null
        return if (prefs.contains(key)) prefs.getBoolean(key, true) else null
    }

    actual fun saveAutoOpenOnOffline(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(autoOpenOnOfflineKey), enabled)
            ?.apply()
    }
}
