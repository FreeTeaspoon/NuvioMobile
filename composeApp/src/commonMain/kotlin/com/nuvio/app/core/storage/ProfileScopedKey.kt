package com.nuvio.app.core.storage

import com.nuvio.app.features.profiles.ProfileRepository


object ProfileScopedKey {
    private var overrideProfileId: Int? = null

    fun of(baseKey: String): String = "${baseKey}_${overrideProfileId ?: ProfileRepository.activeProfileId}"

    internal fun <T> scopedTo(profileId: Int, block: () -> T): T {
        val previous = overrideProfileId
        overrideProfileId = profileId
        return try {
            block()
        } finally {
            overrideProfileId = previous
        }
    }
}
