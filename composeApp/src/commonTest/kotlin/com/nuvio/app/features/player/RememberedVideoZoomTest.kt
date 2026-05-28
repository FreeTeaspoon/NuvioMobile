package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RememberedVideoZoomTest {

    @Test
    fun contentKeyUsesTitleIdentity() {
        assertEquals("series|tt123", rememberedVideoZoomContentKey("Series", "tt123"))
        assertEquals("movie|tt123", rememberedVideoZoomContentKey("movie", "tt123"))
        assertNull(rememberedVideoZoomContentKey("", "tt123"))
        assertNull(rememberedVideoZoomContentKey("series", ""))
    }

    @Test
    fun decodeRememberedVideoZooms_handlesEmptyAndMalformedStorage() {
        assertEquals(emptyMap(), decodeRememberedVideoZooms(null))
        assertEquals(emptyMap(), decodeRememberedVideoZooms(""))
        assertEquals(emptyMap(), decodeRememberedVideoZooms("not json"))
    }

    @Test
    fun decodeRememberedVideoZooms_separatesShowsByContentKey() {
        val zooms = decodeRememberedVideoZooms("""{"series|A":0.35,"series|B":1.1}""")

        assertEquals(0.35f, zooms["series|A"])
        assertEquals(1.1f, zooms["series|B"])
        assertNull(zooms["series|C"])
    }

    @Test
    fun rememberedVideoZoomsAfterSave_updatesOnlyTheTargetShow() {
        val zooms = rememberedVideoZoomsAfterSave(
            current = mapOf("series|A" to 0.25f),
            contentKey = "series|B",
            zoom = 0.75f,
        )

        assertEquals(0.25f, zooms["series|A"])
        assertEquals(0.75f, zooms["series|B"])
    }

    @Test
    fun decodeRememberedVideoZooms_clampsStoredValues() {
        val zooms = decodeRememberedVideoZooms("""{"series|A":9.0,"series|B":-9.0}""")

        assertEquals(PlayerVideoZoomMax, zooms["series|A"])
        assertEquals(PlayerVideoZoomMin, zooms["series|B"])
    }

    @Test
    fun rememberedVideoZoomsAfterSave_clampsSavedValues() {
        val zooms = rememberedVideoZoomsAfterSave(
            current = emptyMap(),
            contentKey = "series|A",
            zoom = 9f,
        )

        assertEquals(PlayerVideoZoomMax, zooms["series|A"])
    }
}
