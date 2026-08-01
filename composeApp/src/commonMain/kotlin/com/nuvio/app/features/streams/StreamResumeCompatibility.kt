package com.nuvio.app.features.streams

import com.nuvio.app.features.watchprogress.WatchProgressEntry

internal data class EffectiveStreamResume(
    val positionMs: Long?,
    val progressFraction: Float?,
)

/**
 * Keeps resume precedence stable while the streams UI is refactored.
 * An explicit position is more precise than a fractional hint, followed by
 * stored position, then the two fractional fallbacks.
 */
internal fun resolveEffectiveStreamResume(
    requestedPositionMs: Long?,
    requestedProgressFraction: Float?,
    storedProgress: WatchProgressEntry?,
    startFromBeginning: Boolean,
): EffectiveStreamResume {
    if (startFromBeginning) {
        return EffectiveStreamResume(positionMs = null, progressFraction = null)
    }

    requestedPositionMs?.takeIf { it > 0L }?.let { positionMs ->
        return EffectiveStreamResume(positionMs = positionMs, progressFraction = null)
    }

    storedProgress
        ?.takeIf { it.isResumable }
        ?.lastPositionMs
        ?.takeIf { it > 0L }
        ?.let { positionMs ->
            return EffectiveStreamResume(positionMs = positionMs, progressFraction = null)
        }

    requestedProgressFraction?.takeIf { it > 0f }?.let { progressFraction ->
        return EffectiveStreamResume(
            positionMs = null,
            progressFraction = progressFraction.coerceIn(0f, 1f),
        )
    }

    val storedProgressFraction = storedProgress
        ?.takeIf { it.isResumable }
        ?.progressPercent
        ?.takeIf { it > 0f }
        ?.let { progressPercent -> (progressPercent / 100f).coerceIn(0f, 1f) }

    return EffectiveStreamResume(positionMs = null, progressFraction = storedProgressFraction)
}
