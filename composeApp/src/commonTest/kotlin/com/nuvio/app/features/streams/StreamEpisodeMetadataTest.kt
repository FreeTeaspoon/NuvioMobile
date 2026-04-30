package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamEpisodeMetadataTest {
    @Test
    fun `buildEpisodeImdbUrl returns episode imdb title url`() {
        assertEquals(
            "https://www.imdb.com/title/tt1234567/",
            buildEpisodeImdbUrl("tt1234567"),
        )
    }

    @Test
    fun `buildEpisodeImdbUrl rejects composite stremio ids`() {
        assertNull(buildEpisodeImdbUrl("tt11815682:5:3"))
    }

    @Test
    fun `sanitizeEpisodeRating trims redundant decimal places`() {
        assertEquals("8.3", sanitizeEpisodeRating("8.30"))
    }

    @Test
    fun `sanitizeEpisodeRating rejects zero`() {
        assertNull(sanitizeEpisodeRating("0"))
    }
}
