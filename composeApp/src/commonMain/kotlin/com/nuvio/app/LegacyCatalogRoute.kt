package com.nuvio.app

import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.catalog.CatalogTargetKind
import kotlinx.serialization.Serializable

/**
 * Compatibility model for catalog routes saved by the pre-Navigation 3 fork.
 *
 * The live app uses [com.nuvio.app.navigation.CatalogRoute], which stores a
 * launch id and keeps the target in [CatalogLaunchStore]. This adapter remains
 * intentionally isolated so old serialized route payloads and fork tests can
 * still be decoded without bringing the old route model back into navigation.
 */
@Serializable
data class CatalogRoute(
    val title: String,
    val subtitle: String,
    val targetKind: String? = null,
    val contentType: String? = null,
    val supportsPagination: Boolean = false,
    val manifestUrl: String? = null,
    val addonCatalogId: String? = null,
    val genre: String? = null,
    val librarySectionType: String? = null,
    val collectionId: String? = null,
    val folderId: String? = null,
    val sourceKey: String? = null,
    val type: String? = null,
    val catalogId: String? = null,
) {
    constructor(
        title: String,
        subtitle: String,
        target: CatalogTarget,
    ) : this(
        title = title,
        subtitle = subtitle,
        targetKind = when (target) {
            is CatalogTarget.Addon -> CatalogTargetKind.ADDON.name
            is CatalogTarget.Library -> CatalogTargetKind.LIBRARY.name
            is CatalogTarget.CollectionSource -> CatalogTargetKind.COLLECTION_SOURCE.name
        },
        contentType = target.contentType,
        supportsPagination = target.supportsPagination,
        manifestUrl = (target as? CatalogTarget.Addon)?.manifestUrl,
        addonCatalogId = (target as? CatalogTarget.Addon)?.catalogId,
        genre = (target as? CatalogTarget.Addon)?.genre,
        librarySectionType = (target as? CatalogTarget.Library)?.sectionType,
        collectionId = (target as? CatalogTarget.CollectionSource)?.collectionId,
        folderId = (target as? CatalogTarget.CollectionSource)?.folderId,
        sourceKey = (target as? CatalogTarget.CollectionSource)?.sourceKey,
        type = target.contentType,
        catalogId = when (target) {
            is CatalogTarget.Addon -> target.catalogId
            is CatalogTarget.Library -> target.sectionType
            is CatalogTarget.CollectionSource -> null
        },
    )

    fun toCatalogTarget(): CatalogTarget =
        requireNotNull(toCatalogTargetOrNull()) { "Unsupported catalog route" }

    fun toCatalogTargetOrNull(): CatalogTarget? =
        when (resolveTargetKindOrNull() ?: return null) {
            CatalogTargetKind.ADDON -> CatalogTarget.Addon(
                manifestUrl = manifestUrl ?: return null,
                contentType = contentType ?: type ?: "movie",
                catalogId = addonCatalogId ?: catalogId ?: return null,
                genre = genre,
                supportsPagination = supportsPagination,
            )

            CatalogTargetKind.LIBRARY -> CatalogTarget.Library(
                contentType = contentType ?: type ?: "movie",
                sectionType = librarySectionType ?: catalogId ?: return null,
            )

            CatalogTargetKind.COLLECTION_SOURCE -> CatalogTarget.CollectionSource(
                collectionId = collectionId ?: return null,
                folderId = folderId ?: return null,
                sourceKey = sourceKey ?: return null,
                contentType = contentType ?: type ?: "movie",
                supportsPagination = supportsPagination,
            )
        }

    private fun resolveTargetKindOrNull(): CatalogTargetKind? =
        targetKind?.let(CatalogTargetKind::fromRouteValue)
            ?: when {
                collectionId != null || folderId != null || sourceKey != null -> CatalogTargetKind.COLLECTION_SOURCE
                librarySectionType != null -> CatalogTargetKind.LIBRARY
                addonCatalogId != null -> CatalogTargetKind.ADDON
                manifestUrl == LegacyInternalLibraryManifestUrl -> CatalogTargetKind.LIBRARY
                manifestUrl != null && catalogId != null -> CatalogTargetKind.ADDON
                else -> null
            }
}

private const val LegacyInternalLibraryManifestUrl = "nuvio://library"
