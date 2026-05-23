package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RememberedSubtitleSelectionTest {

    @Test
    fun contentKeysPreferEpisodeSpecificBeforeTitleFallback() {
        assertEquals(
            listOf("series|tt123|video|tt123:1:2", "series|tt123"),
            rememberedSubtitleContentKeys(
                parentMetaType = "Series",
                parentMetaId = "tt123",
                seasonNumber = 1,
                episodeNumber = 2,
                videoId = "tt123:1:2",
            ),
        )
    }

    @Test
    fun contentKeysUseSeasonEpisodeWhenVideoIdIsMissing() {
        assertEquals(
            listOf("series|tt123|s1|e2", "series|tt123"),
            rememberedSubtitleContentKeys(
                parentMetaType = "Series",
                parentMetaId = "tt123",
                seasonNumber = 1,
                episodeNumber = 2,
                videoId = null,
            ),
        )
    }

    @Test
    fun contentKeysFallbackToTitleForMovies() {
        assertEquals(
            listOf(null, "movie|tt123"),
            rememberedSubtitleContentKeys(
                parentMetaType = "movie",
                parentMetaId = "tt123",
                seasonNumber = null,
                episodeNumber = null,
                videoId = null,
            ),
        )
    }

    @Test
    fun builtInTrackIdWinsOverLabelAndLanguage() {
        val tracks = listOf(
            subtitleTrack(index = 0, id = "eng-main", label = "English", language = "eng"),
            subtitleTrack(index = 1, id = "spa-main", label = "Spanish", language = "spa"),
        )
        val selection = RememberedSubtitleSelection(
            kind = RememberedSubtitleKind.BuiltIn,
            trackId = "spa-main",
            label = "English",
            language = "eng",
            index = 0,
        )

        assertEquals(1, resolveRememberedSubtitleTrackIndex(tracks, selection))
    }

    @Test
    fun builtInLabelAndLanguageSurviveTrackOrderChanges() {
        val tracks = listOf(
            subtitleTrack(index = 0, id = "track-0", label = "English SDH", language = "eng"),
            subtitleTrack(index = 2, id = "track-2", label = "English Forced", language = "eng"),
        )
        val selection = RememberedSubtitleSelection(
            kind = RememberedSubtitleKind.BuiltIn,
            trackId = "missing",
            label = " english   forced ",
            language = "ENG",
            index = 0,
        )

        assertEquals(2, resolveRememberedSubtitleTrackIndex(tracks, selection))
    }

    @Test
    fun addonSelectionResolvesByIdOrUrlFirst() {
        val addons = listOf(
            addonSubtitle(id = "opensubtitles|eng", url = "https://example.test/en.vtt", language = "eng"),
            addonSubtitle(id = "opensubtitles|spa", url = "https://example.test/es.vtt", language = "spa"),
        )

        assertEquals(
            "opensubtitles|spa",
            resolveRememberedAddonSubtitle(
                addons = addons,
                selection = RememberedSubtitleSelection(
                    kind = RememberedSubtitleKind.Addon,
                    addonId = "opensubtitles|spa",
                    url = null,
                ),
            )?.id,
        )
    }

    @Test
    fun wrongSelectionKindDoesNotResolve() {
        assertEquals(
            -1,
            resolveRememberedSubtitleTrackIndex(
                tracks = listOf(subtitleTrack(index = 0, id = "eng", label = "English", language = "eng")),
                selection = RememberedSubtitleSelection(kind = RememberedSubtitleKind.Addon, index = 0),
            ),
        )
        assertNull(
            resolveRememberedAddonSubtitle(
                addons = listOf(addonSubtitle(id = "eng", url = "https://example.test/en.vtt", language = "eng")),
                selection = RememberedSubtitleSelection(kind = RememberedSubtitleKind.BuiltIn, addonId = "eng"),
            ),
        )
    }

    private fun subtitleTrack(
        index: Int,
        id: String,
        label: String,
        language: String?,
    ): SubtitleTrack = SubtitleTrack(
        index = index,
        id = id,
        label = label,
        language = language,
    )

    private fun addonSubtitle(
        id: String,
        url: String,
        language: String,
    ): AddonSubtitle = AddonSubtitle(
        id = id,
        url = url,
        language = language,
        display = language,
    )
}
