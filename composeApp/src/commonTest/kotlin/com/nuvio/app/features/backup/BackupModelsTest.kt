package com.nuvio.app.features.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupModelsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun payloadRoundTrips() {
        val payload = NuvioBackupPayload(
            exportedAtEpochMs = 123L,
            activeProfileIndex = 2,
            profiles = listOf(
                BackupProfilePayload(
                    profileIndex = 2,
                    profile = BackupProfileMetadata(name = "Kids", pinEnabled = true),
                    addons = BackupAddonsPayload(
                        urls = listOf("https://example.com/manifest.json"),
                        enabledByUrl = mapOf("https://example.com/manifest.json" to false),
                    ),
                    libraryPayload = """{"items":[{"id":"tt1"}]}""",
                    watchProgressPayload = """{"entries":[{"videoId":"tt1:1:1"}]}""",
                ),
            ),
        )

        val decoded = json.decodeFromString<NuvioBackupPayload>(json.encodeToString(payload))

        assertEquals(payload, decoded)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val decoded = json.decodeFromString<NuvioBackupPayload>(
            """
            {
              "schemaVersion": 1,
              "exportedAtEpochMs": 123,
              "activeProfileIndex": 1,
              "futureField": true,
              "profiles": [
                {
                  "profileIndex": 1,
                  "unknownProfileField": "ignored",
                  "addons": {
                    "urls": ["https://example.com/manifest.json"],
                    "enabledByUrl": {}
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, decoded.activeProfileIndex)
        assertEquals(listOf("https://example.com/manifest.json"), decoded.profiles.single().addons.urls)
    }

    @Test
    fun missingSectionsUseDefaults() {
        val decoded = json.decodeFromString<NuvioBackupPayload>(
            """
            {
              "schemaVersion": 1,
              "exportedAtEpochMs": 123,
              "activeProfileIndex": 1,
              "profiles": [{"profileIndex": 1}]
            }
            """.trimIndent(),
        )

        val profile = decoded.profiles.single()
        assertTrue(profile.addons.urls.isEmpty())
        assertEquals("", profile.libraryPayload)
        assertEquals(BackupSettingsPayload(), profile.settings)
    }
}

