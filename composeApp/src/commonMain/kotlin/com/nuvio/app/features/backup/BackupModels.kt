package com.nuvio.app.features.backup

import com.nuvio.app.features.profiles.NuvioProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

internal const val BACKUP_FORMAT = "nuvio.backup"
internal const val BACKUP_VERSION = 1
internal const val BACKUP_SCHEMA_VERSION = 1
internal const val BACKUP_EXTENSION = ".nuvio-backup"

@Serializable
data class NuvioBackupEnvelope(
    val format: String = BACKUP_FORMAT,
    val version: Int = BACKUP_VERSION,
    val encryption: BackupEncryptionMetadata,
    val createdAtEpochMs: Long,
    val appVersionName: String,
    val appVersionCode: Int,
    val payloadCiphertextBase64: String,
)

@Serializable
data class BackupEncryptionMetadata(
    val algorithm: String = "AES-256-CBC-HMAC-SHA256",
    val kdf: String = "PBKDF2-HMAC-SHA256",
    val iterations: Int = 210_000,
    val saltBase64: String,
    val ivBase64: String,
    val hmacBase64: String,
)

@Serializable
data class NuvioBackupPayload(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val exportedAtEpochMs: Long,
    val activeProfileIndex: Int,
    val profiles: List<BackupProfilePayload>,
    val global: BackupGlobalPayload = BackupGlobalPayload(),
)

@Serializable
data class BackupGlobalPayload(
    val collectionsPayload: String = "",
)

@Serializable
data class BackupProfilePayload(
    val profileIndex: Int,
    val profile: BackupProfileMetadata? = null,
    val addons: BackupAddonsPayload = BackupAddonsPayload(),
    val pluginsPayload: String = "",
    val libraryPayload: String = "",
    val watchProgressPayload: String = "",
    val watchedPayload: String = "",
    val searchHistoryPayload: String = "",
    val settings: BackupSettingsPayload = BackupSettingsPayload(),
)

@Serializable
data class BackupProfileMetadata(
    val name: String = "",
    val avatarColorHex: String = "#1E88E5",
    val avatarId: String? = null,
    val avatarUrl: String? = null,
    val usesPrimaryAddons: Boolean = false,
    val usesPrimaryPlugins: Boolean = false,
    val pinEnabled: Boolean = false,
) {
    companion object {
        fun fromProfile(profile: NuvioProfile): BackupProfileMetadata =
            BackupProfileMetadata(
                name = profile.name,
                avatarColorHex = profile.avatarColorHex,
                avatarId = profile.avatarId,
                avatarUrl = profile.avatarUrl,
                usesPrimaryAddons = profile.usesPrimaryAddons,
                usesPrimaryPlugins = profile.usesPrimaryPlugins,
                pinEnabled = profile.pinEnabled,
            )
    }
}

@Serializable
data class BackupAddonsPayload(
    val urls: List<String> = emptyList(),
    val enabledByUrl: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class BackupSettingsPayload(
    @SerialName("theme_settings") val themeSettings: JsonObject = JsonObject(emptyMap()),
    @SerialName("poster_card_style_payload") val posterCardStylePayload: String = "",
    @SerialName("player_settings") val playerSettings: JsonObject = JsonObject(emptyMap()),
    @SerialName("stream_badge_settings") val streamBadgeSettings: JsonObject = JsonObject(emptyMap()),
    @SerialName("debrid_settings") val debridSettings: JsonObject = JsonObject(emptyMap()),
    @SerialName("tmdb_settings") val tmdbSettings: JsonObject = JsonObject(emptyMap()),
    @SerialName("mdblist_settings") val mdbListSettings: JsonObject = JsonObject(emptyMap()),
    @SerialName("meta_screen_settings_payload") val metaScreenSettingsPayload: String = "",
    @SerialName("season_view_mode") val seasonViewMode: String? = null,
    @SerialName("home_catalog_settings_payload") val homeCatalogSettingsPayload: String = "",
    @SerialName("collection_mobile_settings_payload") val collectionMobileSettingsPayload: String = "",
    @SerialName("continue_watching_preferences_payload") val continueWatchingPreferencesPayload: String = "",
    @SerialName("player_track_preferences_payload") val playerTrackPreferencesPayload: String = "",
    @SerialName("resume_was_in_player") val resumeWasInPlayer: Boolean? = null,
    @SerialName("resume_last_player_video_id") val resumeLastPlayerVideoId: String? = null,
    @SerialName("trakt_auth_payload") val traktAuthPayload: String = "",
    @SerialName("trakt_library_payload") val traktLibraryPayload: String = "",
    @SerialName("trakt_settings_payload") val traktSettingsPayload: String = "",
    @SerialName("trakt_comments_settings") val traktCommentsSettings: JsonObject = JsonObject(emptyMap()),
    @SerialName("episode_release_notifications_payload") val episodeReleaseNotificationsPayload: String = "",
    @SerialName("p2p_settings") val p2pSettings: BackupP2pSettingsPayload = BackupP2pSettingsPayload(),
)

@Serializable
data class BackupP2pSettingsPayload(
    val p2pEnabled: Boolean? = null,
    val enableUpload: Boolean? = null,
    val hideTorrentStats: Boolean? = null,
)

enum class BackupImportMode {
    Merge,
    Replace,
}

sealed interface BackupExportResult {
    data class Success(val defaultFileName: String, val bytes: ByteArray) : BackupExportResult
    data class Error(val message: String) : BackupExportResult
}

sealed interface BackupImportResult {
    data class Success(val summary: BackupImportSummary) : BackupImportResult
    data class NeedsConfirmation(val preview: BackupImportSummary, val bytes: ByteArray, val passphrase: String) : BackupImportResult
    data class Error(val message: String) : BackupImportResult
}

@Serializable
data class BackupImportSummary(
    val profileCount: Int = 0,
    val addonCount: Int = 0,
    val libraryItemCount: Int = 0,
    val watchProgressItemCount: Int = 0,
)

sealed interface BackupFileResult {
    data object Success : BackupFileResult
    data object Cancelled : BackupFileResult
    data class Error(val message: String) : BackupFileResult
}

sealed interface BackupFileReadResult {
    data class Success(val bytes: ByteArray) : BackupFileReadResult
    data object Cancelled : BackupFileReadResult
    data class Error(val message: String) : BackupFileReadResult
}
