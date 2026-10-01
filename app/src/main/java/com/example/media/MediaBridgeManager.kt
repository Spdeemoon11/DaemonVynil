package com.example.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MediaBridgeManager
 *
 * Central engine responsible for discovering active Android MediaSessions,
 * extracting real-time metadata (track, artist, album, artwork, duration, position),
 * listening to playback state transitions, and forwarding transport commands (play, pause, seek, skip).
 *
 * Architecture:
 * - Emits reactive state updates via [state] Flow.
 * - Handles both system [MediaSessionManager] active sessions and notification-extracted sessions.
 * - Gracefully provides an Audition Mode for interactive turntable physics testing
 *   when external music players are inactive or in development/emulator environments.
 */
class MediaBridgeManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private val _state = MutableStateFlow(NowPlayingState.Standby)
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private var activeController: MediaController? = null
    private var isListenerRegistered = false

    // Internal state tracking for Audition mode simulation
    private var auditionState = NowPlayingState.SampleAudition.copy(
        isPlaying = false,
        lastPositionUpdateTime = SystemClock.elapsedRealtime()
    )

    /**
     * Controller callback monitoring playback state changes and metadata updates
     * from the active external media player.
     */
    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            Log.d(TAG, "onPlaybackStateChanged: state=${state?.state}")
            updateFromCurrentController()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            Log.d(TAG, "onMetadataChanged: title=${metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)}")
            updateFromCurrentController()
        }

        override fun onSessionDestroyed() {
            Log.d(TAG, "onSessionDestroyed: active controller destroyed")
            detachActiveController()
            refreshActiveSessions()
        }
    }

    /**
     * Session manager callback monitoring changes in the list of active device media sessions.
     */
    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            Log.d(TAG, "Active sessions changed. Found ${controllers?.size ?: 0} controllers")
            handleActiveSessions(controllers)
        }

    init {
        // Initial check for permission and sessions
        checkAndRefresh()
    }

    /**
     * Refreshes the connection status, verifies notification listener permission,
     * and queries the active media controllers.
     */
    fun checkAndRefresh() {
        val hasPermission = VinylNotificationListenerService.isAccessGranted(context)
        Log.d(TAG, "checkAndRefresh: isNotificationAccessGranted=$hasPermission")

        if (hasPermission) {
            registerSessionsListenerIfNeeded()
            refreshActiveSessions()
        } else {
            unregisterSessionsListenerIfNeeded()
            if (!_state.value.isAuditionMode) {
                _state.value = NowPlayingState.Standby.copy(
                    isNotificationAccessGranted = false
                )
            }
        }
    }

    /**
     * Called when [VinylNotificationListenerService] successfully binds to the system.
     */
    fun onNotificationListenerConnected(service: VinylNotificationListenerService) {
        Log.d(TAG, "Notification listener connected to bridge.")
        registerSessionsListenerIfNeeded()
        refreshActiveSessions()
    }

    /**
     * Called when [VinylNotificationListenerService] unbinds or is disconnected.
     */
    fun onNotificationListenerDisconnected() {
        Log.d(TAG, "Notification listener disconnected from bridge.")
        unregisterSessionsListenerIfNeeded()
        detachActiveController()
        if (!_state.value.isAuditionMode) {
            _state.value = NowPlayingState.Standby.copy(
                isNotificationAccessGranted = false
            )
        }
    }

    /**
     * Called when a media notification with an embedded [MediaSession.Token] is received.
     */
    fun onMediaNotificationDetected(packageName: String, token: MediaSession.Token) {
        try {
            val controller = MediaController(context, token)
            Log.d(TAG, "Created MediaController from notification token: $packageName")
            attachController(controller)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate controller from notification token", e)
        }
    }

    /**
     * Queries [MediaSessionManager] for current active sessions using the listener component.
     */
    fun refreshActiveSessions() {
        if (!VinylNotificationListenerService.isAccessGranted(context)) {
            Log.d(TAG, "Notification listener access not granted yet.")
            return
        }

        try {
            val componentName = VinylNotificationListenerService.getComponentName(context)
            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            handleActiveSessions(controllers)
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException querying active sessions: ${se.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing active sessions", e)
        }
    }

    private fun registerSessionsListenerIfNeeded() {
        if (isListenerRegistered) return
        try {
            val componentName = VinylNotificationListenerService.getComponentName(context)
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                componentName
            )
            isListenerRegistered = true
            Log.d(TAG, "Registered OnActiveSessionsChangedListener successfully.")
        } catch (se: SecurityException) {
            Log.w(TAG, "Cannot register OnActiveSessionsChangedListener without permission.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register OnActiveSessionsChangedListener", e)
        }
    }

    private fun unregisterSessionsListenerIfNeeded() {
        if (!isListenerRegistered) return
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            isListenerRegistered = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister OnActiveSessionsChangedListener", e)
        }
    }

    /**
     * Selects the best active controller from the provided list.
     * Prioritizes players that are actively in STATE_PLAYING.
     */
    private fun handleActiveSessions(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            Log.d(TAG, "No active media controllers found on device.")
            if (!_state.value.isAuditionMode) {
                detachActiveController()
                _state.value = NowPlayingState.Standby.copy(
                    isNotificationAccessGranted = VinylNotificationListenerService.isAccessGranted(context)
                )
            }
            return
        }

        // Find the currently playing controller first, or fallback to the most recent one
        val preferred = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.first()

        attachController(preferred)
    }

    private fun attachController(controller: MediaController) {
        if (activeController?.sessionToken == controller.sessionToken) {
            // Already attached to this session; just refresh its state
            updateFromCurrentController()
            return
        }

        detachActiveController()
        activeController = controller
        try {
            controller.registerCallback(controllerCallback, mainHandler)
            Log.d(TAG, "Attached to controller: ${controller.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register callback on controller", e)
        }
        updateFromCurrentController()
    }

    private fun detachActiveController() {
        try {
            activeController?.unregisterCallback(controllerCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering controller callback", e)
        }
        activeController = null
    }

    /**
     * Extracts all track metadata and playback parameters from the attached controller
     * and updates [_state].
     */
    private fun updateFromCurrentController() {
        val controller = activeController
        if (controller == null) {
            if (!_state.value.isAuditionMode) {
                _state.value = NowPlayingState.Standby.copy(
                    isNotificationAccessGranted = VinylNotificationListenerService.isAccessGranted(context)
                )
            }
            return
        }

        val metadata = controller.metadata
        val playbackState = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "Unknown Track"

        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: "Unknown Artist"

        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?: "Analog Stereo"

        val artworkBitmap: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val artworkUri = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L

        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING ||
                playbackState?.state == PlaybackState.STATE_FAST_FORWARDING ||
                playbackState?.state == PlaybackState.STATE_REWINDING

        val position = playbackState?.position?.coerceAtLeast(0L) ?: 0L
        val speed = playbackState?.playbackSpeed ?: 1.0f
        val lastUpdate = playbackState?.lastPositionUpdateTime?.takeIf { it > 0L }
            ?: SystemClock.elapsedRealtime()

        val newState = NowPlayingState(
            title = title,
            artist = artist,
            album = album,
            artworkBitmap = artworkBitmap,
            artworkUri = artworkUri,
            isPlaying = isPlaying,
            positionMs = position,
            durationMs = duration,
            playbackSpeed = speed,
            lastPositionUpdateTime = lastUpdate,
            packageName = controller.packageName,
            hasActiveSession = true,
            isNotificationAccessGranted = true,
            isAuditionMode = false
        )

        _state.value = newState
    }

    // ==========================================
    // Hardware Transport Controls
    // ==========================================

    /**
     * Toggles playback between Play and Pause on the active media session.
     */
    fun togglePlayPause() {
        if (_state.value.isAuditionMode) {
            toggleAuditionPlayPause()
            return
        }

        val controller = activeController ?: return
        val isCurrentlyPlaying = _state.value.isPlaying
        try {
            if (isCurrentlyPlaying) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle play/pause", e)
        }
    }

    /**
     * Skips to the next track on the active media session.
     */
    fun skipToNext() {
        if (_state.value.isAuditionMode) {
            skipAuditionTrack(forward = true)
            return
        }
        try {
            activeController?.transportControls?.skipToNext()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to skip to next", e)
        }
    }

    /**
     * Skips to the previous track on the active media session.
     */
    fun skipToPrevious() {
        if (_state.value.isAuditionMode) {
            skipAuditionTrack(forward = false)
            return
        }
        try {
            activeController?.transportControls?.skipToPrevious()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to skip to previous", e)
        }
    }

    /**
     * Seeks playback to the specified target position in milliseconds.
     */
    fun seekTo(positionMs: Long) {
        if (_state.value.isAuditionMode) {
            seekAudition(positionMs)
            return
        }
        try {
            activeController?.transportControls?.seekTo(positionMs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek", e)
        }
    }

    // ==========================================
    // Audition Mode (Interactive Turntable Demo)
    // ==========================================

    /**
     * Activates or toggles the built-in analog groove audition track.
     * Allows testing turntable rotation, inertia physics, and lighting.
     */
    fun toggleAuditionMode() {
        val currentlyAudition = _state.value.isAuditionMode
        if (currentlyAudition) {
            // Revert back to real session detection
            auditionState = auditionState.copy(isPlaying = false)
            checkAndRefresh()
        } else {
            // Enter audition mode
            auditionState = auditionState.copy(
                isPlaying = true,
                lastPositionUpdateTime = SystemClock.elapsedRealtime()
            )
            _state.value = auditionState
        }
    }

    private fun toggleAuditionPlayPause() {
        val current = _state.value
        val now = SystemClock.elapsedRealtime()
        val currentPos = current.calculateCurrentPositionMs()

        val updated = current.copy(
            isPlaying = !current.isPlaying,
            positionMs = currentPos,
            lastPositionUpdateTime = now
        )
        auditionState = updated
        _state.value = updated
    }

    private fun skipAuditionTrack(forward: Boolean) {
        val sampleTracks = listOf(
            Triple("Kind of Blue", "Miles Davis", "Columbia Master Sound • 1959"),
            Triple("A Love Supreme", "John Coltrane", "Impulse! Records • 1965"),
            Triple("Time Out", "The Dave Brubeck Quartet", "Columbia Records • 1959"),
            Triple("Moanin'", "Art Blakey & The Jazz Messengers", "Blue Note Records • 1958")
        )

        val currentTitle = _state.value.title
        val currentIndex = sampleTracks.indexOfFirst { it.first == currentTitle }.coerceAtLeast(0)
        val nextIndex = if (forward) {
            (currentIndex + 1) % sampleTracks.size
        } else {
            (currentIndex - 1 + sampleTracks.size) % sampleTracks.size
        }

        val track = sampleTracks[nextIndex]
        val updated = _state.value.copy(
            title = track.first,
            artist = track.second,
            album = track.third,
            positionMs = 0L,
            durationMs = 384_000L,
            isPlaying = true,
            lastPositionUpdateTime = SystemClock.elapsedRealtime()
        )
        auditionState = updated
        _state.value = updated
    }

    private fun seekAudition(positionMs: Long) {
        val updated = _state.value.copy(
            positionMs = positionMs.coerceIn(0L, _state.value.durationMs),
            lastPositionUpdateTime = SystemClock.elapsedRealtime()
        )
        auditionState = updated
        _state.value = updated
    }

    companion object {
        private const val TAG = "MediaBridgeManager"

        @Volatile
        private var INSTANCE: MediaBridgeManager? = null

        fun getInstance(context: Context): MediaBridgeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaBridgeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
