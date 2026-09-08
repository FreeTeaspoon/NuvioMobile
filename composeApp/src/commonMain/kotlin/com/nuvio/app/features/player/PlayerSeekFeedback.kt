package com.nuvio.app.features.player

internal fun resolveFinishedScrubTarget(latestScrubPositionMs: Long, durationMs: Long): Long =
    latestScrubPositionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))

internal fun calculateHorizontalSeekDeltaSeconds(targetPositionMs: Long, currentPositionMs: Long): Int =
    ((targetPositionMs - currentPositionMs) / 1000L).toInt()
