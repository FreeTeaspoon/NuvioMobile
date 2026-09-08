package com.nuvio.app.features.player

import android.content.Context
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object VideoZoomStorage {
    private var context: Context? = null

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    // Preserve the saved per-title zoom values from earlier fork releases.
    actual fun load(): String? = context?.getSharedPreferences("nuvio_player_settings", Context.MODE_PRIVATE)
        ?.getString(ProfileScopedKey.of("remembered_video_zooms"), null)

    actual fun save(payload: String) {
        context?.getSharedPreferences("nuvio_player_settings", Context.MODE_PRIVATE)?.edit()
            ?.putString(ProfileScopedKey.of("remembered_video_zooms"), payload)?.apply()
    }
}
