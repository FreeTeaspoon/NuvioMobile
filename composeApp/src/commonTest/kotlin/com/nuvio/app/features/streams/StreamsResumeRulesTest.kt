package com.nuvio.app.features.streams

import com.nuvio.app.features.watchprogress.WatchProgressEntry
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
        lastPositionMs: Long = 120_000L,
        durationMs: Long = 600_000L,
        progressPercent: Float? = null,
    ): WatchProgressEntry =
        WatchProgressEntry(
            contentType = "movie",
            parentMetaId = "movie-1",
            parentMetaType = "movie",
            videoId = "movie-1",
            title = "Movie",
            lastPositionMs = lastPositionMs,
            durationMs = durationMs,
            lastUpdatedEpochMs = 1L,
            progressPercent = progressPercent,
        )
}
