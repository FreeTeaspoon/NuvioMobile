package com.nuvio.app.features.downloads

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object DownloadsStorage {
    private val store = DesktopStorage.store("nuvio_downloads")
    private const val autoOpenOnOfflineKey = "downloads_auto_open_on_offline"

    actual fun loadPayload(): String? =
        store.getString(ProfileScopedKey.of("downloads"))

    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of("downloads"), payload)
    }

    actual fun loadAutoOpenOnOffline(): Boolean? =
        store.getBoolean(ProfileScopedKey.of(autoOpenOnOfflineKey))

    actual fun saveAutoOpenOnOffline(enabled: Boolean) {
        store.putBoolean(ProfileScopedKey.of(autoOpenOnOfflineKey), enabled)
    }
}
