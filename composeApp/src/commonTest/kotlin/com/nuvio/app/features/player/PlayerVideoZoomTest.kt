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
    fun labelMatchesTheActualVideoScale() {
        assertEquals("0.25x", formatPlayerVideoZoomLabel(-2f))
        assertEquals("0.50x", formatPlayerVideoZoomLabel(-1f))
        assertEquals("1.00x", formatPlayerVideoZoomLabel(0f))
        assertEquals("2.00x", formatPlayerVideoZoomLabel(1f))
        assertEquals("4.00x", formatPlayerVideoZoomLabel(2f))
        assertEquals("1.04x", formatPlayerVideoZoomLabel(0.05f))
    }

    @Test
    fun corruptSavedZoomFallsBackToOriginalSize() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertEquals(0f, PlayerVideoZoomState(it).normalized().zoom)
            assertEquals(1f, playerVideoZoomScale(it))
        }
    }
}
