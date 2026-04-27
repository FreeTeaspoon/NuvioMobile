package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerEngineTypeTest {
    @Test
    fun missingStoredEngineFallsBackToMedia3() {
        assertEquals(
            PlayerEngineType.MEDIA3,
            resolvePlayerEngine(rawEngine = null, mpvSelectable = true),
        )
    }

    @Test
    fun invalidStoredEngineFallsBackToMedia3() {
        assertEquals(
            PlayerEngineType.MEDIA3,
            resolvePlayerEngine(rawEngine = "UNKNOWN", mpvSelectable = true),
        )
    }

    @Test
    fun mpvStoredOnUnsupportedBuildUsesMedia3() {
        assertEquals(
            PlayerEngineType.MEDIA3,
            resolvePlayerEngine(rawEngine = "MPV", mpvSelectable = false),
        )
    }

    @Test
    fun mpvStoredOnSupportedBuildUsesMpv() {
        assertEquals(
            PlayerEngineType.MPV,
            resolvePlayerEngine(rawEngine = "MPV", mpvSelectable = true),
        )
    }
}
