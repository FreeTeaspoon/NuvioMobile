package com.nuvio.app.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsScreenStateCompatibilityTest {

    @Test
    fun unknownSavedPageFallsBackToRoot() {
        assertEquals(SettingsPage.Root, settingsPageFromSavedName("LegacyPage"))
    }

    @Test
    fun unknownRequestedPageIsIgnored() {
        assertNull(settingsPageFromSavedNameOrNull("LegacyPage"))
    }

    @Test
    fun unknownSavedCategoryFallsBackToGeneral() {
        assertEquals(SettingsCategory.General, settingsCategoryFromSavedName("LegacyCategory"))
    }
}
