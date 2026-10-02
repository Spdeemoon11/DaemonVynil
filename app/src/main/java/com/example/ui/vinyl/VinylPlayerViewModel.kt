package com.example.ui.vinyl

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Choreographer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.media.MediaBridgeManager
import com.example.media.NowPlayingState
import com.example.media.VinylNotificationListenerService
import com.example.media.VinylSoundEngine
import com.example.sensor.DeviceTiltSensor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Vinyl Turntable Speed Mode
 * Exact five physical RPM settings in order:
 * 13⅓ RPM -> 23⅓ RPM -> 33⅓ RPM -> 45 RPM -> 52⅔ RPM (wraps back to 13⅓ RPM).
 * Visual turntable speed is strictly proportional to selected RPM:
 * targetDegreesPerSecond = RPM * 360 / 60 = RPM * 6.
 * 33⅓ RPM is the normal/default turntable speed.
 */
enum class RpmMode(
    val displayName: String,
    val targetDegreesPerSecond: Float,
    val indicatorAngleDeg: Float
) {
    RPM_13("13 ⅓", 80.0f, -54f),    // (40/3) * 6 = 80.0 deg/sec
    RPM_23("23 ⅓", 140.0f, -27f),   // (70/3) * 6 = 140.0 deg/sec
    RPM_33("33 ⅓", 200.0f, 0f),     // Default normal turntable speed (100/3) * 6 = 200.0 deg/sec
    RPM_45("45", 270.0f, 27f),       // 45 * 6 = 270.0 deg/sec
    RPM_52("52 ⅔", 316.0f, 54f);    // (158/3) * 6 = 316.0 deg/sec

    fun next(): RpmMode {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

/**
 * VinylPlayerViewModel
 *
 * Coordinates real-time media session observation, physical rotational inertia calculations,
 * device gyroscope parallax inputs, and transport commands.
 *
 * Physical Rotational Inertia Engine:
 * - Angular velocity (omega in deg/sec) with physical flywheel mass and bearing friction
 * - Continuous angle progression (theta) with GPU matrix hardware transforms
 * - Deterministic tone arm tracking mapped from song completion percentage
 * - CPU Throttle: Automatically sleeps when platter is stationary to conserve battery and CPU
 */
class VinylPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaBridge = MediaBridgeManager.getInstance(application)
    private val tiltSensor = DeviceTiltSensor(application)
    private val vinylSoundEngine = VinylSoundEngine.getInstance(application)

    // Direct stream of media session playback state
    val nowPlayingState: StateFlow<NowPlayingState> = mediaBridge.state

    // Normalized device tilt coordinates for optical parallax
    val tiltState: StateFlow<Pair<Float, Float>> = tiltSensor.tilt

    // Rotational physics state
    val rotationAngle = mutableFloatStateOf(0.0f)

    private val _currentAngle = MutableStateFlow(0.0f)
    val currentAngle: StateFlow<Float> = _currentAngle.asStateFlow()

    private val _currentAngularVelocity = MutableStateFlow(0.0f)
    val currentAngularVelocity: StateFlow<Float> = _currentAngularVelocity.asStateFlow()

    // Current RPM switch setting (33 ⅓ or 45 RPM)
    private val _rpmMode = MutableStateFlow(RpmMode.RPM_33)
    val rpmMode: StateFlow<RpmMode> = _rpmMode.asStateFlow()

    // System Media Volume Integration (0% to 100% in 10% discrete increments)
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val _volumePercent = MutableStateFlow(readCurrentVolumePercent())
    val volumePercent: StateFlow<Int> = _volumePercent.asStateFlow()

    // Optimistic seek position to provide instantaneous UI & tonearm response
    private val _optimisticSeekPositionMs = MutableStateFlow<Long?>(null)
    val optimisticSeekPositionMs: StateFlow<Long?> = _optimisticSeekPositionMs.asStateFlow()

    // Tone arm needle tracking angle (smooth progression from outer lead-in groove to inner dead wax)
    private val _toneArmProgress = MutableStateFlow(0.0f)
    val toneArmProgress: StateFlow<Float> = _toneArmProgress.asStateFlow()

    // Vinyl Touch Gestures & Physical Feedback
    private val _swipeDirection = MutableStateFlow(0) // -1 for Up (Next), +1 for Down (Previous)
    val swipeDirection: StateFlow<Int> = _swipeDirection.asStateFlow()

    private val _swipeTrigger = MutableStateFlow(0L)
    val swipeTrigger: StateFlow<Long> = _swipeTrigger.asStateFlow()

    private val _transientOmegaBoost = MutableStateFlow(0.0f)

    // Physical haptics vibrator
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // Physical simulation state (zero allocations in frame loop)
    private var isPhysicsRunning = false
    private var lastTimeNanos = 0L
    private var currentTheta = 0.0f
    private var currentOmega = 0.0f
    private val spinUpAcceleration = 340.0f // deg / s^2 (takes ~0.6s to reach 200 deg/s)
    private val frictionDecayRate = 1.6f     // exponential deceleration damping factor

    private val physicsFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val isPlaying = nowPlayingState.value.isPlaying
            val targetOmega = if (isPlaying) _rpmMode.value.targetDegreesPerSecond else 0.0f
            val hasBoost = _transientOmegaBoost.value > 0.0f

            if (currentOmega > 0.0f || isPlaying || hasBoost) {
                if (lastTimeNanos == 0L) {
                    lastTimeNanos = frameTimeNanos
                }
                val deltaSeconds = ((frameTimeNanos - lastTimeNanos) / 1_000_000_000.0f).coerceIn(0.001f, 0.035f)
                lastTimeNanos = frameTimeNanos

                val boost = _transientOmegaBoost.value
                if (boost > 0.0f) {
                    currentOmega = (currentOmega + boost).coerceAtMost(360.0f)
                    _transientOmegaBoost.value = 0.0f
                }

                if (isPlaying) {
                    if (currentOmega < targetOmega) {
                        currentOmega = (currentOmega + spinUpAcceleration * deltaSeconds).coerceAtMost(targetOmega)
                    } else if (currentOmega > targetOmega) {
                        currentOmega = (currentOmega - spinUpAcceleration * deltaSeconds).coerceAtLeast(targetOmega)
                    }
                } else {
                    currentOmega *= (1.0f - frictionDecayRate * deltaSeconds).coerceIn(0.0f, 1.0f)
                    if (currentOmega < 0.5f) {
                        currentOmega = 0.0f
                    }
                }

                currentTheta = (currentTheta + currentOmega * deltaSeconds) % 360.0f
                rotationAngle.floatValue = currentTheta
                if (_currentAngle.value != currentTheta) {
                    _currentAngle.value = currentTheta
                }
                if (_currentAngularVelocity.value != currentOmega) {
                    _currentAngularVelocity.value = currentOmega
                }

                // Continuously post next VSYNC frame without any object allocation
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                lastTimeNanos = 0L
                if (_currentAngularVelocity.value > 0.0f) {
                    _currentAngularVelocity.value = 0.0f
                }
                isPhysicsRunning = false
            }
        }
    }

    private fun ensurePhysicsRunning() {
        if (!isPhysicsRunning) {
            isPhysicsRunning = true
            lastTimeNanos = 0L
            Choreographer.getInstance().postFrameCallback(physicsFrameCallback)
        }
    }

    init {
        // Start device tilt sensor
        tiltSensor.start()

        // Launch observers to awaken the zero-allocation physics loop on state changes
        // and trigger the subtle pre-playback needle contact sound on playback transition
        viewModelScope.launch {
            var wasPlaying = false
            var lastTrackIdentity = ""

            nowPlayingState.collect { state ->
                val currentTrackIdentity = "${state.title}|${state.artist}"
                val isNowPlaying = state.isPlaying && !state.isEmpty

                if (isNowPlaying) {
                    ensurePhysicsRunning()
                    val isTrackTransition = wasPlaying && currentTrackIdentity.isNotEmpty() && currentTrackIdentity != lastTrackIdentity
                    val isResume = !wasPlaying

                    if (isTrackTransition || isResume) {
                        vinylSoundEngine.playNeedleContactSound()
                    }
                }

                wasPlaying = state.isPlaying
                if (state.title.isNotEmpty()) {
                    lastTrackIdentity = currentTrackIdentity
                }
            }
        }
        viewModelScope.launch {
            _rpmMode.collect {
                if (nowPlayingState.value.isPlaying) {
                    ensurePhysicsRunning()
                }
            }
        }

        // Initial launch check
        ensurePhysicsRunning()
    }

    override fun onCleared() {
        super.onCleared()
        tiltSensor.stop()
        Choreographer.getInstance().removeFrameCallback(physicsFrameCallback)
        vinylSoundEngine.release()
    }

    /**
     * Resumes sensor tracking and refreshes active media sessions when the app returns to foreground.
     */
    fun onResume() {
        tiltSensor.start()
        mediaBridge.checkAndRefresh()
        if (nowPlayingState.value.isPlaying || currentOmega > 0f) {
            ensurePhysicsRunning()
        }
    }

    /**
     * Pauses sensor tracking to preserve battery when the app is in background.
     */
    fun onPause() {
        tiltSensor.stop()
    }

    // ==========================================
    // Tactile Hardware Controls
    // ==========================================

    /**
     * Touching/tapping the physical vinyl disc:
     * If currently playing -> immediately pause playback.
     * If currently paused -> remain paused (never resume).
     */
    fun onVinylTapped() {
        if (nowPlayingState.value.isPlaying) {
            triggerTactileHaptic()
            mediaBridge.pause()
        }
    }

    /**
     * Swiping UP on the physical vinyl disc (deltaY < -80dp):
     * Triggers next track with a physical rotational acceleration and feedback animation.
     */
    fun onVinylSwipeUp() {
        triggerTactileHaptic()
        _swipeDirection.value = -1
        _swipeTrigger.value = SystemClock.uptimeMillis()
        _transientOmegaBoost.value = 85.0f
        ensurePhysicsRunning()
        mediaBridge.skipToNext()
    }

    /**
     * Swiping DOWN on the physical vinyl disc (deltaY > +80dp):
     * Triggers previous track with a physical rotational acceleration and feedback animation.
     */
    fun onVinylSwipeDown() {
        triggerTactileHaptic()
        _swipeDirection.value = 1
        _swipeTrigger.value = SystemClock.uptimeMillis()
        _transientOmegaBoost.value = 85.0f
        ensurePhysicsRunning()
        mediaBridge.skipToPrevious()
    }

    /**
     * Toggles playback between Play and Pause with tactile haptic feedback.
     * When starting/resuming playback, immediately fires the subtle needle-contact SFX.
     */
    fun onPlayPauseClicked() {
        triggerTactileHaptic()
        if (!nowPlayingState.value.isPlaying) {
            vinylSoundEngine.playNeedleContactSound()
        }
        mediaBridge.togglePlayPause()
    }

    /**
     * Called when the user physically touches and drags on the vinyl platter.
     * Modulates subtle organic vinyl rubbing/scratching noise according to drag velocity.
     */
    fun onPlatterMoved(velocityPxPerSec: Float) {
        vinylSoundEngine.onPlatterMovement(velocityPxPerSec)
    }

    /**
     * Called when manual platter interaction ends.
     * Immediately terminates scratch audio without trailing noise.
     */
    fun onPlatterTouchEnded() {
        vinylSoundEngine.onPlatterMovementStopped()
    }

    /**
     * Skips to the next track.
     */
    fun onSkipNextClicked() {
        triggerTactileHaptic()
        mediaBridge.skipToNext()
    }

    /**
     * Skips to the previous track.
     */
    fun onSkipPreviousClicked() {
        triggerTactileHaptic()
        mediaBridge.skipToPrevious()
    }

    /**
     * Advances to the next RPM setting in the exact physical sequence:
     * 13⅓ RPM -> 23⅓ RPM -> 33⅓ RPM -> 45 RPM -> 52⅔ RPM -> wraps back to 13⅓ RPM.
     */
    fun onToggleSpeedClicked() {
        triggerTactileHaptic()
        _rpmMode.value = _rpmMode.value.next()
    }

    /**
     * Reads the current system/media volume and rounds it to the nearest 10% increment.
     */
    private fun readCurrentVolumePercent(): Int {
        return try {
            val am = audioManager ?: return 50
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (max <= 0) return 50
            val fraction = current.toFloat() / max.toFloat()
            (kotlin.math.round(fraction * 10f).toInt() * 10).coerceIn(0, 100)
        } catch (e: Exception) {
            50
        }
    }

    /**
     * Advances the volume by 10% increments on each click:
     * 0% -> 10% -> 20% -> ... -> 100% -> wraps to 0%.
     * Instantly sets the media volume using standard Android AudioManager APIs without popups.
     */
    fun onToggleVolumeClicked() {
        triggerTactileHaptic()
        val next = (_volumePercent.value + 10) % 110 // wraps 100 -> 0
        _volumePercent.value = next
        try {
            val am = audioManager ?: return
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max > 0) {
                val targetStreamVol = kotlin.math.round((next / 100f) * max).toInt().coerceIn(0, max)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetStreamVol, 0)
            }
        } catch (e: Exception) {
            // Gracefully ignore if volume adjustment is restricted
        }
    }

    /**
     * Toggles the built-in analog groove audition track.
     */
    fun onToggleAuditionClicked() {
        triggerTactileHaptic()
        mediaBridge.toggleAuditionMode()
    }

    /**
     * Direct seeking invoked from circular progress indicator tap/drag.
     */
    fun onSeekRequested(positionMs: Long) {
        _optimisticSeekPositionMs.value = positionMs
        triggerTactileHaptic()
        mediaBridge.seekTo(positionMs)
        viewModelScope.launch {
            delay(500)
            _optimisticSeekPositionMs.value = null
        }
    }

    /**
     * Directs the user to Android's Notification Access settings to enable the Media Bridge.
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = VinylNotificationListenerService.createSettingsIntent()
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Produces a subtle, machined tactile click haptic response.
     */
    private fun triggerTactileHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(15L)
            }
        } catch (e: Exception) {
            // Gracefully ignore if vibrator is unavailable
        }
    }
}
