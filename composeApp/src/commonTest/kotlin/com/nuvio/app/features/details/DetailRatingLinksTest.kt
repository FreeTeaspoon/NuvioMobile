package com.nuvio.app.features.details

import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_AUDIENCE
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_IMDB
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_LETTERBOXD
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_METACRITIC
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TMDB
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TOMATOES
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TRAKT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetailRatingLinksTest {
    @Test
    fun `imdb source opens direct title page when imdb id exists`() {
        val meta = meta(id = "tt0840196")

        assertEquals(
            "https://www.imdb.com/title/tt0840196/",
            buildRatingProviderUrl(meta, PROVIDER_IMDB),
        )
    }

    @Test
    fun `parents guide opens direct page when imdb id exists`() {
        val meta = meta(id = "tt0840196")

        assertEquals(
            "https://www.imdb.com/title/tt0840196/parentalguide/",
            buildImdbParentsGuideUrl(meta),
        )
    }

    @Test
    fun `parents guide extracts imdb id from metadata links`() {
        val meta = meta(
            id = "tmdb:123",
            links = listOf(
                MetaLink(
                    name = "IMDb",
                    category = "metadata",
                    url = "https://www.imdb.com/title/tt0840196/",
                ),
            ),
        )

        assertEquals(
            "https://www.imdb.com/title/tt0840196/parentalguide/",
            buildImdbParentsGuideUrl(meta),
        )
    }

    @Test
    fun `parents guide returns null without imdb id`() {
        val meta = meta(id = "tmdb:123", name = "Example")

        assertNull(buildImdbParentsGuideUrl(meta))
    }

    @Test
    fun `imdb source falls back to title and year search without imdb id`() {
        val meta = meta(id = "series:123", name = "Skins", releaseInfo = "2007-01-25")

        assertEquals(
            "https://www.imdb.com/find/?q=Skins%202007",
            buildRatingProviderUrl(meta, PROVIDER_IMDB),
        )
    }

    @Test
    fun `tmdb movie source opens direct movie page when tmdb id exists`() {
        val meta = meta(id = "tmdb:123", type = "movie")

        assertEquals(
            "https://www.themoviedb.org/movie/123",
            buildRatingProviderUrl(meta, PROVIDER_TMDB),
        )
    }

    @Test
    fun `tmdb series source opens direct tv page when tmdb id exists`() {
        val meta = meta(id = "tmdb:456", type = "series")

        assertEquals(
            "https://www.themoviedb.org/tv/456",
            buildRatingProviderUrl(meta, PROVIDER_TMDB),
        )
    }

    @Test
    fun `tmdb source falls back to search without tmdb id`() {
        val meta = meta(id = "mal:62516", type = "series", name = "Skins", releaseInfo = "2007")

        assertEquals(
            "https://www.themoviedb.org/search?query=Skins%202007",
            buildRatingProviderUrl(meta, PROVIDER_TMDB),
        )
    }

    @Test
    fun `rotten tomatoes source opens rotten tomatoes search`() {
        val meta = meta(name = "Skins", releaseInfo = "2007-01-25")

        assertEquals(
            "https://www.rottentomatoes.com/search?search=Skins%202007",
            buildRatingProviderUrl(meta, PROVIDER_TOMATOES),
        )
    }

    @Test
    fun `audience score source opens rotten tomatoes search`() {
        val meta = meta(name = "Skins", releaseInfo = "2007-01-25")

        assertEquals(
            "https://www.rottentomatoes.com/search?search=Skins%202007",
            buildRatingProviderUrl(meta, PROVIDER_AUDIENCE),
        )
    }

    @Test
    fun `metacritic source opens metacritic search path`() {
        val meta = meta(name = "Skins", releaseInfo = "2007-01-25")

        assertEquals(
            "https://www.metacritic.com/search/Skins%202007/",
            buildRatingProviderUrl(meta, PROVIDER_METACRITIC),
        )
    }

    @Test
    fun `trakt source opens trakt search`() {
        val meta = meta(name = "Skins", releaseInfo = "2007-01-25")

        assertEquals(
            "https://trakt.tv/search?query=Skins%202007",
            buildRatingProviderUrl(meta, PROVIDER_TRAKT),
        )
    }

    @Test
    fun `letterboxd source opens full text search`() {
        val meta = meta(name = "Skins", releaseInfo = "2007-01-25")

        assertEquals(
            "https://letterboxd.com/search/full-text/Skins%202007/",
            buildRatingProviderUrl(meta, PROVIDER_LETTERBOXD),
        )
    }

    @Test
    fun `direct rating provider links win over search fallbacks`() {
        val meta = meta(
            name = "Skins",
            releaseInfo = "2007-01-25",
            links = listOf(
                ratingProviderLink(PROVIDER_TMDB, "https://www.themoviedb.org/tv/900"),
                ratingProviderLink(PROVIDER_TOMATOES, "https://www.rottentomatoes.com/tv/skins"),
                ratingProviderLink(PROVIDER_AUDIENCE, "https://www.rottentomatoes.com/tv/skins"),
                ratingProviderLink(PROVIDER_METACRITIC, "https://www.metacritic.com/tv/the-xac"),
                ratingProviderLink(PROVIDER_TRAKT, "https://trakt.tv/shows/skins"),
                ratingProviderLink(PROVIDER_LETTERBOXD, "https://letterboxd.com/imdb/tt0840196"),
            ),
        )

        assertEquals(
            "https://www.themoviedb.org/tv/900",
            buildRatingProviderUrl(meta, PROVIDER_TMDB),
        )
        assertEquals(
            "https://www.rottentomatoes.com/tv/skins",
            buildRatingProviderUrl(meta, PROVIDER_TOMATOES),
        )
        assertEquals(
            "https://www.rottentomatoes.com/tv/skins",
            buildRatingProviderUrl(meta, PROVIDER_AUDIENCE),
        )
        assertEquals(
            "https://www.metacritic.com/tv/the-xac",
            buildRatingProviderUrl(meta, PROVIDER_METACRITIC),
        )
        assertEquals(
            "https://trakt.tv/shows/skins",
            buildRatingProviderUrl(meta, PROVIDER_TRAKT),
        )
        assertEquals(
            "https://letterboxd.com/imdb/tt0840196",
            buildRatingProviderUrl(meta, PROVIDER_LETTERBOXD),
        )
    }

    @Test
    fun `audience source can reuse rotten tomatoes provider link`() {
        val meta = meta(
            links = listOf(
                ratingProviderLink(PROVIDER_TOMATOES, "https://www.rottentomatoes.com/tv/skins"),
            ),
        )

        assertEquals(
            "https://www.rottentomatoes.com/tv/skins",
            buildRatingProviderUrl(meta, PROVIDER_AUDIENCE),
        )
    }

    @Test
    fun `unknown source has no link`() {
        assertNull(buildRatingProviderUrl(meta(), "unknown"))
    }

    @Test
    fun `generic metadata provider links open directly`() {
        val meta = meta(
            id = "addon:skins",
            links = listOf(
                MetaLink(
                    name = "TMDB",
                    category = "metadata",
                    url = "https://www.themoviedb.org/tv/9001-skins",
                ),
                MetaLink(
                    name = "IMDb",
                    category = "metadata",
                    url = "https://www.imdb.com/title/tt0840196/",
                ),
            ),
        )

        assertEquals(
            "https://www.themoviedb.org/tv/9001-skins",
            buildRatingProviderUrl(meta, PROVIDER_TMDB),
        )
        assertEquals(
            "https://www.imdb.com/title/tt0840196/",
            buildRatingProviderUrl(meta, PROVIDER_IMDB),
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

    private fun meta(
        id: String = "show",
        type: String = "series",
        name: String = "Example",
        releaseInfo: String? = null,
        links: List<MetaLink> = emptyList(),
    ): MetaDetails = MetaDetails(
        id = id,
        type = type,
        name = name,
        releaseInfo = releaseInfo,
        links = links,
    )
}
