package com.nuvio.app.features.downloads

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object DownloadsStorage {
    private const val payloadKey = "downloads_payload"
    private const val autoOpenOnOfflineKey = "auto_open_downloads_on_offline"

    actual fun loadPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(payloadKey))

    actual fun savePayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(payloadKey))
    }

    actual fun loadAutoOpenOnOffline(): Boolean? {
        val key = ProfileScopedKey.of(autoOpenOnOfflineKey)
        NSUserDefaults.standardUserDefaults.objectForKey(key) ?: return null
        return NSUserDefaults.standardUserDefaults.boolForKey(key)
    }

    actual fun saveAutoOpenOnOffline(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = ProfileScopedKey.of(autoOpenOnOfflineKey))
    }
}
