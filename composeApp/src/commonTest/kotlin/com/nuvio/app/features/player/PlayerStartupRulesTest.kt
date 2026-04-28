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
}
