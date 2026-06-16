package com.nuvio.app

import com.nuvio.app.features.catalog.CatalogTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CatalogRouteCompatibilityTest {

    @Test
    fun legacyAddonRouteMapsToAddonTarget() {
        val route = CatalogRoute(
            title = "Movies",
            subtitle = "Browse",
            manifestUrl = "https://example.test/manifest.json",
            type = "movie",
            catalogId = "top",
            supportsPagination = true,
            genre = "Action",
        )

        val target = route.toCatalogTarget()

        assertIs<CatalogTarget.Addon>(target)
        assertEquals("https://example.test/manifest.json", target.manifestUrl)
        assertEquals("movie", target.contentType)
        assertEquals("top", target.catalogId)
        assertEquals("Action", target.genre)
        assertEquals(true, target.supportsPagination)
    }

    @Test
    fun legacyLibraryRouteMapsToLibraryTarget() {
        val route = CatalogRoute(
            title = "Library",
            subtitle = "Saved",
            manifestUrl = "nuvio://library",
            type = "series",
            catalogId = "watchlist",
        )

        val target = route.toCatalogTarget()

        assertIs<CatalogTarget.Library>(target)
        assertEquals("series", target.contentType)
        assertEquals("watchlist", target.sectionType)
    }
}
