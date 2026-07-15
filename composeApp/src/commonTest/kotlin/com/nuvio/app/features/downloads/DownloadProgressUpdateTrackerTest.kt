package com.nuvio.app.features.downloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadProgressUpdateTrackerTest {
    @Test
    fun firstUpdatePublishesPersistsAndNotifies() {
        val update = DownloadProgressUpdateTracker().update(
            downloadedBytes = 0L,
            totalBytes = 10_000_000L,
            nowMs = 1_000L,
        )

        assertTrue(update.publishUi)
        assertTrue(update.persist)
        assertTrue(update.notify)
        assertNull(update.speedBytesPerSecond)
    }

    @Test
    fun uiUpdatesAtOneHundredMillisecondsWithoutExtraWrites() {
        val tracker = DownloadProgressUpdateTracker()
        tracker.update(downloadedBytes = 0L, totalBytes = 10_000_000L, nowMs = 0L)

        val tooSoon = tracker.update(
            downloadedBytes = 50_000L,
            totalBytes = 10_000_000L,
            nowMs = 99L,
        )
        val uiUpdate = tracker.update(
            downloadedBytes = 100_000L,
            totalBytes = 10_000_000L,
            nowMs = 100L,
        )

        assertFalse(tooSoon.publishUi)
        assertTrue(uiUpdate.publishUi)
        assertFalse(uiUpdate.persist)
        assertFalse(uiUpdate.notify)
        assertEquals(1_000_000L, uiUpdate.speedBytesPerSecond)
    }

    @Test
    fun notificationAndPersistenceUseIndependentCadences() {
        val tracker = DownloadProgressUpdateTracker()
        tracker.update(downloadedBytes = 0L, totalBytes = 10_000_000L, nowMs = 0L)

        val notificationUpdate = tracker.update(
            downloadedBytes = 500_000L,
            totalBytes = 10_000_000L,
            nowMs = DownloadProgressNotificationIntervalMs,
        )
        val persistenceUpdate = tracker.update(
            downloadedBytes = 1_000_000L,
            totalBytes = 10_000_000L,
            nowMs = DownloadProgressPersistenceIntervalMs,
        )

        assertTrue(notificationUpdate.publishUi)
        assertTrue(notificationUpdate.notify)
        assertFalse(notificationUpdate.persist)
        assertTrue(persistenceUpdate.publishUi)
        assertTrue(persistenceUpdate.notify)
        assertTrue(persistenceUpdate.persist)
    }

    @Test
    fun reachingEndForcesAllUpdates() {
        val tracker = DownloadProgressUpdateTracker()
        tracker.update(downloadedBytes = 0L, totalBytes = 1_000L, nowMs = 0L)

        val update = tracker.update(
            downloadedBytes = 1_000L,
            totalBytes = 1_000L,
            nowMs = 10L,
        )

        assertTrue(update.publishUi)
        assertTrue(update.persist)
        assertTrue(update.notify)
    }
}
