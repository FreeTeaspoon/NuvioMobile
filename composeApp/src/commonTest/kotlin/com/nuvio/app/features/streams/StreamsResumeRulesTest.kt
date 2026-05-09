package com.nuvio.app.features.streams

import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.progressForPlaybackTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamsResumeRulesTest {

    @Test
    fun exactRequestedPositionWinsOverRequestedProgressFraction() {
        val result = resolveEffectiveStreamResume(
            requestedPositionMs = 120_000L,
            requestedProgressFraction = 0.5f,
            storedProgress = null,
            startFromBeginning = false,
        )

        assertEquals(120_000L, result.positionMs)
        assertNull(result.progressFraction)
    }

    @Test
    fun storedPositionWinsOverStoredProgressPercent() {
        val result = resolveEffectiveStreamResume(
            requestedPositionMs = null,
            requestedProgressFraction = null,
            storedProgress = progressEntry(
                lastPositionMs = 180_000L,
                durationMs = 600_000L,
                progressPercent = 30f,
            ),
            startFromBeginning = false,
        )

        assertEquals(180_000L, result.positionMs)
        assertNull(result.progressFraction)
    }

    @Test
    fun storedProgressCanResumeWhenResolvedEpisodeIdDiffersFromProgressId() {
        val storedProgress = listOf(
            progressEntry(
                videoId = "addon-episode-id",
                parentMetaId = "show",
                seasonNumber = 1,
                episodeNumber = 2,
                lastPositionMs = 240_000L,
            ),
        ).progressForPlaybackTarget(
            videoId = "show:1:2",
            parentMetaId = "show",
            seasonNumber = 1,
            episodeNumber = 2,
        )

        val result = resolveEffectiveStreamResume(
            requestedPositionMs = null,
            requestedProgressFraction = null,
            storedProgress = storedProgress,
            startFromBeginning = false,
        )

        assertEquals(240_000L, result.positionMs)
        assertNull(result.progressFraction)
    }

    @Test
    fun requestedProgressFractionIsUsedWhenNoPositionExists() {
        val result = resolveEffectiveStreamResume(
            requestedPositionMs = 0L,
            requestedProgressFraction = 0.4f,
            storedProgress = null,
            startFromBeginning = false,
        )

        assertNull(result.positionMs)
        assertEquals(0.4f, result.progressFraction)
    }

    @Test
    fun startFromBeginningClearsResumeTarget() {
        val result = resolveEffectiveStreamResume(
            requestedPositionMs = 120_000L,
            requestedProgressFraction = 0.5f,
            storedProgress = progressEntry(),
            startFromBeginning = true,
        )

        assertNull(result.positionMs)
        assertNull(result.progressFraction)
    }

    private fun progressEntry(
        videoId: String = "movie-1",
        parentMetaId: String = "movie-1",
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        lastPositionMs: Long = 120_000L,
        durationMs: Long = 600_000L,
        progressPercent: Float? = null,
    ): WatchProgressEntry =
        WatchProgressEntry(
            contentType = if (seasonNumber != null && episodeNumber != null) "series" else "movie",
            parentMetaId = parentMetaId,
            parentMetaType = if (seasonNumber != null && episodeNumber != null) "series" else "movie",
            videoId = videoId,
            title = "Movie",
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            lastPositionMs = lastPositionMs,
            durationMs = durationMs,
            lastUpdatedEpochMs = 1L,
            progressPercent = progressPercent,
        )
}
