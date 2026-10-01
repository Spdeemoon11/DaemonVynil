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
    fun verifyColorExtractorWithBitmap() {
        val bmp = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.BLUE)

        val palette = com.example.media.ColorExtractor.extractPalette(bmp)
        org.junit.Assert.assertNotNull(palette)
        // Dominant tint should capture the blue hue
        org.junit.Assert.assertTrue(palette.dominantTint.blue > palette.dominantTint.red)
        // Vinyl core must remain dominant authentic black vinyl (#101013)
        assertEquals(com.example.media.AlbumColorPalette.DefaultVinylCore, palette.vinylCoreColor)
        // Color wash must be ultra-subtle (alpha <= 0.10f)
        assertTrue(palette.vinylWashColor.alpha <= 0.10f)
    }
}
