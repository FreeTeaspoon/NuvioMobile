package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_ios_hardware_decoder_off
import nuvio.composeapp.generated.resources.player_ios_preset_compatibility_desc
import nuvio.composeapp.generated.resources.player_ios_preset_compatibility_label
import nuvio.composeapp.generated.resources.player_ios_preset_custom_desc
import nuvio.composeapp.generated.resources.player_ios_preset_custom_label
import nuvio.composeapp.generated.resources.player_ios_preset_native_edr_desc
import nuvio.composeapp.generated.resources.player_ios_preset_native_edr_label
import nuvio.composeapp.generated.resources.player_ios_preset_sdr_tone_mapped_desc
import nuvio.composeapp.generated.resources.player_ios_preset_sdr_tone_mapped_label
import org.jetbrains.compose.resources.stringResource

@Serializable
data class PlayerRoute(
    val launchId: Long,
)

@Serializable
data class PlayerLaunch(
    val profileId: Int = 1,
    val title: String,
    val sourceUrl: String,
    val sourceAudioUrl: String? = null,
    val sourceHeaders: Map<String, String> = emptyMap(),
    val sourceResponseHeaders: Map<String, String> = emptyMap(),
    val externalSubtitles: List<com.nuvio.app.features.streams.StreamSubtitle> = emptyList(),
    val streamType: String? = null,
    val sourceFilename: String? = null,
    val sourceVideoSize: Long? = null,
    val logo: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
    val streamTitle: String,
    val streamSubtitle: String? = null,
    val bingeGroup: String? = null,
    val pauseDescription: String? = null,
    val providerName: String,
    val providerAddonId: String? = null,
    val contentType: String? = null,
    val videoId: String? = null,
    val parentMetaId: String,
    val parentMetaType: String,
    val torrentInfoHash: String? = null,
    val torrentFileIdx: Int? = null,
    val torrentFilename: String? = null,
    val torrentMagnetUri: String? = null,
    val torrentTrackers: List<String> = emptyList(),
    val initialPositionMs: Long = 0L,
    val initialProgressFraction: Float? = null,
    val contentLanguage: String? = null,
)

internal expect object PlayerLaunchStorage {
    fun loadLaunchPayload(launchId: Long): String?
    fun saveLaunchPayload(launchId: Long, payload: String)
    fun removeLaunchPayload(launchId: Long)
    fun loadNextLaunchId(): Long?
    fun saveNextLaunchId(nextLaunchId: Long)
    fun loadLaunchIds(): Set<Long>
    fun saveLaunchIds(launchIds: Set<Long>)
    fun clear()
}

internal interface PlayerLaunchPayloadStorage {
    fun loadLaunchPayload(launchId: Long): String?
    fun saveLaunchPayload(launchId: Long, payload: String)
    fun removeLaunchPayload(launchId: Long)
    fun loadNextLaunchId(): Long?
    fun saveNextLaunchId(nextLaunchId: Long)
    fun loadLaunchIds(): Set<Long>
    fun saveLaunchIds(launchIds: Set<Long>)
    fun clear()
}

private object PlatformPlayerLaunchPayloadStorage : PlayerLaunchPayloadStorage {
    override fun loadLaunchPayload(launchId: Long): String? =
        PlayerLaunchStorage.loadLaunchPayload(launchId)

    override fun saveLaunchPayload(launchId: Long, payload: String) {
        PlayerLaunchStorage.saveLaunchPayload(launchId, payload)
    }

    override fun removeLaunchPayload(launchId: Long) {
        PlayerLaunchStorage.removeLaunchPayload(launchId)
    }

    override fun loadNextLaunchId(): Long? =
        PlayerLaunchStorage.loadNextLaunchId()

    override fun saveNextLaunchId(nextLaunchId: Long) {
        PlayerLaunchStorage.saveNextLaunchId(nextLaunchId)
    }

    override fun loadLaunchIds(): Set<Long> =
        PlayerLaunchStorage.loadLaunchIds()

    override fun saveLaunchIds(launchIds: Set<Long>) {
        PlayerLaunchStorage.saveLaunchIds(launchIds)
    }

    override fun clear() {
        PlayerLaunchStorage.clear()
    }
}

object PlayerLaunchStore {
    private val json = Json { ignoreUnknownKeys = true }
    private var storage: PlayerLaunchPayloadStorage = PlatformPlayerLaunchPayloadStorage
    private var nextLaunchId = storage.loadNextLaunchId() ?: 1L
    private val launches = mutableMapOf<Long, PlayerLaunch>()

    fun put(launch: PlayerLaunch): Long {
        val launchId = nextLaunchId++
        launches[launchId] = launch
        storage.saveLaunchPayload(launchId, json.encodeToString(PlayerLaunch.serializer(), launch))
        storage.saveNextLaunchId(nextLaunchId)
        storage.saveLaunchIds(storage.loadLaunchIds() + launchId)
        return launchId
    }

