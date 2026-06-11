package com.nuvio.app.features.player

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

actual object PlayerLaunchStorage {
    private const val nextLaunchIdKey = "player_launch_next_launch_id"
    private const val launchIdsKey = "player_launch_ids"
    private val store = DesktopStorage.store("nuvio_player_launches")

    actual fun loadLaunchPayload(launchId: Long): String? =
        store.getString(ProfileScopedKey.of(launchPayloadKey(launchId)))

    actual fun saveLaunchPayload(launchId: Long, payload: String) {
        store.putString(ProfileScopedKey.of(launchPayloadKey(launchId)), payload)
    }

    actual fun removeLaunchPayload(launchId: Long) {
        store.remove(ProfileScopedKey.of(launchPayloadKey(launchId)))
    }

    actual fun loadNextLaunchId(): Long? =
        store.getString(ProfileScopedKey.of(nextLaunchIdKey))?.toLongOrNull()

    actual fun saveNextLaunchId(nextLaunchId: Long) {
        store.putString(ProfileScopedKey.of(nextLaunchIdKey), nextLaunchId.toString())
    }

    actual fun loadLaunchIds(): Set<Long> =
        store.getStringSet(ProfileScopedKey.of(launchIdsKey))
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    actual fun saveLaunchIds(launchIds: Set<Long>) {
        store.putStringSet(ProfileScopedKey.of(launchIdsKey), launchIds.map { it.toString() }.toSet())
    }

    actual fun clear() {
        loadLaunchIds().forEach { launchId -> removeLaunchPayload(launchId) }
        store.remove(ProfileScopedKey.of(nextLaunchIdKey))
        store.remove(ProfileScopedKey.of(launchIdsKey))
    }

    private fun launchPayloadKey(launchId: Long): String = "player_launch_$launchId"
}
