package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerStartupRulesTest {

    @Test
    fun absoluteInitialPositionWinsOverProgressFraction() {
        val result = resolveInitialSeekTarget(
            initialPositionMs = 60_000L,
            initialProgressFraction = 0.5f,
            durationMs = 200_000L,
        )

        assertEquals(InitialSeekTarget.Position(60_000L), result)
    }

    @Test
    fun progressFractionWaitsForDurationWhenDurationIsUnknown() {
        val result = resolveInitialSeekTarget(
            initialPositionMs = 0L,
            initialProgressFraction = 0.5f,
            durationMs = 0L,
        )

        assertEquals(InitialSeekTarget.WaitingForDuration, result)
    }

    @Test
    fun percentageResumeCanWaitLongerForSlowDurationDiscovery() {
        assertTrue(InitialSeekDurationWaitMaxAttempts > InitialSeekMaxAttempts)
    }

    @Test
    fun localPositionResumeCanRetryLongerDuringSlowStartup() {
        assertTrue(InitialSeekPositionMaxAttempts > InitialSeekMaxAttempts)
    }

    @Test
    fun progressFractionComputesPositionWhenDurationIsKnown() {
        val result = resolveInitialSeekTarget(
            initialPositionMs = 0L,
            initialProgressFraction = 0.25f,
            durationMs = 400_000L,
        )

        assertEquals(InitialSeekTarget.Position(100_000L), result)
    }

    @Test
    fun seekIsSettledWithinTolerance() {
        assertTrue(
            hasInitialSeekSettled(
                targetMs = 120_000L,
                currentPositionMs = 118_600L,
                durationMs = 300_000L,
            )
        )
        assertFalse(
            hasInitialSeekSettled(
                targetMs = 120_000L,
                currentPositionMs = 118_400L,
                durationMs = 300_000L,
            )
        )
    }

    @Test
    fun seekTargetClampsToDuration() {
        val result = resolveInitialSeekTarget(
            initialPositionMs = 500_000L,
            initialProgressFraction = null,
            durationMs = 300_000L,
        )

        assertEquals(InitialSeekTarget.Position(300_000L), result)
    }

    @Test
    fun emptyInitialValuesDoNotNeedSeek() {
        val result = resolveInitialSeekTarget(
            initialPositionMs = 0L,
            initialProgressFraction = null,
            durationMs = 300_000L,
        )

        assertEquals(InitialSeekTarget.None, result)
    }

    @Test
    fun openingOverlayWaitsForKnownDuration() {
        val snapshot = PlayerPlaybackSnapshot(
            isLoading = false,
            durationMs = 0L,
        )

        assertFalse(
            isPlaybackReadyForOpeningOverlay(
                snapshot = snapshot,
                initialSeekApplied = true,
            )
        )
    }

    @Test
    fun openingOverlayCompletesAfterSeekLoadingAndDurationAreReady() {
        val snapshot = PlayerPlaybackSnapshot(
            isLoading = false,
            durationMs = 120_000L,
        )

        assertTrue(
            isPlaybackReadyForOpeningOverlay(
                snapshot = snapshot,
                initialSeekApplied = true,
            )
        )
        assertFalse(
            isPlaybackReadyForOpeningOverlay(
                snapshot = snapshot,
                initialSeekApplied = false,
            )
        )
    }

    @Test
    fun finishedScrubTargetUsesLatestDragValue() {
        assertEquals(
            75_000L,
            resolveFinishedScrubTarget(
                latestScrubPositionMs = 75_000L,
                durationMs = 120_000L,
            )
        )
    }

    @Test
    fun pendingScrubDisplayOverridesStaleSnapshotUntilSettled() {
        val staleSnapshot = PlayerPlaybackSnapshot(
            durationMs = 120_000L,
            positionMs = 15_000L,
        )

        assertEquals(
            75_000L,
            resolveDisplayedPlaybackPosition(
                scrubbingPositionMs = null,
                pendingScrubTargetMs = 75_000L,
                snapshotPositionMs = staleSnapshot.positionMs,
            )
        )
        assertTrue(shouldHoldPendingScrubDisplay(75_000L, staleSnapshot))

        val settledSnapshot = staleSnapshot.copy(positionMs = 74_000L)
        assertFalse(shouldHoldPendingScrubDisplay(75_000L, settledSnapshot))
    }

    @Test
    fun horizontalSeekForwardDeltaCountsDownAgainstPlayback() {
        assertEquals(3, calculateHorizontalSeekDeltaSeconds(targetPositionMs = 13_000L, currentPositionMs = 10_000L))
        assertEquals(2, calculateHorizontalSeekDeltaSeconds(targetPositionMs = 13_000L, currentPositionMs = 11_000L))
        assertEquals(1, calculateHorizontalSeekDeltaSeconds(targetPositionMs = 13_000L, currentPositionMs = 12_000L))
        assertEquals(0, calculateHorizontalSeekDeltaSeconds(targetPositionMs = 13_000L, currentPositionMs = 13_000L))
    }

    @Test
    fun horizontalSeekBackwardKeepsFixedTargetAgainstPlayback() {
        assertEquals(-3, calculateHorizontalSeekDeltaSeconds(targetPositionMs = 7_000L, currentPositionMs = 10_000L))
        assertEquals(-6, calculateHorizontalSeekDeltaSeconds(targetPositionMs = 7_000L, currentPositionMs = 13_000L))
    }
}
