package com.nuvio.app.features.player

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

actual object PlayerLaunchStorage {
    private const val preferencesName = "nuvio_player_launches"
    private const val nextLaunchIdKey = "next_launch_id"
    private const val launchIdsKey = "launch_ids"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadLaunchPayload(launchId: Long): String? =
        preferences?.getString(ProfileScopedKey.of(launchPayloadKey(launchId)), null)

    actual fun saveLaunchPayload(launchId: Long, payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(launchPayloadKey(launchId)), payload)
            ?.apply()
    }

    actual fun removeLaunchPayload(launchId: Long) {
        preferences
            ?.edit()
            ?.remove(ProfileScopedKey.of(launchPayloadKey(launchId)))
            ?.apply()
    }

    actual fun loadNextLaunchId(): Long? =
        preferences?.let { sharedPreferences ->
            val key = ProfileScopedKey.of(nextLaunchIdKey)
            if (sharedPreferences.contains(key)) sharedPreferences.getLong(key, 1L) else null
        }

    actual fun saveNextLaunchId(nextLaunchId: Long) {
        preferences
            ?.edit()
            ?.putLong(ProfileScopedKey.of(nextLaunchIdKey), nextLaunchId)
            ?.apply()
    }

    actual fun loadLaunchIds(): Set<Long> =
        preferences
            ?.getStringSet(ProfileScopedKey.of(launchIdsKey), emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    actual fun saveLaunchIds(launchIds: Set<Long>) {
        preferences
            ?.edit()
            ?.putStringSet(ProfileScopedKey.of(launchIdsKey), launchIds.map { it.toString() }.toSet())
            ?.apply()
    }

    actual fun clear() {
        preferences?.edit()?.apply {
            loadLaunchIds().forEach { launchId ->
                remove(ProfileScopedKey.of(launchPayloadKey(launchId)))
            }
            remove(ProfileScopedKey.of(nextLaunchIdKey))
            remove(ProfileScopedKey.of(launchIdsKey))
        }?.apply()
    }

    private fun launchPayloadKey(launchId: Long): String = "launch_$launchId"
}
