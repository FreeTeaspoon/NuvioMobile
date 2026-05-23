package com.nuvio.app.features.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpdaterTest {
    @Test
    fun `latest fork update accepts plain version tags with sha release targets`() {
        val update = AppUpdaterRepository.parseLatestForkUpdate(
            """
            [
              {
                "tag_name": "0.1.12",
                "name": "0.1.12",
                "body": "Fork build from FreeTeaspoon/NuvioMobile:cmp-rewrite.",
                "draft": false,
                "prerelease": false,
                "target_commitish": "d27b3ff87f9b3575bc706552c57b3dc837776743",
                "html_url": "https://github.com/FreeTeaspoon/NuvioMobile/releases/tag/0.1.12",
                "assets": [
                  {
                    "name": "nuvio-freeteaspoon-full-0.1.12.apk",
                    "browser_download_url": "https://github.com/FreeTeaspoon/NuvioMobile/releases/download/0.1.12/nuvio-freeteaspoon-full-0.1.12.apk",
                    "size": 1234,
                    "content_type": "application/vnd.android.package-archive"
                  }
                ]
              },
              {
                "tag_name": "cmp-rewrite-0.1.10.3",
                "name": "cmp-rewrite-0.1.10.3",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "nuvio-freeteaspoon-full-0.1.10.3.apk",
                    "browser_download_url": "https://github.com/FreeTeaspoon/NuvioMobile/releases/download/cmp-rewrite-0.1.10.3/nuvio-freeteaspoon-full-0.1.10.3.apk"
                  }
                ]
              }
            ]
            """.trimIndent(),
        )

        assertEquals("0.1.12", update.tag)
        assertEquals("0.1.12", update.title)
        assertEquals("nuvio-freeteaspoon-full-0.1.12.apk", update.assetName)
    }

    @Test
    fun `latest fork update skips published releases without apk assets`() {
        val update = AppUpdaterRepository.parseLatestForkUpdate(
            """
            [
              {
                "tag_name": "0.1.13",
                "name": "0.1.13",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "source.zip",
                    "browser_download_url": "https://github.com/FreeTeaspoon/NuvioMobile/releases/download/0.1.13/source.zip"
                  }
                ]
              },
              {
                "tag_name": "0.1.12.1",
                "name": "0.1.12.1",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "nuvio-freeteaspoon-full-0.1.12.1.apk",
                    "browser_download_url": "https://github.com/FreeTeaspoon/NuvioMobile/releases/download/0.1.12.1/nuvio-freeteaspoon-full-0.1.12.1.apk"
                  }
                ]
              }
            ]
            """.trimIndent(),
        )

        assertEquals("0.1.12.1", update.tag)
    }

    @Test
    fun `latest fork update chooses highest version instead of newest list item`() {
        val update = AppUpdaterRepository.parseLatestForkUpdate(
            """
            [
              {
                "tag_name": "0.1.11.2",
                "name": "0.1.11.2",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "nuvio-freeteaspoon-full-0.1.11.2.apk",
                    "browser_download_url": "https://github.com/FreeTeaspoon/NuvioMobile/releases/download/0.1.11.2/nuvio-freeteaspoon-full-0.1.11.2.apk"
                  }
                ]
              },
              {
                "tag_name": "0.1.12",
                "name": "0.1.12",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "nuvio-freeteaspoon-full-0.1.12.apk",
                    "browser_download_url": "https://github.com/FreeTeaspoon/NuvioMobile/releases/download/0.1.12/nuvio-freeteaspoon-full-0.1.12.apk"
                  }
                ]
              }
            ]
            """.trimIndent(),
        )

        assertEquals("0.1.12", update.tag)
    }

    @Test
    fun `version comparison handles upstream and fork version segments`() {
        assertTrue(VersionUtils.isRemoteNewer(remote = "0.1.12", local = "0.1.11.1"))
        assertTrue(VersionUtils.isRemoteNewer(remote = "0.1.12.2", local = "0.1.12.1"))
        assertFalse(VersionUtils.isRemoteNewer(remote = "0.1.12", local = "0.1.12.1"))
        assertFalse(VersionUtils.isRemoteNewer(remote = "0.1.12.1", local = "0.1.12.1"))
    }
}
