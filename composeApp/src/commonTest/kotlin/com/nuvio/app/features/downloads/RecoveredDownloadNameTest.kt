package com.nuvio.app.features.downloads

import kotlin.test.Test
import kotlin.test.assertEquals

class RecoveredDownloadNameTest {
    @Test
    fun stripsLegacyTimestampAndNewUniqueDownloadSuffixes() {
        assertEquals("Show S01E02 Episode", "Show_S01E02_Episode_m123abcd.mkv".recoveredDisplayNameFromFileName())
        assertEquals("Show S01E02 Episode", "Show_S01E02_Episode_m123abcd_1.mkv".recoveredDisplayNameFromFileName())
        assertEquals("Film", "Film_m123abcd_1z.mp4".recoveredDisplayNameFromFileName())
    }
}
