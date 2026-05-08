package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RememberedAudioSelectionTest {

    @Test
    fun contentKeySeparatesShowsAndMoviesByTypeAndId() {
        assertEquals("series|tt123", rememberedAudioContentKey("Series", "tt123"))
        assertEquals("movie|tt123", rememberedAudioContentKey("movie", "tt123"))
        assertNull(rememberedAudioContentKey("", "tt123"))
        assertNull(rememberedAudioContentKey("series", ""))
    }

    @Test
    fun rememberedTrackIdWinsOverOtherMatches() {
        val tracks = listOf(
            audioTrack(index = 0, id = "eng-main", label = "English", language = "eng"),
            audioTrack(index = 1, id = "jpn-main", label = "Japanese", language = "jpn"),
        )
        val selection = RememberedAudioSelection(
            trackId = "jpn-main",
            label = "English",
            language = "eng",
            index = 0,
        )

        assertEquals(1, resolveRememberedAudioTrackIndex(tracks, selection))
    }

    @Test
    fun rememberedLabelAndLanguageSurviveTrackOrderChanges() {
        val tracks = listOf(
            audioTrack(index = 0, id = "track-0", label = "Commentary", language = "eng"),
            audioTrack(index = 1, id = "track-1", label = "English 5.1", language = "eng"),
        )
        val selection = RememberedAudioSelection(
            trackId = "missing",
            label = "  english   5.1 ",
            language = "ENG",
            index = 0,
        )

        assertEquals(1, resolveRememberedAudioTrackIndex(tracks, selection))
    }

    @Test
    fun rememberedIndexIsLastFallback() {
        val tracks = listOf(
            audioTrack(index = 2, id = "new-track", label = "Spanish", language = "spa"),
        )
        val selection = RememberedAudioSelection(
            trackId = "missing",
            label = "English",
            language = "eng",
            index = 2,
        )

        assertEquals(2, resolveRememberedAudioTrackIndex(tracks, selection))
    }

    @Test
    fun missingRememberedTrackAllowsPreferenceFallback() {
        val tracks = listOf(
            audioTrack(index = 0, id = "eng", label = "English", language = "eng"),
            audioTrack(index = 1, id = "jpn", label = "Japanese", language = "jpn"),
        )
        val selection = RememberedAudioSelection(
            trackId = "missing",
            label = "Spanish",
            language = "spa",
            index = 9,
        )

        assertEquals(-1, resolveRememberedAudioTrackIndex(tracks, selection))
        assertEquals(
            1,
            findPreferredTrackIndex(
                tracks = tracks,
                targets = listOf("jpn"),
                language = { it.language },
            ),
        )
    }

    private fun audioTrack(
        index: Int,
        id: String,
        label: String,
        language: String?,
    ): AudioTrack = AudioTrack(
        index = index,
        id = id,
        label = label,
        language = language,
    )
}
