package com.nuvio.app.features.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackMimeRecoveryAndroidTest {

    @Test
    fun hlsPathTokenSetsAdaptiveMimeType() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            inferPlaybackMimeType(
                url = "https://example.test/play/hls/session",
                responseHeaders = emptyMap(),
                streamType = null,
            ),
        )
    }

    @Test
    fun sourceErrorIsProbedOnlyOnce() {
        assertTrue(
            shouldProbePlaybackMimeType(
                errorCode = PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                causeText = null,
                probeAttempted = false,
            ),
        )
        assertFalse(
            shouldProbePlaybackMimeType(
                errorCode = PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                causeText = null,
                probeAttempted = true,
            ),
        )
    }

    @Test
    fun unrecognizedInputFormatTriggersProbe() {
        assertTrue(
            shouldProbePlaybackMimeType(
                errorCode = PlaybackException.ERROR_CODE_UNSPECIFIED,
                causeText = "androidx.media3.exoplayer.source.UnrecognizedInputFormatException",
                probeAttempted = false,
            ),
        )
    }
}
