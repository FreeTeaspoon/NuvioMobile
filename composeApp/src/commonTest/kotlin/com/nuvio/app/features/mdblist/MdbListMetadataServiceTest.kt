package com.nuvio.app.features.mdblist

import com.nuvio.app.features.details.MetaLink
import com.nuvio.app.features.details.RATING_PROVIDER_LINK_CATEGORY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MdbListMetadataServiceTest {
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
}
