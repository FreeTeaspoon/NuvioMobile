package com.nuvio.app.features.streams

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

actual object StreamLaunchStorage {
    private const val nextLaunchIdKey = "stream_launch_next_launch_id"
    private const val launchIdsKey = "stream_launch_ids"

    actual fun loadLaunchPayload(launchId: Long): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(launchPayloadKey(launchId)))

    actual fun saveLaunchPayload(launchId: Long, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(launchPayloadKey(launchId)))
    }

    actual fun removeLaunchPayload(launchId: Long) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(launchPayloadKey(launchId)))
    }

    actual fun loadNextLaunchId(): Long? {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = ProfileScopedKey.of(nextLaunchIdKey)
        return if (defaults.objectForKey(key) != null) defaults.integerForKey(key) else null
    }

    actual fun saveNextLaunchId(nextLaunchId: Long) {
        NSUserDefaults.standardUserDefaults.setInteger(nextLaunchId, forKey = ProfileScopedKey.of(nextLaunchIdKey))
    }

    @Suppress("UNCHECKED_CAST")
    actual fun loadLaunchIds(): Set<Long> =
        (NSUserDefaults.standardUserDefaults.arrayForKey(ProfileScopedKey.of(launchIdsKey)) as? List<String>)
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    actual fun saveLaunchIds(launchIds: Set<Long>) {
        NSUserDefaults.standardUserDefaults.setObject(
            launchIds.map { it.toString() },
            forKey = ProfileScopedKey.of(launchIdsKey),
        )
    }

    actual fun clear() {
        val defaults = NSUserDefaults.standardUserDefaults
        loadLaunchIds().forEach { launchId ->
            defaults.removeObjectForKey(ProfileScopedKey.of(launchPayloadKey(launchId)))
        }
        defaults.removeObjectForKey(ProfileScopedKey.of(nextLaunchIdKey))
        defaults.removeObjectForKey(ProfileScopedKey.of(launchIdsKey))
    }

    private fun launchPayloadKey(launchId: Long): String = "stream_launch_$launchId"
}
