package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.media.NowPlayingState
import com.example.ui.vinyl.RpmMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VinylRobolectricTest
 *
 * Robolectric unit tests verifying app resources, standby state defaults,
 * and playback progress calculations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun verifyAppNameResource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Vinyl", appName)
    }

    @Test
    fun verifyStandbyStateProperties() {
        val standby = NowPlayingState.Standby
        assertTrue(standby.isEmpty)
        assertFalse(standby.isPlaying)
        assertEquals("Nothing Playing", standby.title)
        assertEquals("Start playback on your device.", standby.artist)
    }

    @Test
    fun verifyProgressRatioCalculation() {
        val playingState = NowPlayingState(
            title = "Aja",
            artist = "Steely Dan",
            album = "Aja",
            isPlaying = true,
            positionMs = 120_000L,
            durationMs = 240_000L,
            playbackSpeed = 1.0f,
            lastPositionUpdateTime = android.os.SystemClock.elapsedRealtime()
        )

        val ratio = playingState.calculateProgressRatio()
        assertEquals(0.5f, ratio, 0.05f)
    }

    @Test
    fun verifyRpmCalculations() {
        assertEquals(200.0f, RpmMode.RPM_33.targetDegreesPerSecond, 0.1f)
        assertEquals(270.0f, RpmMode.RPM_45.targetDegreesPerSecond, 0.1f)
    }

    @Test
    fun verifyVinylTapWhenPlayingPauses() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val mediaBridge = com.example.media.MediaBridgeManager.getInstance(app)
        val vm = com.example.ui.vinyl.VinylPlayerViewModel(app)

        // Start audition playback
        mediaBridge.skipToNext()
        assertTrue(vm.nowPlayingState.value.isPlaying)

        // Tap on vinyl -> must immediately pause
        vm.onVinylTapped()
        assertFalse(vm.nowPlayingState.value.isPlaying)

        // Tap on vinyl when paused -> must remain paused, NEVER resume
        vm.onVinylTapped()
        assertFalse(vm.nowPlayingState.value.isPlaying)
    }

    @Test
    fun verifyVinylSwipeNavigatesTracks() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val mediaBridge = com.example.media.MediaBridgeManager.getInstance(app)
        val vm = com.example.ui.vinyl.VinylPlayerViewModel(app)

        // Swipe UP -> Next Track
        val initialTitle = vm.nowPlayingState.value.title
        vm.onVinylSwipeUp()
        assertEquals(-1, vm.swipeDirection.value)
        val afterNextTitle = vm.nowPlayingState.value.title
        org.junit.Assert.assertNotEquals(initialTitle, afterNextTitle)

        // Swipe DOWN -> Previous Track
        vm.onVinylSwipeDown()
        assertEquals(1, vm.swipeDirection.value)
    }

    @Test
    fun verifyVinylSoundEngineTriggersWithoutCrashing() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val soundEngine = com.example.media.VinylSoundEngine.getInstance(app)

        // Pre-playback needle sound
        soundEngine.playNeedleContactSound()

        // Platter movement scratch sound
        soundEngine.onPlatterMovement(240f)
        soundEngine.onPlatterMovement(1200f)
        soundEngine.onPlatterMovementStopped()

        val vm = com.example.ui.vinyl.VinylPlayerViewModel(app)
        vm.onPlatterMoved(300f)
        vm.onPlatterTouchEnded()
    }
}
