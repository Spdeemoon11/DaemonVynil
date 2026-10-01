package com.example.media

import android.graphics.Bitmap

/**
 * NowPlayingState
 *
 * Immutable data model capturing the complete snapshot of active device audio playback.
 * Decouples the Android platform MediaSession/Notification APIs from the UI rendering layer.
 *
 * @property title The song or track title (e.g. "Aja", "Kind of Blue").
 * @property artist The performing artist or band name.
 * @property album The album or collection name.
 * @property artworkBitmap In-memory decoded bitmap if supplied directly by MediaMetadata.
 * @property artworkUri Remote or local URI string for album artwork (e.g. content:// or https://).
 * @property isPlaying True if the media controller reports active audio playback.
 * @property positionMs The current playback position in milliseconds at [lastPositionUpdateTime].
 * @property durationMs The total duration of the track in milliseconds, or 0 if unknown/stream.
 * @property playbackSpeed The speed multiplier (e.g. 1.0f for normal speed).
 * @property lastPositionUpdateTime System clock time (elapsedRealtime) when [positionMs] was sampled.
 * @property packageName The Android application ID hosting the media session (e.g. "com.spotify.music").
 * @property hasActiveSession True if a real MediaController was detected on the device.
 * @property isNotificationAccessGranted True if the user has enabled notification/media session access in Android settings.
 * @property isAuditionMode True if the user is running the built-in analog groove audition track.
 */
data class NowPlayingState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkBitmap: Bitmap? = null,
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val lastPositionUpdateTime: Long = 0L,
    val packageName: String? = null,
    val hasActiveSession: Boolean = false,
    val isNotificationAccessGranted: Boolean = false,
    val isAuditionMode: Boolean = false
) {
    /**
     * Determines whether the track is in an empty / standby state.
     * When empty, the UI displays a stationary vinyl record with an elegant monochrome center label.
     */
    val isEmpty: Boolean
        get() = !hasActiveSession && !isAuditionMode

    /**
     * Calculates the interpolated real-time playback position in milliseconds
     * based on elapsed wall-clock time since the last state update.
     */
    fun calculateCurrentPositionMs(): Long {
        if (!isPlaying || durationMs <= 0L) return positionMs
        val now = safeClockTime()
        val elapsed = (now - lastPositionUpdateTime) * playbackSpeed
        val estimated = positionMs + elapsed.toLong()
        return estimated.coerceIn(0L, durationMs)
    }

    /**
     * Returns the normalized progress ratio in the range [0.0f, 1.0f].
     */
    fun calculateProgressRatio(): Float {
        if (durationMs <= 0L) return 0.0f
        return (calculateCurrentPositionMs().toFloat() / durationMs.toFloat()).coerceIn(0.0f, 1.0f)
    }

    companion object {
        /**
         * Safely retrieves elapsed real-time or falls back to system clock in non-Android unit tests.
         */
        fun safeClockTime(): Long {
            return try {
                android.os.SystemClock.elapsedRealtime()
            } catch (t: Throwable) {
                System.currentTimeMillis()
            }
        }

        /**
         * Standard standby state when no music is actively playing on the Android device.
         */
        val Standby = NowPlayingState(
            title = "Nothing Playing",
            artist = "Start playback on your device.",
            album = "Stereo Hi-Fi Platter",
            artworkBitmap = null,
            artworkUri = null,
            isPlaying = false,
            positionMs = 0L,
            durationMs = 0L,
            hasActiveSession = false
        )

        /**
         * Built-in classic analog audition groove track for testing and showcasing
         * turntable rotation physics, tone arm motion, and specular lighting in emulator environments.
         */
        val SampleAudition = NowPlayingState(
            title = "Kind of Blue",
            artist = "Miles Davis",
            album = "Columbia Master Sound • 1959",
            artworkBitmap = null,
            artworkUri = null,
            isPlaying = true,
            positionMs = 64_000L,
            durationMs = 562_000L,
            playbackSpeed = 1.0f,
            lastPositionUpdateTime = safeClockTime(),
            hasActiveSession = true,
            isNotificationAccessGranted = true,
            isAuditionMode = true
        )
    }
}
