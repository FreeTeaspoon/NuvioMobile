package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerLaunchStoreTest {

    @Test
    fun storesAndRemovesLaunchesById() {
        val storage = FakePlayerLaunchPayloadStorage()
        PlayerLaunchStore.useStorageForTesting(storage)
        val launch = PlayerLaunch(
            title = "Title",
            sourceUrl = "https://example.com/video.m3u8?token=a/b:c",
            streamTitle = "Source",
            providerName = "Provider",
            parentMetaId = "tt1234567",
            parentMetaType = "movie",
        )

        val launchId = PlayerLaunchStore.put(launch)

        assertEquals(launch, PlayerLaunchStore.get(launchId))

        PlayerLaunchStore.remove(launchId)

        assertNull(PlayerLaunchStore.get(launchId))
        assertNull(storage.loadLaunchPayload(launchId))
        PlayerLaunchStore.resetStorageForTesting()
    }

    @Test
    fun restoresLaunchFromStorageAfterMemoryReset() {
        val storage = FakePlayerLaunchPayloadStorage()
        PlayerLaunchStore.useStorageForTesting(storage)
        val launch = PlayerLaunch(
            title = "Restored",
            sourceUrl = "https://example.com/restored.m3u8",
            sourceHeaders = mapOf("Referer" to "https://example.com"),
            streamTitle = "Source",
            providerName = "Provider",
            parentMetaId = "tt7654321",
            parentMetaType = "movie",
            initialPositionMs = 42_000L,
        )

        val launchId = PlayerLaunchStore.put(launch)
        PlayerLaunchStore.resetMemoryForTesting()

        assertEquals(launch, PlayerLaunchStore.get(launchId))
        PlayerLaunchStore.resetStorageForTesting()
    }
}

private class FakePlayerLaunchPayloadStorage : PlayerLaunchPayloadStorage {
    private val payloads = mutableMapOf<Long, String>()
    private var nextLaunchId: Long? = null
    private var launchIds = emptySet<Long>()

    override fun loadLaunchPayload(launchId: Long): String? = payloads[launchId]

    override fun saveLaunchPayload(launchId: Long, payload: String) {
        payloads[launchId] = payload
    }

    override fun removeLaunchPayload(launchId: Long) {
        payloads.remove(launchId)
    }

    override fun loadNextLaunchId(): Long? = nextLaunchId

    override fun saveNextLaunchId(nextLaunchId: Long) {
        this.nextLaunchId = nextLaunchId
    }

    override fun loadLaunchIds(): Set<Long> = launchIds

    override fun saveLaunchIds(launchIds: Set<Long>) {
        this.launchIds = launchIds
    }

    override fun clear() {
        payloads.clear()
        nextLaunchId = null
        launchIds = emptySet()
    }
}
