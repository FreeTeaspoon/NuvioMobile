package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamLaunchStoreTest {

    @Test
    fun storesAndRemovesLaunchesById() {
        val storage = FakeStreamLaunchPayloadStorage()
        StreamLaunchStore.useStorageForTesting(storage)
        val launch = StreamLaunch(
            type = "series",
            videoId = "tt1234567:1:2",
            parentMetaId = "tt1234567",
            parentMetaType = "series",
            title = "Episode",
            seasonNumber = 1,
            episodeNumber = 2,
        )

        val launchId = StreamLaunchStore.put(launch)

        assertEquals(launch, StreamLaunchStore.get(launchId))

        StreamLaunchStore.remove(launchId)

        assertNull(StreamLaunchStore.get(launchId))
        assertNull(storage.loadLaunchPayload(launchId))
        StreamLaunchStore.resetStorageForTesting()
    }

    @Test
    fun restoresLaunchFromStorageAfterMemoryReset() {
        val storage = FakeStreamLaunchPayloadStorage()
        StreamLaunchStore.useStorageForTesting(storage)
        val launch = StreamLaunch(
            type = "movie",
            videoId = "tt7654321",
            title = "Movie",
            poster = "https://example.com/poster.jpg",
            resumePositionMs = 12_000L,
        )

        val launchId = StreamLaunchStore.put(launch)
        StreamLaunchStore.resetMemoryForTesting()

        assertEquals(launch, StreamLaunchStore.get(launchId))
        StreamLaunchStore.resetStorageForTesting()
    }
}

private class FakeStreamLaunchPayloadStorage : StreamLaunchPayloadStorage {
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
