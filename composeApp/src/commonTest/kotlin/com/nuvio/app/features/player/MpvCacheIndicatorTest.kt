package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals

class MpvCacheIndicatorTest {

    @Test
    fun cacheTimeFallback_isTreatedAsAbsoluteTimestamp() {
        val result = resolveMpvBufferedPositionMs(
            positionSeconds = 600.0,
            durationSeconds = 1_000.0,
            cacheTimeSeconds = 120.0,
            seekableRanges = emptyList(),
        )

        assertEquals(120_000L, result)
    }

    @Test
    fun containingSeekableRange_returnsRangeEnd() {
        val result = resolveMpvBufferedPositionMs(
            positionSeconds = 60.0,
            durationSeconds = 300.0,
            cacheTimeSeconds = 240.0,
            seekableRanges = listOf(
                MpvSeekableCacheRange(startSeconds = 30.0, endSeconds = 90.0),
            ),
        )

        assertEquals(90_000L, result)
    }

    @Test
    fun multipleSeekableRanges_pickRelevantForwardRange() {
        val result = resolveMpvBufferedPositionMs(
            positionSeconds = 120.0,
            durationSeconds = 300.0,
            cacheTimeSeconds = 260.0,
            seekableRanges = listOf(
                MpvSeekableCacheRange(startSeconds = 200.0, endSeconds = 230.0),
                MpvSeekableCacheRange(startSeconds = 110.0, endSeconds = 150.0),
                MpvSeekableCacheRange(startSeconds = 40.0, endSeconds = 80.0),
            ),
        )

        assertEquals(150_000L, result)
    }

    @Test
    fun unreadableSeekableRanges_fallsBackToCacheTime() {
        val result = resolveMpvBufferedPositionMs(
            positionSeconds = 80.0,
            durationSeconds = 300.0,
            cacheTimeSeconds = 180.0,
            seekableRanges = listOf(
                MpvSeekableCacheRange(startSeconds = Double.NaN, endSeconds = 150.0),
                MpvSeekableCacheRange(startSeconds = 40.0, endSeconds = Double.NaN),
            ),
        )

        assertEquals(180_000L, result)
    }

    @Test
    fun negativeAndBeyondDurationValues_areClamped() {
        val negativeResult = resolveMpvBufferedPositionMs(
            positionSeconds = -20.0,
            durationSeconds = 300.0,
            cacheTimeSeconds = -10.0,
            seekableRanges = emptyList(),
        )
        val beyondDurationResult = resolveMpvBufferedPositionMs(
            positionSeconds = 40.0,
            durationSeconds = 300.0,
            cacheTimeSeconds = 500.0,
            seekableRanges = emptyList(),
        )

        assertEquals(0L, negativeResult)
        assertEquals(300_000L, beyondDurationResult)
    }
}
