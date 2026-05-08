package com.nuvio.app.features.streams

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StreamLaunch(
    val type: String,
    val videoId: String,
    val parentMetaId: String? = null,
    val parentMetaType: String? = null,
    val title: String,
    val logo: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
    val pauseDescription: String? = null,
    val resumePositionMs: Long? = null,
    val resumeProgressFraction: Float? = null,
    val manualSelection: Boolean = false,
    val startFromBeginning: Boolean = false,
)

internal expect object StreamLaunchStorage {
    fun loadLaunchPayload(launchId: Long): String?
    fun saveLaunchPayload(launchId: Long, payload: String)
    fun removeLaunchPayload(launchId: Long)
    fun loadNextLaunchId(): Long?
    fun saveNextLaunchId(nextLaunchId: Long)
    fun loadLaunchIds(): Set<Long>
    fun saveLaunchIds(launchIds: Set<Long>)
    fun clear()
}

internal interface StreamLaunchPayloadStorage {
    fun loadLaunchPayload(launchId: Long): String?
    fun saveLaunchPayload(launchId: Long, payload: String)
    fun removeLaunchPayload(launchId: Long)
    fun loadNextLaunchId(): Long?
    fun saveNextLaunchId(nextLaunchId: Long)
    fun loadLaunchIds(): Set<Long>
    fun saveLaunchIds(launchIds: Set<Long>)
    fun clear()
}

private object PlatformStreamLaunchPayloadStorage : StreamLaunchPayloadStorage {
    override fun loadLaunchPayload(launchId: Long): String? =
        StreamLaunchStorage.loadLaunchPayload(launchId)

    override fun saveLaunchPayload(launchId: Long, payload: String) {
        StreamLaunchStorage.saveLaunchPayload(launchId, payload)
    }

    override fun removeLaunchPayload(launchId: Long) {
        StreamLaunchStorage.removeLaunchPayload(launchId)
    }

    override fun loadNextLaunchId(): Long? =
        StreamLaunchStorage.loadNextLaunchId()

    override fun saveNextLaunchId(nextLaunchId: Long) {
        StreamLaunchStorage.saveNextLaunchId(nextLaunchId)
    }

    override fun loadLaunchIds(): Set<Long> =
        StreamLaunchStorage.loadLaunchIds()

    override fun saveLaunchIds(launchIds: Set<Long>) {
        StreamLaunchStorage.saveLaunchIds(launchIds)
    }

    override fun clear() {
        StreamLaunchStorage.clear()
    }
}

object StreamLaunchStore {
    private val json = Json { ignoreUnknownKeys = true }
    private var storage: StreamLaunchPayloadStorage = PlatformStreamLaunchPayloadStorage
    private var nextLaunchId = storage.loadNextLaunchId() ?: 1L
    private val launches = mutableMapOf<Long, StreamLaunch>()

    fun put(launch: StreamLaunch): Long {
        val launchId = nextLaunchId++
        launches[launchId] = launch
        storage.saveLaunchPayload(launchId, json.encodeToString(StreamLaunch.serializer(), launch))
        storage.saveNextLaunchId(nextLaunchId)
        storage.saveLaunchIds(storage.loadLaunchIds() + launchId)
        return launchId
    }

    fun get(launchId: Long): StreamLaunch? {
        launches[launchId]?.let { return it }
        val payload = storage.loadLaunchPayload(launchId) ?: return null
        return runCatching {
            json.decodeFromString(StreamLaunch.serializer(), payload)
        }.getOrNull()
            ?.also { launches[launchId] = it }
            ?: run {
                storage.removeLaunchPayload(launchId)
                storage.saveLaunchIds(storage.loadLaunchIds() - launchId)
                null
            }
    }

    fun remove(launchId: Long) {
        launches.remove(launchId)
        storage.removeLaunchPayload(launchId)
        storage.saveLaunchIds(storage.loadLaunchIds() - launchId)
    }

    fun clear() {
        nextLaunchId = 1L
        launches.clear()
        storage.clear()
    }

    internal fun resetMemoryForTesting() {
        launches.clear()
        nextLaunchId = storage.loadNextLaunchId() ?: 1L
    }

    internal fun useStorageForTesting(testStorage: StreamLaunchPayloadStorage) {
        storage = testStorage
        nextLaunchId = storage.loadNextLaunchId() ?: 1L
        launches.clear()
    }

    internal fun resetStorageForTesting() {
        storage = PlatformStreamLaunchPayloadStorage
        nextLaunchId = storage.loadNextLaunchId() ?: 1L
        launches.clear()
    }
}
