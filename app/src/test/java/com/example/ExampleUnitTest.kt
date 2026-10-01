package com.example

import com.example.media.AlbumColorPalette
import com.example.media.ColorExtractor
import com.example.media.NowPlayingState
import com.example.ui.vinyl.RpmMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vinyl Local Unit Tests
 *
 * Validates core business logic, rotational physics formulas,
 * duration interpolation, and adaptive color extraction with 95% black vinyl dominance.
 */
class ExampleUnitTest {

    @Test
    fun standbyState_hasEmptyAndStandbyFlags() {
        val state = NowPlayingState.Standby
        assertTrue("Standby state should be marked as empty", state.isEmpty)
        assertFalse("Standby state should not be playing", state.isPlaying)
        assertEquals("Standby duration should be 0", 0L, state.durationMs)
        assertEquals("Standby position should be 0", 0L, state.positionMs)
    }

    @Test
    fun sampleAudition_hasProperDefaults() {
        val audition = NowPlayingState.SampleAudition
        assertTrue("Audition mode should be flagged", audition.isAuditionMode)
        assertTrue("Audition should default to playing", audition.isPlaying)
        assertEquals("Kind of Blue", audition.title)
        assertEquals("Miles Davis", audition.artist)
    }

    @Test
    fun progressRatio_handlesZeroDurationGracefully() {
        val state = NowPlayingState(
            title = "Live Radio Stream",
            durationMs = 0L,
            positionMs = 1500L,
            isPlaying = true
        )
        assertEquals("Zero duration streams must yield 0 progress ratio", 0.0f, state.calculateProgressRatio(), 0.001f)
    }

    @Test
    fun progressRatio_clampsWithinBounds() {
        val state = NowPlayingState(
            title = "Track",
            durationMs = 100_000L,
            positionMs = 120_000L, // simulated overshoot
            isPlaying = false
        )
        assertEquals("Progress ratio must be clamped to 1.0f max", 1.0f, state.calculateProgressRatio(), 0.001f)
    }

    @Test
    fun rpmMode_rotationalSpeedFormulas() {
        // Standard vinyl speeds:
        // 33 ⅓ RPM = (100 / 3) * 360 / 60 = 200 degrees / second
        // 45 RPM = 45 * 360 / 60 = 270 degrees / second
        assertEquals(200.0f, RpmMode.RPM_33.targetDegreesPerSecond, 0.001f)
        assertEquals(270.0f, RpmMode.RPM_45.targetDegreesPerSecond, 0.001f)
    }

    @Test
    fun colorExtractor_nullBitmapReturnsMonochromeDefault() {
        val palette = ColorExtractor.extractPalette(null, "", "")
        assertNotNull(palette)
        assertEquals(AlbumColorPalette.MonochromeDefault, palette)
    }

    @Test
    fun colorExtractor_synthesizesAuditionSignaturesWithBlackVinylDominance() {
        val bluePalette = ColorExtractor.extractPalette(null, "Kind of Blue", "Miles Davis")
        assertNotNull(bluePalette)
        assertTrue("Kind of Blue should synthesize blue dominant tint", bluePalette.dominantTint.blue > bluePalette.dominantTint.red)
        assertEquals("Vinyl core must be authentic black vinyl", AlbumColorPalette.DefaultVinylCore, bluePalette.vinylCoreColor)
        assertTrue("Color wash must be ultra-subtle (alpha <= 0.06f)", bluePalette.vinylWashColor.alpha <= 0.06f)
    }
}
