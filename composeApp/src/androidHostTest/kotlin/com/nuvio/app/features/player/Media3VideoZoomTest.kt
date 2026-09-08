package com.nuvio.app.features.player

import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Media3VideoZoomTest {
    @Test
    fun zoomScalesTheVideoAndLeavesSubtitlesAtOriginalSize() {
        val playerView = PlayerView(RuntimeEnvironment.getApplication())
        playerView.measure(exactly(1920), exactly(1080))
        playerView.layout(0, 0, 1920, 1080)
        val surface = assertNotNull(playerView.videoSurfaceView)
        val subtitles = assertNotNull(playerView.subtitleView)

        playerView.applyVideoZoom(1f)
        assertEquals(2f, surface.scaleX)
        assertEquals(2f, surface.scaleY)
        assertEquals(1f, subtitles.scaleX)
        assertEquals(1f, subtitles.scaleY)
        assertFalse((surface.parent as ViewGroup).clipChildren)
        assertTrue(playerView.clipChildren)

        playerView.applyVideoZoom(0f)
        assertEquals(1f, surface.scaleX)
        assertEquals(1f, surface.scaleY)
    }

    @Test
    fun zoomStaysCenteredWhenTheVideoSurfaceChangesSize() {
        val surface = View(RuntimeEnvironment.getApplication())
        surface.layout(0, 0, 1920, 1080)
        surface.applyCenteredVideoZoom(1f)
        assertEquals(960f, surface.pivotX)
        assertEquals(540f, surface.pivotY)

        surface.layout(0, 0, 1280, 720)
        assertEquals(640f, surface.pivotX)
        assertEquals(360f, surface.pivotY)
        assertEquals(2f, surface.scaleX)
        assertEquals(0f, surface.translationX)
        assertEquals(0f, surface.translationY)
    }

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
}
