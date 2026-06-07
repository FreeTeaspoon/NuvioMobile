package com.nuvio.app.features.backup

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.core.sync.ProfileSettingsSync
import com.nuvio.app.features.home.HomeCatalogSettingsSyncService
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.plugins.StoredPluginsState
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watchprogress.WatchProgressCodec
import com.nuvio.app.features.watching.sync.SupabaseProgressSyncAdapter
import com.nuvio.app.features.watching.sync.SupabaseWatchedSyncAdapter
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

internal object BackupSupabaseRestore {
    private val log = Logger.withTag("BackupSupabaseRestore")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun pushImportedPayload(payload: NuvioBackupPayload, mode: BackupImportMode) {
        val authState = AuthRepository.state.value
        if (authState !is AuthState.Authenticated || authState.isAnonymous) return

        payload.profiles.forEach { profile ->
            pushProfilePayload(profile)
        }
        runRestoreStep("collections") {
            pushCollections(payload)
        }
    }

    private suspend fun runRestoreStep(
        label: String,
        block: suspend () -> Unit,
    ) {
        runCatching {
            block()
        }.onFailure { error ->
            log.e(error) { "Failed to push imported backup $label to Supabase" }
        }
    }

    private suspend fun pushProfilePayload(profile: BackupProfilePayload) {
        runRestoreStep("profile ${profile.profileIndex} addons") {
            pushAddons(profile)
        }
        runRestoreStep("profile ${profile.profileIndex} library") {
            pushLibrary(profile)
        }
        runRestoreStep("profile ${profile.profileIndex} plugins") {
            pushPlugins(profile)
        }
        runRestoreStep("profile ${profile.profileIndex} watch progress") {
            pushWatchProgress(profile)
        }
        runRestoreStep("profile ${profile.profileIndex} watched history") {
            pushWatched(profile)
        }
        runRestoreStep("profile ${profile.profileIndex} settings") {
            pushSettings(profile)
        }
    }

    private suspend fun pushAddons(profile: BackupProfilePayload) {
        val items = profile.addons.urls.distinct().mapIndexed { index, url ->
            BackupAddonPushItem(
                url = url,
                enabled = profile.addons.enabledByUrl[url] ?: true,
                sortOrder = index,
            )
        }
        val params = buildJsonObject {
            put("p_profile_id", profile.profileIndex)
            put("p_addons", json.encodeToJsonElement(items))
        }
        SupabaseProvider.client.postgrest.rpc("sync_push_addons", params)
    }

    private suspend fun pushLibrary(profile: BackupProfilePayload) {
        val storedPayload = decodeOrDefault<BackupStoredLibraryPayload>(profile.libraryPayload)
        val items = storedPayload.items.map { item -> item.toBackupSyncItem() }
        val params = buildJsonObject {
            put("p_profile_id", profile.profileIndex)
            put("p_items", json.encodeToJsonElement(items))
        }
        SupabaseProvider.client.postgrest.rpc("sync_push_library", params)
    }

    private suspend fun pushPlugins(profile: BackupProfilePayload) {
        val storedPayload = decodeOrDefault<StoredPluginsState>(profile.pluginsPayload)
        val items = storedPayload.repositories.mapIndexed { index, repository ->
            BackupPluginPushItem(
                url = repository.manifestUrl,
                name = repository.name,
                enabled = true,
                sortOrder = index,
            )
        }
        val params = buildJsonObject {
            put("p_profile_id", profile.profileIndex)
            put("p_plugins", json.encodeToJsonElement(items))
        }
        SupabaseProvider.client.postgrest.rpc("sync_push_plugins", params)
    }

    private suspend fun pushWatchProgress(profile: BackupProfilePayload) {
        val entries = WatchProgressCodec.decodeEntries(profile.watchProgressPayload)
        if (entries.isNotEmpty()) {
            SupabaseProgressSyncAdapter.push(profile.profileIndex, entries)
        }
    }

    private suspend fun pushWatched(profile: BackupProfilePayload) {
        val storedPayload = decodeOrDefault<BackupStoredWatchedPayload>(profile.watchedPayload)
        if (storedPayload.items.isNotEmpty()) {
            SupabaseWatchedSyncAdapter.push(profile.profileIndex, storedPayload.items)
        }
    }

    private suspend fun pushSettings(profile: BackupProfilePayload) {
        ProfileSettingsSync.pushProfileToRemote(profile.profileIndex)
        HomeCatalogSettingsSyncService.pushProfileToRemote(profile.profileIndex)
    }

    private suspend fun pushCollections(payload: NuvioBackupPayload) {
        val collectionsJson = payload.global.collectionsPayload
        if (collectionsJson.isBlank()) return

        val jsonElement = runCatching {
            json.parseToJsonElement(collectionsJson)
        }.getOrDefault(JsonArray(emptyList()))

        val params = buildJsonObject {
            put("p_profile_id", payload.activeProfileIndex)
            put("p_collections_json", jsonElement)
        }
        SupabaseProvider.client.postgrest.rpc("sync_push_collections", params)
    }

    private inline fun <reified T> decodeOrDefault(payload: String): T =
        runCatching { json.decodeFromString<T>(payload) }
            .getOrElse { json.decodeFromString("{}") }

    @Serializable
    private data class BackupAddonPushItem(
        val url: String,
        val name: String = "",
        val enabled: Boolean = true,
        @SerialName("sort_order") val sortOrder: Int = 0,
    )

    @Serializable
    private data class BackupPluginPushItem(
        val url: String,
        val name: String = "",
        val enabled: Boolean = true,
        @SerialName("sort_order") val sortOrder: Int = 0,
    )

    @Serializable
    private data class BackupStoredLibraryPayload(
        val items: List<LibraryItem> = emptyList(),
    )

    @Serializable
    private data class BackupLibrarySyncItem(
        @SerialName("content_id") val contentId: String,
        @SerialName("content_type") val contentType: String,
        val name: String = "",
        val poster: String? = null,
        @SerialName("poster_shape") val posterShape: String = "POSTER",
        val background: String? = null,
        val description: String? = null,
        @SerialName("release_info") val releaseInfo: String? = null,
        @SerialName("imdb_rating") val imdbRating: Float? = null,
        val genres: List<String> = emptyList(),
        @SerialName("addon_base_url") val addonBaseUrl: String? = null,
        @SerialName("added_at") val addedAt: Long = 0,
    )

    @Serializable
    private data class BackupStoredWatchedPayload(
        val items: List<WatchedItem> = emptyList(),
    )

    private fun LibraryItem.toBackupSyncItem(): BackupLibrarySyncItem =
        BackupLibrarySyncItem(
            contentId = id,
            contentType = type,
            name = name,
            poster = poster,
            posterShape = posterShape.toSyncName(),
            background = banner,
            description = description,
            releaseInfo = releaseInfo,
            imdbRating = imdbRating?.toFloatOrNull(),
            genres = genres,
            addonBaseUrl = addonBaseUrl,
            addedAt = savedAtEpochMs,
        )

    private fun PosterShape.toSyncName(): String =
        when (this) {
            PosterShape.Poster -> "POSTER"
            PosterShape.Square -> "SQUARE"
            PosterShape.Landscape -> "LANDSCAPE"
        }
}
