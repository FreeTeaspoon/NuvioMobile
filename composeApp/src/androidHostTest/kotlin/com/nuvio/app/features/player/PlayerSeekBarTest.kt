package com.nuvio.app.features.player

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlayerSeekBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun commitsLatestScrubBeforePlaybackSnapshotUpdates() {
        val changes = mutableListOf<Long>()
        val commits = mutableListOf<Long>()
        val scrubbing = mutableListOf<Boolean>()
        compose.setContent {
            NuvioTheme {
                PlayerSeekBar(
                    durationMs = 120_000L,
                    displayedPositionMs = 10_000L,
                    bufferedPositionMs = 90_000L,
                    metrics = PlayerLayoutMetrics.fromWidth(640.dp),
                    onScrubChange = { changes += it },
                    onScrubFinished = { commits += it },
                    onScrubActiveChanged = { scrubbing += it },
                )
            }
        }

        compose.onNodeWithContentDescription("Playback position")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(75_000f) }

        compose.runOnIdle {
            assertEquals(listOf(75_000L), changes)
            assertEquals(listOf(75_000L), commits)
            assertEquals(listOf(true, false), scrubbing)
        }
    }

    @Test
    fun disablesSeekingUntilDurationIsKnown() {
        compose.setContent {
            NuvioTheme {
                PlayerSeekBar(
                    durationMs = 0L,
                    displayedPositionMs = 0L,
                    metrics = PlayerLayoutMetrics.fromWidth(640.dp),
                    onScrubChange = { error("Seeking must be disabled") },
                    onScrubFinished = { error("Seeking must be disabled") },
                )
            }
        }

        compose.onNodeWithContentDescription("Playback position").assertIsNotEnabled()
    }
}
