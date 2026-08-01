package com.nuvio.app.features.mdblist

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaExternalRating
import com.nuvio.app.features.details.MetaLink
import com.nuvio.app.features.details.RATING_PROVIDER_LINK_CATEGORY
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MdbListMetadataServiceTest {
    @Test
    fun `rating enrichment keeps successful providers when one provider fails`() = runBlocking {
        val originalRatingPayloadFetcher = MdbListMetadataService.ratingPayloadFetcher
        val originalProviderLinksHtmlFetcher = MdbListMetadataService.providerLinksHtmlFetcher
        val originalTmdbToImdbResolver = MdbListMetadataService.tmdbToImdbResolver
        MdbListMetadataService.clearCache()
        MdbListMetadataService.providerLinksHtmlFetcher = { "" }
        MdbListMetadataService.tmdbToImdbResolver = { _, _ -> null }
        MdbListMetadataService.ratingPayloadFetcher = { url, _ ->
            when {
                "/tmdb?" in url -> error("TMDB request failed")
                "/imdb?" in url -> """{"ratings":[{"rating":8.3}]}"""
                else -> """{"ratings":[]}"""
            }
        }

        try {
            val result = MdbListMetadataService.enrichMeta(
                meta = MetaDetails(
                    id = "tt0840196",
                    type = "series",
                    name = "Skins",
                ),
                fallbackItemId = "tt0840196",
                settings = MdbListSettings(
                    enabled = true,
                    apiKey = "test-key",
                    useImdb = true,
                    useTmdb = true,
                    useTomatoes = false,
                    useMetacritic = false,
                    useTrakt = false,
                    useLetterboxd = false,
                    useAudience = false,
                    useMal = false,
                ),
            )

            assertEquals(
                listOf(MetaExternalRating(source = MdbListMetadataService.PROVIDER_IMDB, value = 8.3)),
                result.externalRatings,
            )
        } finally {
            MdbListMetadataService.ratingPayloadFetcher = originalRatingPayloadFetcher
            MdbListMetadataService.providerLinksHtmlFetcher = originalProviderLinksHtmlFetcher
            MdbListMetadataService.tmdbToImdbResolver = originalTmdbToImdbResolver
            MdbListMetadataService.clearCache()
        }
    }

    @Test
    fun `rating enrichment resolves tmdb fallback id to imdb id`() = runBlocking {
        val originalRatingPayloadFetcher = MdbListMetadataService.ratingPayloadFetcher
        val originalProviderLinksHtmlFetcher = MdbListMetadataService.providerLinksHtmlFetcher
        val originalTmdbToImdbResolver = MdbListMetadataService.tmdbToImdbResolver
        val requestedTmdbLookups = mutableListOf<Pair<Int, String>>()
        val ratingRequestBodies = mutableListOf<String>()
        MdbListMetadataService.clearCache()
        MdbListMetadataService.providerLinksHtmlFetcher = { "" }
        MdbListMetadataService.tmdbToImdbResolver = { tmdbId, mediaType ->
            requestedTmdbLookups += tmdbId to mediaType
            if (tmdbId == 900 && mediaType == "tv") "tt0840196" else null
        }
        MdbListMetadataService.ratingPayloadFetcher = { _, body ->
            ratingRequestBodies += body
            """{"ratings":[{"rating":8.3}]}"""
        }

        try {
            val result = MdbListMetadataService.enrichMeta(
                meta = MetaDetails(
                    id = "tmdb:900",
                    type = "series",
                    name = "Skins",
                ),
                fallbackItemId = "tmdb:900",
                settings = mdbListSettings(useImdb = true),
            )

            assertEquals(listOf(900 to "tv"), requestedTmdbLookups)
            assertTrue(ratingRequestBodies.single().contains("tt0840196"))
            assertEquals(
                listOf(MetaExternalRating(source = MdbListMetadataService.PROVIDER_IMDB, value = 8.3)),
                result.externalRatings,
            )
        } finally {
            MdbListMetadataService.ratingPayloadFetcher = originalRatingPayloadFetcher
            MdbListMetadataService.providerLinksHtmlFetcher = originalProviderLinksHtmlFetcher
            MdbListMetadataService.tmdbToImdbResolver = originalTmdbToImdbResolver
            MdbListMetadataService.clearCache()
        }
    }

    @Test
    fun `rating enrichment resolves tmdb provider link to imdb id`() = runBlocking {
        val originalRatingPayloadFetcher = MdbListMetadataService.ratingPayloadFetcher
        val originalProviderLinksHtmlFetcher = MdbListMetadataService.providerLinksHtmlFetcher
        val originalTmdbToImdbResolver = MdbListMetadataService.tmdbToImdbResolver
        val requestedTmdbLookups = mutableListOf<Pair<Int, String>>()
        MdbListMetadataService.clearCache()
        MdbListMetadataService.providerLinksHtmlFetcher = { "" }
        MdbListMetadataService.tmdbToImdbResolver = { tmdbId, mediaType ->
            requestedTmdbLookups += tmdbId to mediaType
            if (tmdbId == 900 && mediaType == "tv") "tt0840196" else null
        }
        MdbListMetadataService.ratingPayloadFetcher = { _, _ -> """{"ratings":[{"rating":8.3}]}""" }

        try {
            val result = MdbListMetadataService.enrichMeta(
                meta = MetaDetails(
                    id = "series:skins",
                    type = "series",
                    name = "Skins",
                    links = listOf(
                        MetaLink(
                            name = "TMDB",
                            category = "metadata",
                            url = "https://www.themoviedb.org/tv/900-skins",
                        ),
                    ),
                ),
                fallbackItemId = "series:skins",
                settings = mdbListSettings(useImdb = true),
            )

            assertEquals(listOf(900 to "tv"), requestedTmdbLookups)
            assertEquals(
                listOf(MetaExternalRating(source = MdbListMetadataService.PROVIDER_IMDB, value = 8.3)),
                result.externalRatings,
            )
        } finally {
            MdbListMetadataService.ratingPayloadFetcher = originalRatingPayloadFetcher
            MdbListMetadataService.providerLinksHtmlFetcher = originalProviderLinksHtmlFetcher
            MdbListMetadataService.tmdbToImdbResolver = originalTmdbToImdbResolver
            MdbListMetadataService.clearCache()
        }
    }

    @Test
    fun `rating enrichment returns base meta when ids cannot resolve to imdb`() = runBlocking {
        val originalRatingPayloadFetcher = MdbListMetadataService.ratingPayloadFetcher
        val originalProviderLinksHtmlFetcher = MdbListMetadataService.providerLinksHtmlFetcher
        val originalTmdbToImdbResolver = MdbListMetadataService.tmdbToImdbResolver
        var ratingRequests = 0
        MdbListMetadataService.clearCache()
        MdbListMetadataService.providerLinksHtmlFetcher = { "" }
        MdbListMetadataService.tmdbToImdbResolver = { _, _ -> null }
        MdbListMetadataService.ratingPayloadFetcher = { _, _ ->
            ratingRequests += 1
            """{"ratings":[{"rating":8.3}]}"""
        }

        try {
            val result = MdbListMetadataService.enrichMeta(
                meta = MetaDetails(
                    id = "series:skins",
                    type = "series",
                    name = "Skins",
                ),
                fallbackItemId = "series:skins",
                settings = mdbListSettings(useImdb = true),
            )

            assertEquals(0, ratingRequests)
            assertTrue(result.externalRatings.isEmpty())
        } finally {
            MdbListMetadataService.ratingPayloadFetcher = originalRatingPayloadFetcher
            MdbListMetadataService.providerLinksHtmlFetcher = originalProviderLinksHtmlFetcher
            MdbListMetadataService.tmdbToImdbResolver = originalTmdbToImdbResolver
            MdbListMetadataService.clearCache()
        }
    }

    @Test
    fun `show page html extracts trusted provider links`() {
        val links = MdbListMetadataService.extractRatingProviderLinksFromHtml(
            """
            <html>
              <body>
                <a href="https://www.imdb.com/title/tt0840196">IMDb</a>
                <a href="https://trakt.tv/shows/skins">Trakt</a>
                <a href="https://www.themoviedb.org/tv/900">TMDB</a>
                <a href="https://www.rottentomatoes.com/tv/skins">Rotten Tomatoes</a>
                <a href="https://www.metacritic.com/tv/the-xac">Metacritic</a>
              </body>
            </html>
            """.trimIndent(),
        )

        assertEquals("https://www.imdb.com/title/tt0840196", links[MdbListMetadataService.PROVIDER_IMDB])
        assertEquals("https://trakt.tv/shows/skins", links[MdbListMetadataService.PROVIDER_TRAKT])
        assertEquals("https://www.themoviedb.org/tv/900", links[MdbListMetadataService.PROVIDER_TMDB])
        assertEquals(
            "https://www.rottentomatoes.com/tv/skins",
            links[MdbListMetadataService.PROVIDER_TOMATOES],
        )
        assertEquals(
            "https://www.rottentomatoes.com/tv/skins",
            links[MdbListMetadataService.PROVIDER_AUDIENCE],
        )
        assertEquals("https://www.metacritic.com/tv/the-xac", links[MdbListMetadataService.PROVIDER_METACRITIC])
    }

    @Test
    fun `movie page html extracts letterboxd link`() {
        val links = MdbListMetadataService.extractRatingProviderLinksFromHtml(
            """
            <a href="https://letterboxd.com/imdb/tt0120382">Letterboxd</a>
            <a href="https://www.metacritic.com/movie/the-truman-show">Metacritic</a>
            """.trimIndent(),
        )

        assertEquals("https://letterboxd.com/imdb/tt0120382", links[MdbListMetadataService.PROVIDER_LETTERBOXD])
        assertEquals(
            "https://www.metacritic.com/movie/the-truman-show",
            links[MdbListMetadataService.PROVIDER_METACRITIC],
        )
    }

    @Test
    fun `untrusted and non-direct links are ignored`() {
        val links = MdbListMetadataService.extractRatingProviderLinksFromHtml(
            """
            <a href="https://example.com/movie/skins">Example</a>
            <a href="/tv/skins">Relative</a>
            <a href="https://letterboxd.com/search/skins">Letterboxd search</a>
            <a href="https://www.rottentomatoes.com/search?search=skins">Rotten Tomatoes search</a>
            """.trimIndent(),
        )

        assertTrue(links.isEmpty())
    }

    @Test
    fun `missing provider anchors produce partial links without failing`() {
        val links = MdbListMetadataService.extractRatingProviderLinksFromHtml(
            """<a href="https://www.rottentomatoes.com/m/truman_show">Rotten Tomatoes</a>""",
        )

        assertEquals(2, links.size)
        assertEquals(
            "https://www.rottentomatoes.com/m/truman_show",
            links[MdbListMetadataService.PROVIDER_TOMATOES],
        )
        assertEquals(
            "https://www.rottentomatoes.com/m/truman_show",
            links[MdbListMetadataService.PROVIDER_AUDIENCE],
        )
    }

    @Test
    fun `empty extracted provider links preserve existing rating provider links`() {
        val existingLinks = listOf(
            ratingProviderLink(MdbListMetadataService.PROVIDER_TMDB, "https://www.themoviedb.org/tv/900"),
            MetaLink(
                name = "Homepage",
                category = "metadata",
                url = "https://example.com/skins",
            ),
        )

        assertEquals(
            existingLinks,
            MdbListMetadataService.mergeRatingProviderLinks(
                existingLinks = existingLinks,
                providerLinks = emptyMap(),
            ),
        )
    }

    @Test
    fun `new provider links replace only matching generated providers`() {
        val existingTmdbLink = ratingProviderLink(
            MdbListMetadataService.PROVIDER_TMDB,
            "https://www.themoviedb.org/tv/900",
        )
        val existingTraktLink = ratingProviderLink(
            MdbListMetadataService.PROVIDER_TRAKT,
            "https://trakt.tv/shows/skins",
        )

        val links = MdbListMetadataService.mergeRatingProviderLinks(
            existingLinks = listOf(existingTmdbLink, existingTraktLink),
            providerLinks = mapOf(
                MdbListMetadataService.PROVIDER_TMDB to "https://www.themoviedb.org/tv/901",
            ),
        )

        assertEquals(existingTraktLink, links[0])
        assertEquals(
            ratingProviderLink(MdbListMetadataService.PROVIDER_TMDB, "https://www.themoviedb.org/tv/901"),
            links[1],
        )
    }

    private fun ratingProviderLink(
        provider: String,
        url: String,
    ): MetaLink = MetaLink(
        name = provider,
        category = RATING_PROVIDER_LINK_CATEGORY,
        url = url,
    )

    private fun mdbListSettings(
        useImdb: Boolean = false,
        useTmdb: Boolean = false,
        useTomatoes: Boolean = false,
        useMetacritic: Boolean = false,
        useTrakt: Boolean = false,
        useLetterboxd: Boolean = false,
        useAudience: Boolean = false,
        useMal: Boolean = false,
    ): MdbListSettings = MdbListSettings(
        enabled = true,
        apiKey = "test-key",
        useImdb = useImdb,
        useTmdb = useTmdb,
        useTomatoes = useTomatoes,
        useMetacritic = useMetacritic,
        useTrakt = useTrakt,
            useLetterboxd = useLetterboxd,
            useAudience = useAudience,
            useMal = useMal,
        )
}
