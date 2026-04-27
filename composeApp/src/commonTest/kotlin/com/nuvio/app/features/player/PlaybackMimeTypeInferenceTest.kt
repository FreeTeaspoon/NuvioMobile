package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackMimeTypeInferenceTest {

    @Test
    fun specificResponseHeaderWins() {
        assertEquals(
            "video/x-matroska",
            inferPlaybackMimeType(
                sourceUrl = "https://dav.example/stream/abc123",
                responseHeaders = mapOf("Content-Type" to "video/x-matroska"),
                sourceFilename = "Movie.mp4",
            ),
        )
    }

    @Test
    fun genericDavResponseFallsBackToFilename() {
        assertEquals(
            "video/x-matroska",
            inferPlaybackMimeType(
                sourceUrl = "https://dav.example/stream/abc123",
                responseHeaders = mapOf("Content-Type" to "application/octet-stream"),
                sourceFilename = "Movie.mkv",
            ),
        )
    }

    @Test
    fun extensionlessUrlUsesBehaviorHintFilename() {
        assertEquals(
            "video/x-matroska",
            inferPlaybackMimeType(
                sourceUrl = "https://dav.example/stream/abc123",
                sourceFilename = "Episode S01E01.mkv",
            ),
        )
    }

    @Test
    fun urlExtensionIgnoresQueryString() {
        assertEquals(
            "video/mp4",
            inferPlaybackMimeType(sourceUrl = "https://example.test/file.mp4?token=abc"),
        )
    }

    @Test
    fun adaptiveStreamTypesArePreserved() {
        assertEquals(
            "application/x-mpegURL",
            inferPlaybackMimeType(sourceUrl = "https://example.test/master.m3u8"),
        )
        assertEquals(
            "application/dash+xml",
            inferPlaybackMimeType(sourceUrl = "https://example.test/manifest.mpd"),
        )
    }

    @Test
    fun noReliableSignalReturnsNull() {
        assertNull(
            inferPlaybackMimeType(
                sourceUrl = "https://dav.example/stream/abc123",
                responseHeaders = mapOf("Content-Type" to "application/octet-stream"),
            ),
        )
    }

    @Test
    fun responseHeaderLookupIsCaseInsensitive() {
        assertEquals(
            "video/mp4",
            inferPlaybackMimeType(
                sourceUrl = "https://example.test/file.mkv",
                responseHeaders = mapOf("content-type" to "video/mp4; charset=binary"),
            ),
        )
        assertEquals(
            "video/webm",
            inferPlaybackMimeType(
                sourceUrl = "https://example.test/file.mkv",
                responseHeaders = mapOf("CONTENT-TYPE" to "video/webm"),
            ),
        )
    }

    @Test
    fun contentDispositionFilenameIsUsedWhenContentTypeIsGeneric() {
        assertEquals(
            "video/x-matroska",
            inferPlaybackMimeType(
                sourceUrl = "https://dav.example/stream/abc123",
                responseHeaders = mapOf(
                    "Content-Type" to "application/force-download",
                    "Content-Disposition" to "attachment; filename=\"Movie.mkv\"",
                ),
            ),
        )
    }
}
