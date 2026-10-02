package com.example.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * VinylSoundEngine
 *
 * Lightweight, high-performance audio engine responsible for authentic physical vinyl sound effects:
 * 1. Pre-Playback Needle Contact Sound:
 *    - Very short, subtle stylus landing contact (~160 ms) + vinyl microgroove friction texture ("shhhk").
 *    - Plays immediately before music becomes audible (volume: -22 dB relative to music).
 * 2. Manual Platter Manipulation Scratch / Rub Sound:
 *    - Subtle granular vinyl surface rubbing texture that dynamically reacts to movement velocity.
 *    - Fades out/stops immediately when platter movement ceases.
 *
 * Performance & Architecture:
 * - Uses low-level static PCM [AudioTrack] in MODE_STATIC with pre-computed 16-bit 44.1kHz samples.
 * - Completely avoids file I/O, disk writes, and MediaCodec/SoundPool decoding pipelines,
 *   preventing "Failed to query component interface for required system resources" errors.
 * - Zero allocations on trigger, hardware-accelerated playback directly via AudioFlinger.
 * - Automatic vinyl rotation during normal playback remains 100% silent.
 */
class VinylSoundEngine private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var needleTrack: AudioTrack? = null
    private var scratchTrack: AudioTrack? = null

    private val needleLock = Any()
    private val scratchLock = Any()

    @Volatile
    private var isInitialized = false

    // Debounce state
    private var lastNeedleTriggerUptimeMs = 0L
    private var lastScratchTriggerUptimeMs = 0L

    init {
        initializeAudioTracks()
    }

    private fun initializeAudioTracks() {
        scope.launch {
            try {
                // Clean up any legacy cached WAV files from previous versions
                try {
                    File(context.cacheDir, "vinyl_needle_contact_v1.wav").delete()
                    File(context.cacheDir, "vinyl_rub_scratch_v1.wav").delete()
                } catch (ignored: Throwable) {}

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                // 1. Synthesize needle contact samples (~160ms)
                val needleSamples = generateNeedleContactSamples(SAMPLE_RATE)
                val needleBufferSize = needleSamples.size * 2

                val nTrack = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(needleBufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                if (nTrack.state == AudioTrack.STATE_INITIALIZED) {
                    nTrack.write(needleSamples, 0, needleSamples.size)
                    synchronized(needleLock) {
                        needleTrack = nTrack
                    }
                }

                // 2. Synthesize platter rub/scratch samples (~90ms)
                val scratchSamples = generateVinylRubSamples(SAMPLE_RATE)
                val scratchBufferSize = scratchSamples.size * 2

                val sTrack = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(scratchBufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                if (sTrack.state == AudioTrack.STATE_INITIALIZED) {
                    sTrack.write(scratchSamples, 0, scratchSamples.size)
                    synchronized(scratchLock) {
                        scratchTrack = sTrack
                    }
                }

                isInitialized = true
                Log.d(TAG, "VinylSoundEngine initialized AudioTracks successfully.")
            } catch (e: Throwable) {
                Log.w(TAG, "AudioTrack initialization failed or not supported in current environment", e)
            }
        }
    }

    /**
     * Triggers the subtle pre-playback needle contact sound immediately before music becomes audible.
     * Duration ~160 ms, volume -22 dB (~0.08f), debounced with 450 ms minimum interval.
     */
    fun playNeedleContactSound() {
        val now = SystemClock.uptimeMillis()
        if (now - lastNeedleTriggerUptimeMs < 450L) {
            return
        }
        lastNeedleTriggerUptimeMs = now

        try {
            synchronized(needleLock) {
                val track = needleTrack ?: return
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    val vol = 0.08f // -22 dB relative to full-scale music
                    track.pause()
                    track.reloadStaticData()
                    track.setVolume(vol)
                    track.play()
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to play needle contact sound", e)
        }
    }

    /**
     * Reports manual physical platter movement from touch drag gestures.
     * Modulates volume and playback rate based on movement velocity.
     *
     * @param velocityPxPerSec Finger drag velocity across the platter in pixels/sec.
     */
    fun onPlatterMovement(velocityPxPerSec: Float) {
        if (velocityPxPerSec < 60f) {
            // Jitter / subpixel drag: quiet
            return
        }

        val now = SystemClock.uptimeMillis()
        val normalizedVelocity = (velocityPxPerSec / 1500f).coerceIn(0.1f, 1.0f)
        // Very quiet rubbing sound (-20 dB to -28 dB)
        val volume = (0.035f + normalizedVelocity * 0.075f).coerceIn(0.035f, 0.11f)
        val targetSampleRate = (SAMPLE_RATE * (0.90f + normalizedVelocity * 0.35f).coerceIn(0.90f, 1.28f)).toInt()

        try {
            synchronized(scratchLock) {
                val track = scratchTrack ?: return
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.setVolume(volume)
                    try {
                        track.playbackRate = targetSampleRate
                    } catch (ignored: Throwable) {}

                    if (now - lastScratchTriggerUptimeMs > 75L || track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        lastScratchTriggerUptimeMs = now
                        track.pause()
                        track.reloadStaticData()
                        track.play()
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to play platter scratch sound", e)
        }
    }

    /**
     * Immediately terminates manual platter scratch feedback when touch or movement ceases.
     * Leaves zero trailing noise.
     */
    fun onPlatterMovementStopped() {
        try {
            synchronized(scratchLock) {
                val track = scratchTrack ?: return
                if (track.state == AudioTrack.STATE_INITIALIZED && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.reloadStaticData()
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to stop scratch sound", e)
        }
    }

    /**
     * Releases audio hardware resources on ViewModel teardown.
     */
    fun release() {
        onPlatterMovementStopped()
        try {
            synchronized(needleLock) {
                needleTrack?.let {
                    try { it.stop() } catch (ignored: Throwable) {}
                    try { it.release() } catch (ignored: Throwable) {}
                }
                needleTrack = null
            }
            synchronized(scratchLock) {
                scratchTrack?.let {
                    try { it.stop() } catch (ignored: Throwable) {}
                    try { it.release() } catch (ignored: Throwable) {}
                }
                scratchTrack = null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error releasing audio tracks", e)
        }
    }

    // =========================================================
    // Pure Mathematical DSP Synthesis for Vinyl Acoustics
    // =========================================================

    /**
     * Synthesizes authentic stylus touchdown sound (~160 ms):
     * 1. Low-frequency mechanical damping (85 Hz sine, 30 ms decay)
     * 2. Microgroove friction texture (2-pole bandpass filtered noise at 1350 Hz, 8ms attack, 55ms decay)
     */
    private fun generateNeedleContactSamples(sampleRate: Int = SAMPLE_RATE): ShortArray {
        val durationSec = 0.16f
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)
        val random = Random(42)

        // 2-pole resonant bandpass filter coefficients at 1350 Hz (Q = 1.8)
        val f0 = 1350.0
        val q = 1.8
        val w0 = 2.0 * PI * f0 / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val b0 = alpha / (1.0 + alpha)
        val b2 = -alpha / (1.0 + alpha)
        val a1 = (-2.0 * cos(w0)) / (1.0 + alpha)
        val a2 = (1.0 - alpha) / (1.0 + alpha)

        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate

            // Stylus landing contact thump
            val thump = sin(2.0 * PI * 85.0 * t).toFloat() * exp(-t / 0.030f) * 0.28f

            // Microgroove noise through bandpass filter
            val xn = (random.nextFloat() * 2f - 1f).toDouble()
            val yn = b0 * xn + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = xn
            y2 = y1
            y1 = yn

            val attack = (t / 0.008f).coerceIn(0f, 1f)
            val decay = exp(-t / 0.055f)
            val texture = yn.toFloat() * attack * decay * 0.45f

            val valSample = (thump + texture).coerceIn(-1.0f, 1.0f)
            samples[i] = (valSample * 32767f).toInt().toShort()
        }

        return samples
    }

    /**
     * Synthesizes granular vinyl surface rubbing texture (~90 ms):
     * 1. 2-pole bandpass filtered noise at 1700 Hz (Q = 2.0)
     * 2. Periodic microgroove ridge modulation (~140 Hz)
     * 3. Smooth attack (5 ms), body (55 ms), decay (20 ms)
     */
    private fun generateVinylRubSamples(sampleRate: Int = SAMPLE_RATE): ShortArray {
        val durationSec = 0.09f
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)
        val random = Random(1337)

        val f0 = 1700.0
        val q = 2.0
        val w0 = 2.0 * PI * f0 / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val b0 = alpha / (1.0 + alpha)
        val b2 = -alpha / (1.0 + alpha)
        val a1 = (-2.0 * cos(w0)) / (1.0 + alpha)
        val a2 = (1.0 - alpha) / (1.0 + alpha)

        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val ridgeMod = 0.70f + 0.30f * sin(2.0 * PI * 140.0 * t).toFloat()
            val xn = ((random.nextFloat() * 2f - 1f) * ridgeMod).toDouble()
            val yn = b0 * xn + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = xn
            y2 = y1
            y1 = yn

            val attack = (t / 0.005f).coerceIn(0f, 1f)
            val decay = exp(-maxOf(0f, t - 0.055f) / 0.020f)
            val valSample = (yn.toFloat() * attack * decay * 0.50f).coerceIn(-1.0f, 1.0f)
            samples[i] = (valSample * 32767f).toInt().toShort()
        }

        return samples
    }

    companion object {
        private const val TAG = "VinylSoundEngine"
        private const val SAMPLE_RATE = 44100

        @Volatile
        private var instance: VinylSoundEngine? = null

        fun getInstance(context: Context): VinylSoundEngine {
            return instance ?: synchronized(this) {
                instance ?: VinylSoundEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}

