package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerEnginePlaybackSourceTest {
    @Test
    fun davMatroskaSourcePrefersMpv() {
        assertTrue(
            shouldPreferMpvForPlaybackSource(
                sourceUrl = "https://media.example/nzbdav/stream",
                responseHeaders = mapOf("Content-Type" to "application/octet-stream"),
                sourceFilename = "Movie.mkv",
            ),
        )
    }

    @Test
    fun davNonMatroskaSourceDoesNotPreferMpv() {
        assertFalse(
            shouldPreferMpvForPlaybackSource(
                sourceUrl = "https://media.example/webdav/stream",
                sourceFilename = "Movie.mp4",
            ),
        )
    }

    @Test
    fun varintFailureFallsBackOnlyForDavMatroskaSource() {
        assertTrue(
            shouldFallbackToMpvForPlaybackError(
                sourceUrl = "https://user:password@media.example/stream",
                sourceFilename = "Episode.mkv",
                errorMessage = "ParserException: No valid varint length mask found",
            ),
        )

        assertFalse(
            shouldFallbackToMpvForPlaybackError(
                sourceUrl = "https://media.example/regular/Episode.mkv",
                sourceFilename = "Episode.mkv",
                errorMessage = "ParserException: No valid varint length mask found",
            ),
        )
    }

    @Test
    fun varintFailureNearTailIsTreatedAsEnded() {
        val snapshot = PlayerPlaybackSnapshot(
            durationMs = 1_000_000L,
            positionMs = 992_000L,
            bufferedPositionMs = 995_000L,
        )

        assertTrue(
            shouldTreatMedia3VarintFailureAsEnded(
                sourceUrl = "https://media.example/altmount/file",
                sourceFilename = "Episode.mkv",
                errorMessage = "No valid varint length mask found",
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun varintFailureAwayFromTailIsNotTreatedAsEnded() {
        val snapshot = PlayerPlaybackSnapshot(
            durationMs = 1_000_000L,
            positionMs = 500_000L,
            bufferedPositionMs = 700_000L,
        )

        assertFalse(
            shouldTreatMedia3VarintFailureAsEnded(
                sourceUrl = "https://media.example/altmount/file",
                sourceFilename = "Episode.mkv",
                errorMessage = "No valid varint length mask found",
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun endedSnapshotNormalizesPositionAndBuffer() {
        val ended = PlayerPlaybackSnapshot(
            isLoading = true,
            isPlaying = true,
            durationMs = 60_000L,
            positionMs = 58_000L,
            bufferedPositionMs = 59_000L,
        ).asEndedPlaybackSnapshot()

        assertFalse(ended.isLoading)
        assertFalse(ended.isPlaying)
        assertTrue(ended.isEnded)
        assertEquals(60_000L, ended.positionMs)
        assertEquals(60_000L, ended.bufferedPositionMs)
    }
}