    fun get(launchId: Long): PlayerLaunch? {
        launches[launchId]?.let { return it }
        val payload = storage.loadLaunchPayload(launchId) ?: return null
        return runCatching {
            json.decodeFromString(PlayerLaunch.serializer(), payload)
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

    internal fun useStorageForTesting(testStorage: PlayerLaunchPayloadStorage) {
        storage = testStorage
        nextLaunchId = storage.loadNextLaunchId() ?: 1L
        launches.clear()
    }

    internal fun resetStorageForTesting() {
        storage = PlatformPlayerLaunchPayloadStorage
        nextLaunchId = storage.loadNextLaunchId() ?: 1L
        launches.clear()
    }
}

enum class PlayerResizeMode {
    Fit,
    Fill,
    Zoom,
}

enum class AndroidLibmpvVideoOutput(
    val mpvValue: String,
    val label: String,
    val description: String,
) {
    GpuNext(
        mpvValue = "gpu-next",
        label = "GPU next",
        description = "Modern libmpv renderer with higher quality processing.",
    ),
    Gpu(
        mpvValue = "gpu",
        label = "GPU",
        description = "Compatibility renderer for devices that have issues with GPU next.",
    ),
}

enum class IosVideoOutputPreset(
    val label: String,
    val description: String,
) {
    NativeEdr(
        label = "Native EDR",
        description = "Best for HDR-capable iPhones and iPads.",
    ),
    SdrToneMapped(
        label = "SDR tone mapped",
        description = "More predictable whites and blacks on SDR-style output.",
    ),
    Compatibility(
        label = "Compatibility",
        description = "Closest to the older iOS MPV behavior.",
    ),
    Custom(
        label = "Custom",
        description = "Use your advanced values below.",
    ),
}

enum class IosToneMappingMode(
    val mpvValue: String,
    val label: String,
) {
    Auto("auto", "Auto"),
    Bt2390("bt.2390", "BT.2390"),
    Mobius("mobius", "Mobius"),
    Reinhard("reinhard", "Reinhard"),
    Hable("hable", "Hable"),
    Gamma("gamma", "Gamma"),
    Clip("clip", "Clip"),
}

enum class IosTargetPrimaries(
    val mpvValue: String,
    val label: String,
) {
    Auto("auto", "Auto"),
    Bt709("bt.709", "BT.709"),
    DisplayP3("display-p3", "Display P3"),
    Bt2020("bt.2020", "BT.2020"),
}

enum class IosTargetTransfer(
    val mpvValue: String,
    val label: String,
) {
    Auto("auto", "Auto"),
    Srgb("srgb", "sRGB"),
    Bt1886("bt.1886", "BT.1886"),
    Gamma22("gamma2.2", "Gamma 2.2"),
    Gamma24("gamma2.4", "Gamma 2.4"),
    Pq("pq", "PQ"),
    Hlg("hlg", "HLG"),
}

enum class IosHardwareDecoderMode(
    val mpvValue: String,
    val label: String,
) {
    Auto("auto", "Auto"),
    VideoToolbox("videotoolbox", "VideoToolbox"),
    Off("no", "Off"),
}

data class PlayerVideoZoomState(
    val zoom: Float = 0f,
) {
    fun normalized(): PlayerVideoZoomState =
        copy(zoom = zoom.coerceIn(PlayerVideoZoomMin, PlayerVideoZoomMax))
}

internal const val PlayerVideoZoomMin = -2f
internal const val PlayerVideoZoomMax = 2f
internal const val PlayerVideoZoomStep = 0.05f

enum class PlayerEngineType {
    MEDIA3,
    MPV,
}

enum class IosAudioOutputMode(
    val mpvValue: String,
    val label: String,
) {
    Auto("audiounit", "Auto"),
    AvFoundation("avfoundation", "AVFoundation"),
    AudioUnit("audiounit", "AudioUnit");

    companion object {
        val selectableEntries: List<IosAudioOutputMode> = listOf(Auto, AudioUnit)

        fun fromStoredName(name: String?): IosAudioOutputMode =
            name
                ?.let { runCatching { valueOf(it) }.getOrNull() }
                ?.takeUnless { it == AvFoundation }
                ?: Auto
    }
}

@Composable
fun IosVideoOutputPreset.localizedLabel(): String = when (this) {
    IosVideoOutputPreset.NativeEdr -> stringResource(Res.string.player_ios_preset_native_edr_label)
    IosVideoOutputPreset.SdrToneMapped -> stringResource(Res.string.player_ios_preset_sdr_tone_mapped_label)
    IosVideoOutputPreset.Compatibility -> stringResource(Res.string.player_ios_preset_compatibility_label)
    IosVideoOutputPreset.Custom -> stringResource(Res.string.player_ios_preset_custom_label)
}

@Composable
fun IosVideoOutputPreset.localizedDescription(): String = when (this) {
    IosVideoOutputPreset.NativeEdr -> stringResource(Res.string.player_ios_preset_native_edr_desc)
    IosVideoOutputPreset.SdrToneMapped -> stringResource(Res.string.player_ios_preset_sdr_tone_mapped_desc)
    IosVideoOutputPreset.Compatibility -> stringResource(Res.string.player_ios_preset_compatibility_desc)
    IosVideoOutputPreset.Custom -> stringResource(Res.string.player_ios_preset_custom_desc)
}

@Composable
fun IosHardwareDecoderMode.localizedLabel(): String = when (this) {
    IosHardwareDecoderMode.Off -> stringResource(Res.string.player_ios_hardware_decoder_off)
    else -> label
}

data class PlayerPlaybackSnapshot(
    val isLoading: Boolean = true,
    val isPlaying: Boolean = false,
    val isEnded: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
)
