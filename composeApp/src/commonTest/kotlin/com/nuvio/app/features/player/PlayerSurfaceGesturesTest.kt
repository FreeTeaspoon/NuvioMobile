package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerSurfaceGesturesTest {
    @Test
    fun horizontalSeekUsesCenterRegionAndLeavesSideZonesDead() {
        val width = 100f

        assertTrue(isHorizontalSeekInDeadZone(positionX = 0f, width = width))
        assertTrue(isHorizontalSeekInDeadZone(positionX = 39f, width = width))
        assertFalse(isHorizontalSeekInDeadZone(positionX = 40f, width = width))
        assertFalse(isHorizontalSeekInDeadZone(positionX = 60f, width = width))
        assertTrue(isHorizontalSeekInDeadZone(positionX = 61f, width = width))
        assertTrue(isHorizontalSeekInDeadZone(positionX = 100f, width = width))
    }

    @Test
    fun horizontalSeekRejectsInvalidWidth() {
        assertTrue(isHorizontalSeekInDeadZone(positionX = 0f, width = 0f))
    }
}
