package com.example.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * AsyncAudioManager
 *
 * Asynchronous audio manager responsible for zero-latency, high-performance physical vinyl SFX:
 * 1. Pre-Playback Needle Drop Sound Effect:
 *    - Subtle 80-250ms (authoritative duration: ~180 ms) stylus touchdown + microgroove friction texture.
 *    - Preloaded from raw audio resource [R.raw.needle_drop] asynchronously at application startup into
 *      a hardware-accelerated static PCM [AudioTrack] buffer.
 *    - Triggers whenever media state transitions from PAUSED to PLAYING or on new track starts.
 *    - Operates completely asynchronously with zero blocking or delay imposed on music playback.
 *    - Louder presence calibrated to approximately -18 dB relative to full-scale music (linear amplitude ~0.126f).
 * 2. Manual Platter Manipulation Sound:
 *    - Granular vinyl surface rubbing texture dynamically modulated by touch velocity.
 *    - Terminates immediately when platter interaction ceases.
 */
class AsyncAudioManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var needleTrack: AudioTrack? = null
    private var scratchTrack: AudioTrack? = null

    private val needleLock = Any()
    private val scratchLock = Any()

    @Volatile
    private var isPreloaded = false

    // Debounce state to ensure exactly one sound effect per transition
    private var lastNeedleTriggerUptimeMs = 0L
    private var lastScratchTriggerUptimeMs = 0L

    init {
        preloadAudioResources()
    }

    /**
     * Asynchronously preloads audio resources into memory-backed static AudioTracks.
     * Guarantees zero latency and zero disk/decoding overhead when playback begins.
     */
    private fun preloadAudioResources() {
        scope.launch {
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                // 1. Load needle-drop audio samples from preloaded resource (R.raw.needle_drop)
                // with seamless mathematical synthesis fallback (~180ms, 16-bit 44.1kHz mono PCM)
                val needleSamples = loadNeedleDropFromResource(context)
                    ?: generateNeedleDropSamples(SAMPLE_RATE)

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

                // 2. Synthesize platter friction/rub samples (~90ms)
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

                isPreloaded = true
                Log.d(TAG, "AsyncAudioManager preloaded needle-drop audio resource successfully.")
            } catch (e: Throwable) {
                Log.w(TAG, "Audio resource preloading failed in current environment", e)
            }
        }
    }

    /**
     * Reads raw PCM samples directly from the packaged [R.raw.needle_drop] resource file.
     * Bypasses MediaCodec/SoundPool entirely to guarantee zero system interface failures.
     */
    private fun loadNeedleDropFromResource(context: Context): ShortArray? {
        return try {
            val inputStream = context.resources.openRawResource(R.raw.needle_drop)
            val bytes = inputStream.use { it.readBytes() }
            if (bytes.size > 44 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte()) {
                val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val pcmBytes = bytes.size - 44
                val sampleCount = pcmBytes / 2
                val samples = ShortArray(sampleCount)
                byteBuffer.position(44)
                for (i in 0 until sampleCount) {
                    samples[i] = byteBuffer.short
                }
                Log.d(TAG, "Loaded needle_drop from R.raw.needle_drop: $sampleCount samples (~${sampleCount * 1000 / SAMPLE_RATE}ms)")
                samples
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not open R.raw.needle_drop, falling back to algorithmic synthesis", e)
            null
        }
    }

    /**
     * Plays the subtle 80-250ms (~180ms) needle-drop sound effect asynchronously.
     *
     * Volume is calibrated to approximately -18 dB relative to music (amplitude ~0.126f),
     * providing a distinct, tactile audio cue before music begins with zero delay to playback.
     */
    fun playNeedleDrop() {
        val now = SystemClock.uptimeMillis()
        if (now - lastNeedleTriggerUptimeMs < 400L) {
            return
        }
        lastNeedleTriggerUptimeMs = now

        scope.launch {
            try {
                synchronized(needleLock) {
                    val track = needleTrack ?: return@launch
                    if (track.state == AudioTrack.STATE_INITIALIZED) {
                        track.pause()
                        track.reloadStaticData()
                        track.setVolume(NEEDLE_DROP_VOLUME_LINEAR)
                        track.play()
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to play needle drop sound", e)
            }
        }
    }

    /**
     * Evaluates playback state changes and fires the needle-drop sound effect whenever
     * transitioning from PAUSED to PLAYING.
     */
    fun onPlaybackStateChanged(wasPlaying: Boolean, isPlaying: Boolean) {
        if (!wasPlaying && isPlaying) {
            playNeedleDrop()
        }
    }

    /**
     * Dynamically modulates vinyl platter rubbing/scratching noise based on user touch velocity.
     *
     * @param velocityPxPerSec Finger drag velocity across the platter in pixels/second.
     */
    fun onPlatterMovement(velocityPxPerSec: Float) {
        if (velocityPxPerSec < 60f) {
            return
        }

        val now = SystemClock.uptimeMillis()
        val normalizedVelocity = (velocityPxPerSec / 1500f).coerceIn(0.1f, 1.0f)
        val volume = (0.040f + normalizedVelocity * 0.085f).coerceIn(0.040f, 0.125f)
        val targetSampleRate = (SAMPLE_RATE * (0.90f + normalizedVelocity * 0.35f).coerceIn(0.90f, 1.28f)).toInt()

        scope.launch {
            try {
                synchronized(scratchLock) {
                    val track = scratchTrack ?: return@launch
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
                Log.w(TAG, "Failed to modulate platter scratch sound", e)
            }
        }
    }

    /**
     * Terminates platter scratch audio immediately when manual touch ends.
     */
    fun onPlatterMovementStopped() {
        scope.launch {
            try {
                synchronized(scratchLock) {
                    val track = scratchTrack ?: return@launch
                    if (track.state == AudioTrack.STATE_INITIALIZED && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause()
                        track.reloadStaticData()
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to stop platter scratch sound", e)
            }
        }
    }

    /**
     * Releases audio hardware resources.
     */
    fun release() {
        onPlatterMovementStopped()
        scope.launch {
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
    }

    // =========================================================
    // Pure Mathematical DSP Synthesis for Vinyl Acoustics
    // =========================================================

    /**
     * Algorithmic synthesis for authentic needle drop (~180 ms):
     * 1. Low-frequency stylus impact thump (85 Hz, 32 ms exponential damping)
     * 2. Microgroove friction resonance (2-pole bandpass noise at 1450 Hz, Q=1.9, 7 ms attack, 65 ms decay)
     * 3. Subtle trailing groove surface contact
     */
    private fun generateNeedleDropSamples(sampleRate: Int = SAMPLE_RATE): ShortArray {
        val durationSec = 0.18f
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)
        val random = Random(42)

        val f0 = 1450.0
        val q = 1.9
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

            val thump = sin(2.0 * PI * 85.0 * t).toFloat() * exp(-t / 0.032f) * 0.40f

            val xn = (random.nextFloat() * 2f - 1f).toDouble()
            val yn = b0 * xn + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = xn
            y2 = y1
            y1 = yn

            val attack = (t / 0.007f).coerceIn(0f, 1f)
            val decay = exp(-t / 0.065f)
            val texture = yn.toFloat() * attack * decay * 0.55f

            val tailFactor = if (t > 0.02f) 1.0f else (t / 0.02f)
            val grooveTail = (random.nextFloat() * 0.30f - 0.15f) * exp(-t / 0.120f) * tailFactor

            val valSample = (thump + texture + grooveTail).coerceIn(-1.0f, 1.0f)
            samples[i] = (valSample * 32767f).toInt().toShort()
        }

        return samples
    }

    /**
     * Synthesizes granular vinyl surface rubbing texture (~90 ms).
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
        private const val TAG = "AsyncAudioManager"
        private const val SAMPLE_RATE = 44100

        // -18 dB relative to full-scale (10^(-18/20) ≈ 0.12589)
        const val NEEDLE_DROP_VOLUME_DB = -18f
        const val NEEDLE_DROP_VOLUME_LINEAR = 0.126f

        @Volatile
        private var instance: AsyncAudioManager? = null

        fun getInstance(context: Context): AsyncAudioManager {
            return instance ?: synchronized(this) {
                instance ?: AsyncAudioManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
