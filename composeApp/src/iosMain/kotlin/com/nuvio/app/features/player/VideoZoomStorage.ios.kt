package com.nuvio.app.features.player

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object VideoZoomStorage {
    actual fun load(): String? = NSUserDefaults.standardUserDefaults
        .stringForKey(ProfileScopedKey.of("remembered_video_zooms"))

    actual fun save(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of("remembered_video_zooms"))
    }
}
