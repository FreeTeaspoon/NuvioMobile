package com.nuvio.app.features.player

import kotlin.math.abs

internal const val InitialSeekRetryIntervalMs = 250L
internal const val InitialSeekMaxAttempts = 24
internal const val InitialSeekDurationWaitMaxAttempts = 120
internal const val InitialSeekPositionMaxAttempts = 120
internal const val InitialSeekToleranceMs = 1_500L
internal const val PendingScrubDisplayTimeoutMs = 1_500L

internal sealed class InitialSeekTarget {
    data object None : InitialSeekTarget()
    data object WaitingForDuration : InitialSeekTarget()
    data class Position(val positionMs: Long) : InitialSeekTarget()
}

internal fun resolveInitialSeekTarget(
    initialPositionMs: Long,
    initialProgressFraction: Float?,
    durationMs: Long,
): InitialSeekTarget {
    val duration = durationMs.coerceAtLeast(0L)
    val explicitPosition = initialPositionMs.coerceAtLeast(0L)
    if (explicitPosition > 0L) {
        return InitialSeekTarget.Position(
            if (duration > 0L) explicitPosition.coerceIn(0L, duration) else explicitPosition,
        )
    }

    val progressFraction = initialProgressFraction
        ?.takeIf { it > 0f }
        ?.coerceIn(0f, 1f)
        ?: return InitialSeekTarget.None

    if (duration <= 0L) return InitialSeekTarget.WaitingForDuration

    val positionMs = (duration.toDouble() * progressFraction.toDouble())
        .toLong()
        .coerceIn(0L, duration)
    return if (positionMs > 0L) {
        InitialSeekTarget.Position(positionMs)
    } else {
        InitialSeekTarget.None
    }
}

internal fun hasInitialSeekSettled(
    targetMs: Long,
    currentPositionMs: Long,
    durationMs: Long,
): Boolean {
    val duration = durationMs.coerceAtLeast(0L)
    val target = if (duration > 0L) {
        targetMs.coerceIn(0L, duration)
    } else {
        targetMs.coerceAtLeast(0L)
    }
    val current = if (duration > 0L) {
        currentPositionMs.coerceIn(0L, duration)
    } else {
        currentPositionMs.coerceAtLeast(0L)
    }
    return abs(current - target) <= InitialSeekToleranceMs
}

internal fun isPlaybackReadyForOpeningOverlay(
    snapshot: PlayerPlaybackSnapshot,
    initialSeekApplied: Boolean,
): Boolean =
    initialSeekApplied &&
        !snapshot.isLoading &&
        snapshot.durationMs > 0L

internal fun shouldHoldPendingScrubDisplay(
    targetMs: Long,
    snapshot: PlayerPlaybackSnapshot,
): Boolean =
    !hasInitialSeekSettled(
        targetMs = targetMs,
        currentPositionMs = snapshot.positionMs,
        durationMs = snapshot.durationMs,
    )

internal fun resolveDisplayedPlaybackPosition(
    scrubbingPositionMs: Long?,
    pendingScrubTargetMs: Long?,
    snapshotPositionMs: Long,
): Long =
    scrubbingPositionMs
        ?: pendingScrubTargetMs
        ?: snapshotPositionMs

internal fun resolveFinishedScrubTarget(
    latestScrubPositionMs: Long,
    durationMs: Long,
): Long =
    latestScrubPositionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))

internal fun calculateHorizontalSeekDeltaSeconds(
    targetPositionMs: Long,
    currentPositionMs: Long,
): Int =
    ((targetPositionMs - currentPositionMs) / 1000L).toInt()
