package com.nuvio.app.features.player

internal data class MpvSeekableCacheRange(
    val startSeconds: Double,
    val endSeconds: Double,
)

internal fun resolveMpvBufferedPositionMs(
    positionSeconds: Double,
    durationSeconds: Double,
    cacheTimeSeconds: Double?,
    seekableRanges: List<MpvSeekableCacheRange>,
): Long {
    val normalizedPosition = positionSeconds.coerceAtLeast(0.0)
    val normalizedDuration = durationSeconds.coerceAtLeast(0.0)
    val rangeEnd = seekableRanges
        .asSequence()
        .filter { it.startSeconds.isFinite() && it.endSeconds.isFinite() }
        .filter { it.endSeconds >= it.startSeconds }
        .sortedBy { it.startSeconds }
        .firstOrNull { range ->
            normalizedPosition in range.startSeconds..range.endSeconds ||
                range.startSeconds >= normalizedPosition
        }
        ?.endSeconds

    val bufferedSeconds = rangeEnd
        ?: cacheTimeSeconds
            ?.takeIf { it.isFinite() }
            ?.coerceAtLeast(0.0)
        ?: 0.0

    val clampedBufferedSeconds = if (normalizedDuration > 0.0) {
        bufferedSeconds.coerceIn(0.0, normalizedDuration)
    } else {
        bufferedSeconds.coerceAtLeast(0.0)
    }

    return (clampedBufferedSeconds * 1000.0).toLong()
}
