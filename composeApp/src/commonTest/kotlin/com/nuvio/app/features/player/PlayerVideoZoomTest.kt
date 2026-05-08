package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerVideoZoomTest {

    @Test
    fun normalized_clampsZoomAndKeepsPanSetting() {
        val high = PlayerVideoZoomState(zoom = 4f, panAndZoomEnabled = true).normalized()
        val low = PlayerVideoZoomState(zoom = -4f, panAndZoomEnabled = false).normalized()

        assertEquals(PlayerVideoZoomMax, high.zoom)
        assertTrue(high.panAndZoomEnabled)
        assertEquals(PlayerVideoZoomMin, low.zoom)
        assertFalse(low.panAndZoomEnabled)
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
