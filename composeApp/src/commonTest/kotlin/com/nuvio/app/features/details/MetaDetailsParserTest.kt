package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MetaDetailsParserTest {

    @Test
    fun `parse rejects null meta object without json object cast crash`() {
        assertFailsWith<IllegalStateException> {
            MetaDetailsParser.parse("""{"meta":null}""")
        }
    }

    @Test
    fun `parse accepts bare meta object response`() {
        val result = MetaDetailsParser.parse(
            """
            {
              "id": "mal:62516",
              "type": "series",
              "name": "The Fragrant Flower Blooms with Dignity"
            }
            """.trimIndent(),
        )

        assertEquals("mal:62516", result.id)
        assertEquals("series", result.type)
        assertEquals("The Fragrant Flower Blooms with Dignity", result.name)
    }

    @Test
    fun `parse reads video imdbRating into episode rating`() {
        val result = parseVideo("""{"id":"ep1","title":"Episode 1","imdbRating":"8.30"}""")

        assertEquals("8.3", result.rating)
    }

    @Test
    fun `parse reads video rating into episode rating`() {
        val result = parseVideo("""{"id":"ep1","title":"Episode 1","rating":"7.8"}""")

        assertEquals("7.8", result.rating)
    }

    @Test
    fun `parse filters zero video rating`() {
        val result = parseVideo("""{"id":"ep1","title":"Episode 1","rating":"0"}""")

        assertNull(result.rating)
    }

    @Test
    fun `parse reads episode imdb id aliases`() {
        assertEquals(
            "tt1111111",
            parseVideo("""{"id":"ep1","title":"Episode 1","imdbId":"tt1111111"}""").imdbId,
        )
        assertEquals(
            "tt2222222",
            parseVideo("""{"id":"ep1","title":"Episode 1","imdb_id":"tt2222222"}""").imdbId,
        )
        assertEquals(
            "tt3333333",
            parseVideo("""{"id":"ep1","title":"Episode 1","imdb":"tt3333333"}""").imdbId,
        )
    }

    @Test
    fun `parse uses video id as episode imdb id only for exact title id`() {
        assertEquals(
            "tt4444444",
            parseVideo("""{"id":"tt4444444","title":"Episode 1"}""").imdbId,
        )
        assertNull(parseVideo("""{"id":"tt4444444:5:3","title":"Episode 1"}""").imdbId)
        assertNull(parseVideo("""{"id":"prefix-tt4444444","title":"Episode 1"}""").imdbId)
    }

    private fun parseVideo(videoJson: String): MetaVideo =
        MetaDetailsParser.parse(
            """
            {
              "meta": {
                "id": "tt9999999",
                "type": "series",
                "name": "Show",
                "videos": [$videoJson]
              }
            }
            """.trimIndent(),
        ).videos.single()
}
