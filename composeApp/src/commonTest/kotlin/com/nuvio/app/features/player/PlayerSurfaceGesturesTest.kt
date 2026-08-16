package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerSurfaceGesturesTest {
    @Test
    fun horizontalSeekUsesMiddleEightyPercentAndLeavesOuterEdgesDead() {
        val width = 100f

        assertTrue(isHorizontalSeekInDeadZone(positionX = 0f, width = width))
        assertTrue(isHorizontalSeekInDeadZone(positionX = 9f, width = width))
        assertFalse(isHorizontalSeekInDeadZone(positionX = 10f, width = width))
        assertFalse(isHorizontalSeekInDeadZone(positionX = 50f, width = width))
        assertFalse(isHorizontalSeekInDeadZone(positionX = 90f, width = width))
        assertTrue(isHorizontalSeekInDeadZone(positionX = 91f, width = width))
        assertTrue(isHorizontalSeekInDeadZone(positionX = 100f, width = width))
    }

    @Test
    fun horizontalSeekRejectsInvalidWidth() {
        assertTrue(isHorizontalSeekInDeadZone(positionX = 0f, width = 0f))
    }
}
