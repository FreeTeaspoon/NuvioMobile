package com.nuvio.app.features.backup

import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.core.ui.PosterCardStyleStorage
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.AddonStorage
import com.nuvio.app.features.collection.CollectionMobileSettingsRepository
import com.nuvio.app.features.collection.CollectionMobileSettingsStorage
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.collection.CollectionStorage
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.debrid.DebridSettingsStorage
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.details.MetaScreenSettingsStorage
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.downloads.DownloadsStorage
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSettingsStorage
import com.nuvio.app.features.library.LibraryStorage
import com.nuvio.app.features.mdblist.MdbListMetadataService
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.mdblist.MdbListSettingsStorage
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsStorage
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.features.p2p.P2pSettingsStorage
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsStorage
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.settings.ThemeSettingsStorage
import com.nuvio.app.features.streams.StreamBadgeSettingsRepository
import com.nuvio.app.features.streams.StreamBadgeSettingsStorage
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.TmdbSettingsStorage
import com.nuvio.app.features.trakt.TraktAuthRepository
import com.nuvio.app.features.trakt.TraktAuthStorage
import com.nuvio.app.features.trakt.TraktCommentsSettings
import com.nuvio.app.features.trakt.TraktCommentsStorage
import com.nuvio.app.features.trakt.TraktLibraryStorage
import com.nuvio.app.features.trakt.TraktSettingsRepository
import com.nuvio.app.features.trakt.TraktSettingsStorage
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watched.WatchedStorage
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesStorage
import com.nuvio.app.features.watchprogress.ResumePromptRepository
import com.nuvio.app.features.watchprogress.ResumePromptStorage
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.WatchProgressStorage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object BackupRepository {
    private const val saltBytes = 16
    private const val ivBytes = 16
    private const val derivedKeyBytes = 64
    private const val secretKeyBytes = 32
    private const val iterations = 210_000

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun exportEncrypted(passphrase: String): BackupExportResult =
        runCatching {
            require(passphrase.isNotBlank()) { "Backup password is required." }
            ensureLoaded()
            val payload = buildPayload()
            val plaintext = json.encodeToString(payload).encodeToByteArray()
            val salt = BackupCrypto.randomBytes(saltBytes)
            val iv = BackupCrypto.randomBytes(ivBytes)
            val keyMaterial = BackupCrypto.deriveKey(passphrase, salt, iterations, derivedKeyBytes)
            val encryptionKey = keyMaterial.copyOfRange(0, secretKeyBytes)
            val hmacKey = keyMaterial.copyOfRange(secretKeyBytes, derivedKeyBytes)
            val ciphertext = BackupCrypto.encryptAesCbcPkcs7(plaintext, encryptionKey, iv)
            val hmac = BackupCrypto.hmacSha256(hmacKey, salt + iv + ciphertext)
            val envelope = NuvioBackupEnvelope(
                encryption = BackupEncryptionMetadata(
                    iterations = iterations,
                    saltBase64 = salt.base64(),
                    ivBase64 = iv.base64(),
                    hmacBase64 = hmac.base64(),
                ),
                createdAtEpochMs = currentTimeMillis(),
                appVersionName = AppVersionConfig.VERSION_NAME,
                appVersionCode = AppVersionConfig.VERSION_CODE,
                payloadCiphertextBase64 = ciphertext.base64(),
            )
            BackupExportResult.Success(
                defaultFileName = "nuvio-backup-${currentDateForFileName()}$BACKUP_EXTENSION",
                bytes = json.encodeToString(envelope).encodeToByteArray(),
            )
        }.getOrElse { error ->
            BackupExportResult.Error(error.message ?: "Backup export failed.")
        }

    suspend fun previewImport(bytes: ByteArray, passphrase: String): BackupImportResult =
        runCatching {
            val payload = decryptPayload(bytes, passphrase)
            BackupImportResult.NeedsConfirmation(
                preview = payload.summary(),
                bytes = bytes,
                passphrase = passphrase,
            )
        }.getOrElse { error ->
            BackupImportResult.Error(error.backupErrorMessage())
        }

    suspend fun importEncrypted(
        bytes: ByteArray,
        passphrase: String,
        mode: BackupImportMode,
    ): BackupImportResult =
        runCatching {
            val payload = decryptPayload(bytes, passphrase)
            applyPayload(payload, mode)
            BackupImportResult.Success(payload.summary())
        }.getOrElse { error ->
            BackupImportResult.Error(error.backupErrorMessage())
        }

    private fun buildPayload(): NuvioBackupPayload {
        val profileId = ProfileRepository.activeProfileId
        val profile = ProfileRepository.state.value.activeProfile
            ?.takeIf { it.profileIndex == profileId }

        return NuvioBackupPayload(
            exportedAtEpochMs = currentTimeMillis(),
            activeProfileIndex = profileId,
            profiles = listOf(profilePayload(profileId, profile)),
            global = BackupGlobalPayload(
                collectionsPayload = CollectionStorage.loadPayload().orEmpty(),
            ),
        )
    }

    private fun profilePayload(profileId: Int, profile: NuvioProfile?): BackupProfilePayload =
        ProfileScopedKey.scopedTo(profileId) {
            BackupProfilePayload(
                profileIndex = profileId,
                profile = profile?.let(BackupProfileMetadata::fromProfile),
                addons = BackupAddonsPayload(
                    urls = AddonStorage.loadInstalledAddonUrls(profileId),
                    enabledByUrl = AddonStorage.loadAddonEnabledStates(profileId),
                ),
                libraryPayload = LibraryStorage.loadPayload(profileId).orEmpty(),
                watchProgressPayload = WatchProgressStorage.loadPayload(profileId).orEmpty(),
                watchedPayload = WatchedStorage.loadPayload(profileId).orEmpty(),
                settings = BackupSettingsPayload(
                    themeSettings = ThemeSettingsStorage.exportToSyncPayload(),
                    posterCardStylePayload = PosterCardStyleStorage.loadPayload().orEmpty(),
                    playerSettings = PlayerSettingsStorage.exportToSyncPayload(),
                    streamBadgeSettings = StreamBadgeSettingsStorage.exportToSyncPayload(),
                    debridSettings = DebridSettingsStorage.exportToSyncPayload(),
                    tmdbSettings = TmdbSettingsStorage.exportToSyncPayload(),
                    mdbListSettings = MdbListSettingsStorage.exportToSyncPayload(),
                    metaScreenSettingsPayload = MetaScreenSettingsStorage.loadPayload().orEmpty(),
                    homeCatalogSettingsPayload = HomeCatalogSettingsStorage.loadPayload().orEmpty(),
                    collectionMobileSettingsPayload = CollectionMobileSettingsStorage.loadPayload().orEmpty(),
                    continueWatchingPreferencesPayload = ContinueWatchingPreferencesStorage.loadPayload().orEmpty(),
                    resumeWasInPlayer = ResumePromptStorage.loadWasInPlayer(),
                    resumeLastPlayerVideoId = ResumePromptStorage.loadLastPlayerVideoId(),
                    traktAuthPayload = TraktAuthStorage.loadPayload().orEmpty(),
                    traktLibraryPayload = TraktLibraryStorage.loadPayload().orEmpty(),
                    traktSettingsPayload = TraktSettingsStorage.loadPayload().orEmpty(),
                    traktCommentsSettings = TraktCommentsStorage.exportToSyncPayload(),
                    episodeReleaseNotificationsPayload = EpisodeReleaseNotificationsStorage.loadPayload().orEmpty(),
                    downloadsAutoOpenOnOffline = DownloadsStorage.loadAutoOpenOnOffline(),
                    p2pSettings = BackupP2pSettingsPayload(
                        p2pEnabled = P2pSettingsStorage.loadP2pEnabled(),
                        enableUpload = P2pSettingsStorage.loadEnableUpload(),
                        hideTorrentStats = P2pSettingsStorage.loadHideTorrentStats(),
                    ),
                ),
            )
    }

    private suspend fun applyPayload(payload: NuvioBackupPayload, mode: BackupImportMode) {
        val activeProfileIndex = ProfileRepository.activeProfileId
        val profile = payload.profileForCurrentImport(activeProfileIndex)
        payload.global.collectionsPayload.takeIf(String::isNotBlank)?.let(CollectionStorage::savePayload)

        ProfileScopedKey.scopedTo(activeProfileIndex) {
            AddonStorage.saveInstalledAddonUrls(activeProfileIndex, profile.addons.urls)
            AddonStorage.saveAddonEnabledStates(activeProfileIndex, profile.addons.enabledByUrl)
            LibraryStorage.savePayload(activeProfileIndex, profile.libraryPayload)
            WatchProgressStorage.savePayload(activeProfileIndex, profile.watchProgressPayload)
            WatchedStorage.savePayload(activeProfileIndex, profile.watchedPayload)
            applySettings(profile.settings)
        }

        val currentProfilePayload = payload.copy(
            activeProfileIndex = activeProfileIndex,
            profiles = listOf(profile),
        )
        BackupSupabaseRestore.pushImportedPayload(currentProfilePayload, mode)
        reinitializeAfterImport(activeProfileIndex)
    }

    private fun NuvioBackupPayload.profileForCurrentImport(activeProfileIndex: Int): BackupProfilePayload =
        (profiles.firstOrNull { it.profileIndex == activeProfileIndex }
            ?: profiles.firstOrNull()
            ?: BackupProfilePayload(profileIndex = activeProfileIndex))
            .copy(
                profileIndex = activeProfileIndex,
                profile = null,
            )

    private fun applySettings(settings: BackupSettingsPayload) {
        ThemeSettingsStorage.replaceFromSyncPayload(settings.themeSettings)
        PosterCardStyleStorage.savePayload(settings.posterCardStylePayload)
        PlayerSettingsStorage.replaceFromSyncPayload(settings.playerSettings)
        StreamBadgeSettingsStorage.replaceFromSyncPayload(settings.streamBadgeSettings)
        DebridSettingsStorage.replaceFromSyncPayload(settings.debridSettings)
        TmdbSettingsStorage.replaceFromSyncPayload(settings.tmdbSettings)
        MdbListSettingsStorage.replaceFromSyncPayload(settings.mdbListSettings)
        MetaScreenSettingsStorage.savePayload(settings.metaScreenSettingsPayload)
        HomeCatalogSettingsStorage.savePayload(settings.homeCatalogSettingsPayload)
        CollectionMobileSettingsStorage.savePayload(settings.collectionMobileSettingsPayload)
        ContinueWatchingPreferencesStorage.savePayload(settings.continueWatchingPreferencesPayload)
        settings.resumeWasInPlayer?.let(ResumePromptStorage::saveWasInPlayer)
        ResumePromptStorage.saveLastPlayerVideoId(settings.resumeLastPlayerVideoId)
        TraktAuthStorage.savePayload(settings.traktAuthPayload)
        TraktLibraryStorage.savePayload(settings.traktLibraryPayload)
        TraktSettingsStorage.savePayload(settings.traktSettingsPayload)
        TraktCommentsStorage.replaceFromSyncPayload(settings.traktCommentsSettings)
        EpisodeReleaseNotificationsStorage.savePayload(settings.episodeReleaseNotificationsPayload)
        settings.downloadsAutoOpenOnOffline?.let(DownloadsStorage::saveAutoOpenOnOffline)
        settings.p2pSettings.p2pEnabled?.let(P2pSettingsStorage::saveP2pEnabled)
        settings.p2pSettings.enableUpload?.let(P2pSettingsStorage::saveEnableUpload)
        settings.p2pSettings.hideTorrentStats?.let(P2pSettingsStorage::saveHideTorrentStats)
    }

    private fun reinitializeAfterImport(activeProfileIndex: Int) {
        ProfileRepository.clearInMemory()
        ProfileRepository.loadCachedProfiles()
        ProfileRepository.selectProfile(activeProfileIndex)
        AddonRepository.initialize()
        AddonRepository.refreshAll()
        CollectionRepository.initialize()
        ThemeSettingsRepository.onProfileChanged()
        PosterCardStyleRepository.onProfileChanged()
        PlayerSettingsRepository.onProfileChanged()
        StreamBadgeSettingsRepository.onProfileChanged()
        DebridSettingsRepository.onProfileChanged()
        TmdbSettingsRepository.onProfileChanged()
        MdbListMetadataService.clearCache()
        MdbListSettingsRepository.onProfileChanged()
        MetaScreenSettingsRepository.onProfileChanged()
        HomeCatalogSettingsRepository.onProfileChanged()
        CollectionMobileSettingsRepository.onProfileChanged()
        ContinueWatchingPreferencesRepository.onProfileChanged()
        TraktAuthRepository.onProfileChanged()
        TraktSettingsRepository.onProfileChanged()
        TraktCommentsSettings.onProfileChanged()
        EpisodeReleaseNotificationsRepository.onProfileChanged()
        DownloadsRepository.onProfileChanged()
        P2pSettingsRepository.onProfileChanged()
        WatchProgressRepository.onProfileChanged(activeProfileIndex)
        WatchedRepository.onProfileChanged(activeProfileIndex)
    }

    private fun ensureLoaded() {
        ProfileRepository.loadCachedProfiles()
        ThemeSettingsRepository.ensureLoaded()
        PosterCardStyleRepository.ensureLoaded()
        PlayerSettingsRepository.ensureLoaded()
        StreamBadgeSettingsRepository.ensureLoaded()
        DebridSettingsRepository.ensureLoaded()
        TmdbSettingsRepository.ensureLoaded()
        MdbListSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.ensureLoaded()
        HomeCatalogSettingsRepository.snapshot()
        CollectionMobileSettingsRepository.ensureLoaded()
        ContinueWatchingPreferencesRepository.ensureLoaded()
        TraktAuthRepository.ensureLoaded()
        TraktSettingsRepository.ensureLoaded()
        TraktCommentsSettings.ensureLoaded()
        EpisodeReleaseNotificationsRepository.ensureLoaded()
        DownloadsRepository.ensureLoaded()
        P2pSettingsRepository.ensureLoaded()
    }

    private fun decryptPayload(bytes: ByteArray, passphrase: String): NuvioBackupPayload {
        require(bytes.isNotEmpty()) { "Backup file is empty." }
        require(passphrase.isNotBlank()) { "Backup password is required." }
        val envelope = runCatching {
            json.decodeFromString<NuvioBackupEnvelope>(bytes.decodeToString())
        }.getOrElse {
            throw BackupInvalidFileException()
        }
        require(envelope.format == BACKUP_FORMAT) { throw BackupInvalidFileException() }
        require(envelope.version <= BACKUP_VERSION) { throw BackupInvalidFileException() }

        val salt = envelope.encryption.saltBase64.fromBase64()
        val iv = envelope.encryption.ivBase64.fromBase64()
        val expectedHmac = envelope.encryption.hmacBase64.fromBase64()
        val ciphertext = envelope.payloadCiphertextBase64.fromBase64()
        val keyMaterial = BackupCrypto.deriveKey(
            passphrase = passphrase,
            salt = salt,
            iterations = envelope.encryption.iterations,
            outputBytes = derivedKeyBytes,
        )
        val encryptionKey = keyMaterial.copyOfRange(0, secretKeyBytes)
        val hmacKey = keyMaterial.copyOfRange(secretKeyBytes, derivedKeyBytes)
        val actualHmac = BackupCrypto.hmacSha256(hmacKey, salt + iv + ciphertext)
        if (!constantTimeEquals(expectedHmac, actualHmac)) {
            throw BackupWrongPasswordException()
        }
        val plaintext = runCatching {
            BackupCrypto.decryptAesCbcPkcs7(ciphertext, encryptionKey, iv)
        }.getOrElse {
            throw BackupWrongPasswordException()
        }
        return json.decodeFromString(plaintext.decodeToString())
    }

    private fun NuvioBackupPayload.summary(): BackupImportSummary =
        BackupImportSummary(
            profileCount = profiles.size,
            addonCount = profiles.sumOf { it.addons.urls.size },
            libraryItemCount = profiles.sumOf { countStoredItems(it.libraryPayload) },
            watchProgressItemCount = profiles.sumOf { countStoredEntries(it.watchProgressPayload) },
        )

    private fun countStoredItems(payload: String): Int =
        countListProperty(payload, "items")

    private fun countStoredEntries(payload: String): Int =
        countListProperty(payload, "entries")

    private fun countListProperty(payload: String, property: String): Int =
        runCatching {
            val parsed = json.decodeFromString<CountPayload>(payload)
            when (property) {
                "items" -> parsed.items.size
                "entries" -> parsed.entries.size
                else -> 0
            }
        }.getOrDefault(0)

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }

    private fun Throwable.backupErrorMessage(): String =
        when (this) {
            is BackupWrongPasswordException -> "Wrong password or corrupted backup."
            is BackupInvalidFileException -> "Invalid backup file."
            else -> message ?: "Backup operation failed."
        }

    @Serializable
    private data class CountPayload(
        val items: List<JsonElement> = emptyList(),
        val entries: List<JsonElement> = emptyList(),
    )

    private class BackupWrongPasswordException : IllegalArgumentException()
    private class BackupInvalidFileException : IllegalArgumentException()
}

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.base64(): String = Base64.encode(this)

@OptIn(ExperimentalEncodingApi::class)
private fun String.fromBase64(): ByteArray = Base64.decode(this)

internal expect fun currentTimeMillis(): Long

internal expect fun currentDateForFileName(): String
