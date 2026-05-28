package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerVideoZoomTest {

    @Test
    fun normalized_clampsZoom() {
        val high = PlayerVideoZoomState(zoom = 4f).normalized()
        val low = PlayerVideoZoomState(zoom = -4f).normalized()

        assertEquals(PlayerVideoZoomMax, high.zoom)
        assertEquals(PlayerVideoZoomMin, low.zoom)
    }

    @Test
    fun stepPlayerVideoZoom_usesConfiguredStepAndClamps() {
        assertEquals(0.05f, stepPlayerVideoZoom(0f, 1))
        assertEquals(-0.05f, stepPlayerVideoZoom(0f, -1))
        assertEquals(PlayerVideoZoomMax, stepPlayerVideoZoom(PlayerVideoZoomMax, 1))
        assertEquals(PlayerVideoZoomMin, stepPlayerVideoZoom(PlayerVideoZoomMin, -1))
    }

    @Test
    fun formatPlayerVideoZoomLabel_alwaysShowsTwoDecimals() {
        assertEquals("0.00x", formatPlayerVideoZoomLabel(0f))
        assertEquals("0.05x", formatPlayerVideoZoomLabel(0.05f))
        assertEquals("-0.05x", formatPlayerVideoZoomLabel(-0.05f))
        assertEquals("1.24x", formatPlayerVideoZoomLabel(1.236f))
    }
}
