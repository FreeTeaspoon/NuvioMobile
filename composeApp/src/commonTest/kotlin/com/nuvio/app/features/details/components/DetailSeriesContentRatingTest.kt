package com.nuvio.app.features.details.components

import com.nuvio.app.features.details.MetaVideo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetailSeriesContentRatingTest {
    @Test
    fun `metadata rating wins over api fallback`() {
        val video = MetaVideo(
            id = "ep1",
            title = "Episode 1",
            season = 1,
            episode = 1,
            rating = "7.2",
        )

        assertEquals(
            7.2,
            resolveEpisodeDisplayRating(video, mapOf((1 to 1) to 8.4)),
        )
    }

    @Test
    fun `api rating is used when metadata rating is missing`() {
        val video = MetaVideo(
            id = "ep1",
            title = "Episode 1",
            season = 1,
            episode = 1,
        )

        assertEquals(
            8.4,
            resolveEpisodeDisplayRating(video, mapOf((1 to 1) to 8.4)),
        )
    }

    @Test
    fun `invalid metadata rating falls back to api rating`() {
        val video = MetaVideo(
            id = "ep1",
            title = "Episode 1",
            season = 1,
            episode = 1,
            rating = "0",
        )

        assertEquals(
            8.4,
            resolveEpisodeDisplayRating(video, mapOf((1 to 1) to 8.4)),
        )
    }

    @Test
    fun `rating is absent when metadata and api ratings are invalid`() {
        val video = MetaVideo(
            id = "ep1",
            title = "Episode 1",
            season = 1,
            episode = 1,
            rating = "not-a-rating",
        )

        assertNull(resolveEpisodeDisplayRating(video, mapOf((1 to 1) to 0.0)))
    }
}
