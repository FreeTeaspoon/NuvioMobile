package com.nuvio.app.features.downloads

internal const val DownloadProgressUiIntervalMs = 100L
internal const val DownloadProgressPersistenceIntervalMs = 1_000L
internal const val DownloadProgressNotificationIntervalMs = 500L

internal data class DownloadProgressUpdate(
    val publishUi: Boolean,
    val persist: Boolean,
    val notify: Boolean,
    val speedBytesPerSecond: Long?,
)

internal class DownloadProgressUpdateTracker {
    private var lastUiUpdateAtMs = Long.MIN_VALUE
    private var lastPersistenceAtMs = Long.MIN_VALUE
    private var lastNotificationAtMs = Long.MIN_VALUE
    private var lastPublishedBytes = -1L
    private var lastPublishedTotalBytes: Long? = null
    private var smoothedSpeedBytesPerSecond: Double? = null

    fun update(
        downloadedBytes: Long,
        totalBytes: Long?,
        nowMs: Long,
    ): DownloadProgressUpdate {
        val normalizedDownloadedBytes = downloadedBytes.coerceAtLeast(0L)
        val normalizedTotalBytes = totalBytes?.takeIf { it > 0L }
        val isFirstUpdate = lastPublishedBytes < 0L
        val restarted = normalizedDownloadedBytes < lastPublishedBytes
        val totalChanged = normalizedTotalBytes != lastPublishedTotalBytes
        val reachedEnd = normalizedTotalBytes != null && normalizedDownloadedBytes >= normalizedTotalBytes
        val forceUpdate = isFirstUpdate || restarted || totalChanged || reachedEnd
        val uiElapsedMs = elapsedSince(lastUiUpdateAtMs, nowMs)

        if (!forceUpdate && uiElapsedMs < DownloadProgressUiIntervalMs) {
            return DownloadProgressUpdate(
                publishUi = false,
                persist = false,
                notify = false,
                speedBytesPerSecond = null,
            )
        }

        val speedBytesPerSecond = if (!isFirstUpdate && !restarted && uiElapsedMs > 0L) {
            val byteDelta = (normalizedDownloadedBytes - lastPublishedBytes).coerceAtLeast(0L)
            val sampledSpeed = byteDelta.toDouble() * 1_000.0 / uiElapsedMs.toDouble()
            val smoothedSpeed = smoothedSpeedBytesPerSecond?.let { previous ->
                (previous * 0.65) + (sampledSpeed * 0.35)
            } ?: sampledSpeed
            smoothedSpeedBytesPerSecond = smoothedSpeed
            smoothedSpeed.toLong().coerceAtLeast(0L)
        } else {
            smoothedSpeedBytesPerSecond = null
            null
        }

        lastUiUpdateAtMs = nowMs
        lastPublishedBytes = normalizedDownloadedBytes
        lastPublishedTotalBytes = normalizedTotalBytes

        val shouldPersist = forceUpdate ||
            elapsedSince(lastPersistenceAtMs, nowMs) >= DownloadProgressPersistenceIntervalMs
        val shouldNotify = forceUpdate ||
            elapsedSince(lastNotificationAtMs, nowMs) >= DownloadProgressNotificationIntervalMs

        if (shouldPersist) lastPersistenceAtMs = nowMs
        if (shouldNotify) lastNotificationAtMs = nowMs

        return DownloadProgressUpdate(
            publishUi = true,
            persist = shouldPersist,
            notify = shouldNotify,
            speedBytesPerSecond = speedBytesPerSecond,
        )
    }

    private fun elapsedSince(previousMs: Long, nowMs: Long): Long {
        if (previousMs == Long.MIN_VALUE) return Long.MAX_VALUE
        return (nowMs - previousMs).takeIf { it >= 0L } ?: Long.MAX_VALUE
    }
}
