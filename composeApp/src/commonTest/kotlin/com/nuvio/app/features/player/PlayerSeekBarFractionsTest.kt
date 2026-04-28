package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerSeekBarFractionsTest {

    @Test
    fun zeroDuration_hidesPlayedAndBufferedFractions() {
        val result = calculatePlayerSeekBarFractions(
            durationMs = 0L,
            displayedPositionMs = 500L,
            bufferedPositionMs = 900L,
        )

        assertEquals(0f, result.playedFraction)
        assertEquals(0f, result.bufferedFraction)
    }

    @Test
    fun negativeBufferedPosition_clampsToZeroAndHidesBufferedSegment() {
        val result = calculatePlayerSeekBarFractions(
            durationMs = 1_000L,
            displayedPositionMs = 250L,
            bufferedPositionMs = -100L,
        )

        assertEquals(0.25f, result.playedFraction)
        assertEquals(0f, result.bufferedFraction)
    }

    @Test
    fun bufferedBeyondDuration_clampsToFullWidth() {
        val result = calculatePlayerSeekBarFractions(
            durationMs = 1_000L,
            displayedPositionMs = 250L,
            bufferedPositionMs = 2_000L,
        )

        assertEquals(0.25f, result.playedFraction)
        assertEquals(1f, result.bufferedFraction)
    }

    @Test
    fun bufferedBehindPlayed_clampsBufferedVisualToPlayedPosition() {
        val result = calculatePlayerSeekBarFractions(
            durationMs = 1_000L,
            displayedPositionMs = 800L,
            bufferedPositionMs = 600L,
        )

        assertEquals(0.8f, result.playedFraction)
        assertEquals(0.8f, result.bufferedFraction)
    }

    @Test
    fun displayedPosition_clampsIntoDurationRange() {
        val result = calculatePlayerSeekBarFractions(
            durationMs = 1_000L,
            displayedPositionMs = 1_800L,
            bufferedPositionMs = 900L,
        )

        assertEquals(1f, result.playedFraction)
        assertEquals(1f, result.bufferedFraction)
    }

    @Test
    fun normalValues_computePlayedAndBufferedFractions() {
        val result = calculatePlayerSeekBarFractions(
            durationMs = 2_000L,
            displayedPositionMs = 500L,
            bufferedPositionMs = 1_500L,
        )

        assertEquals(0.25f, result.playedFraction)
        assertEquals(0.75f, result.bufferedFraction)
    }
}
