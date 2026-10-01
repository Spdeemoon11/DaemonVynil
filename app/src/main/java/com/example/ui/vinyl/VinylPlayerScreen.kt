package com.example.ui.vinyl

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.media.AlbumColorPalette
import com.example.media.rememberResolvedArtwork
import com.example.media.rememberResolvedPalette
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * VinylPlayerScreen
 *
 * Skeuomorphic turntable interface redesigned to match the primary visual reference image
 * in composition, proportions, spacing, and physical design — transformed into a luxury
 * matte dark graphite / black edition:
 *
 * 1. Reference Image Overall Composition:
 *    - Large vinyl record occupying the upper/central portion of the screen, positioned toward left/center.
 *    - Physical tonearm positioned to the right of the record, tracking playback deterministically.
 *    - Clean, spacious negative space below the record.
 *    - Track title beneath the record (left-aligned, bold, prominent).
 *    - Artist / secondary metadata underneath the title (clean, smaller, muted contrast).
 *    - Tactile pill-shaped playback controls near the bottom (Play on left, Previous/Next on right).
 * 2. Dark Edition Transformation:
 *    - Dark graphite/black physical environment (#0D0D10 to #08080A).
 *    - Dark graphite hardware and tonearm with subtle metallic chamfer highlights.
 *    - Subtle dark contact shadows.
 *    - Very subtle (4.5%) album-art color tint in reflected room light.
 * 3. Restored Minimal Circular Progress Ring:
 *    - Thin (1.2dp) circular progress indicator around the vinyl platter edge.
 *    - The ONLY playback progress visualization, synchronized with `currentPosition / duration`.
 * 4. Deterministic Tonearm Position:
 *    - Position mapped strictly from `currentPosition / duration`.
 *    - 0% = outer groove, 100% = inner dead wax.
 * 5. Stable 60-120 FPS Performance:
 *    - Defer rotation state reading to GPU draw phase; zero 60 FPS Compose recompositions.
 *
 * @param viewModel The backing [VinylPlayerViewModel].
 * @param modifier Composable layout modifier.
 */
@Composable
fun VinylPlayerScreen(
    viewModel: VinylPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nowPlayingState by viewModel.nowPlayingState.collectAsStateWithLifecycle()
    val rpmMode by viewModel.rpmMode.collectAsStateWithLifecycle()
    val volumePercent by viewModel.volumePercent.collectAsStateWithLifecycle()
    val optimisticSeekPos by viewModel.optimisticSeekPositionMs.collectAsStateWithLifecycle()
    val tilt by viewModel.tiltState.collectAsStateWithLifecycle()

    // Resolve album artwork bitmap (cached and downsampled)
    val artworkBitmap = rememberResolvedArtwork(nowPlayingState)

    // Extract adaptive color palette from album artwork or track signature
    val palette = rememberResolvedPalette(
        artworkBitmap = artworkBitmap,
        trackTitle = nowPlayingState.title,
        artist = nowPlayingState.artist
    )

    // Smooth real-time playback position interpolation (updates at 10Hz for progress synchronization)
    var interpolatedPositionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(nowPlayingState.isPlaying, nowPlayingState.positionMs, nowPlayingState.lastPositionUpdateTime) {
        while (isActive) {
            interpolatedPositionMs = nowPlayingState.calculateCurrentPositionMs()
            delay(100L)
        }
    }

    val displayPositionMs = optimisticSeekPos ?: interpolatedPositionMs
    val displayDurationMs = nowPlayingState.durationMs
    val playbackProgress = if (displayDurationMs > 0L) {
        (displayPositionMs.toFloat() / displayDurationMs.toFloat()).coerceIn(0.0f, 1.0f)
    } else {
        0.0f
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val isLandscape = screenWidth > screenHeight

        // ==========================================
        // 1. PHYSICAL MATTE DARK STUDIO BACKDROP
        // ==========================================
        MicaBackdrop(
            palette = palette,
            tiltX = tilt.first,
            tiltY = tilt.second,
            modifier = Modifier.fillMaxSize()
        )

        if (isLandscape) {
            // ==========================================
            // LANDSCAPE MODE: STUDIO CONSOLE WORKSTATION
            // Left: Vinyl Deck with Tonearm. Right: Metadata & Pill Controls.
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Turntable Deck filling up to 96% of screen height
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .testTag("vinyl_turntable_container"),
                    contentAlignment = Alignment.Center
                ) {
                    TurntableDeckView(
                        viewModel = viewModel,
                        artworkBitmap = artworkBitmap,
                        trackTitle = nowPlayingState.title,
                        trackArtist = nowPlayingState.artist,
                        hasActiveTrack = !nowPlayingState.isEmpty,
                        playbackProgress = playbackProgress,
                        palette = palette,
                        onSeekFraction = { frac ->
                            if (displayDurationMs > 0L) {
                                viewModel.onSeekRequested((frac * displayDurationMs).toLong())
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Right: Metadata & Pill Controls
                Column(
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.Start
                ) {
                    // Minimal status bar
                    HardwareStatusBar(
                        isPlaying = nowPlayingState.isPlaying,
                        rpmMode = rpmMode,
                        onToggleSpeedClick = { viewModel.onToggleSpeedClicked() },
                        isAuditionMode = nowPlayingState.isAuditionMode,
                        onToggleAuditionClick = { viewModel.onToggleAuditionClicked() },
                        dominantTint = palette.dominantTint
                    )

                    // Track Metadata (Left-aligned)
                    TrackMetadataTypography(
                        title = nowPlayingState.title,
                        artist = nowPlayingState.artist,
                        album = nowPlayingState.album,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    // Reference Pill Controls
                    MachinedControls(
                        isPlaying = nowPlayingState.isPlaying,
                        onPlayPauseClick = { viewModel.onPlayPauseClicked() },
                        onSkipPreviousClick = { viewModel.onSkipPreviousClicked() },
                        onSkipNextClick = { viewModel.onSkipNextClicked() },
                        rpmMode = rpmMode,
                        onToggleSpeedClick = { viewModel.onToggleSpeedClicked() },
                        volumePercent = volumePercent,
                        onToggleVolumeClick = { viewModel.onToggleVolumeClicked() },
                        isAuditionMode = nowPlayingState.isAuditionMode,
                        onToggleAuditionClick = { viewModel.onToggleAuditionClicked() },
                        palette = palette,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            // ==========================================
            // PORTRAIT MODE: EXACT REFERENCE COMPOSITION
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .widthIn(max = 600.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // -------------------------------------------------
                // 1. Subtle Hardware Status & Permission Prompt
                // -------------------------------------------------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    HardwareStatusBar(
                        isPlaying = nowPlayingState.isPlaying,
                        rpmMode = rpmMode,
                        onToggleSpeedClick = { viewModel.onToggleSpeedClicked() },
                        isAuditionMode = nowPlayingState.isAuditionMode,
                        onToggleAuditionClick = { viewModel.onToggleAuditionClicked() },
                        dominantTint = palette.dominantTint
                    )

                    MediaAccessBanner(
                        isGranted = nowPlayingState.isNotificationAccessGranted,
                        onGrantClick = { viewModel.openNotificationSettings(context) },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                    )
                }

                // -------------------------------------------------
                // 2. Physical Turntable Object (matching reference)
                // Vinyl on left/center + Tonearm on right
                // -------------------------------------------------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.35f, fill = true)
                        .testTag("vinyl_turntable_container"),
                    contentAlignment = Alignment.Center
                ) {
                    TurntableDeckView(
                        viewModel = viewModel,
                        artworkBitmap = artworkBitmap,
                        trackTitle = nowPlayingState.title,
                        trackArtist = nowPlayingState.artist,
                        hasActiveTrack = !nowPlayingState.isEmpty,
                        playbackProgress = playbackProgress,
                        palette = palette,
                        onSeekFraction = { frac ->
                            if (displayDurationMs > 0L) {
                                viewModel.onSeekRequested((frac * displayDurationMs).toLong())
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // -------------------------------------------------
                // 3. Track Metadata Typography (matching reference)
                // Left-aligned underneath the record with generous breathing space
                // -------------------------------------------------
                TrackMetadataTypography(
                    title = nowPlayingState.title,
                    artist = nowPlayingState.artist,
                    album = nowPlayingState.album,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // -------------------------------------------------
                // 4. Tactile Pill Controls (matching reference)
                // Left: Play/Pause pill with label underneath
                // Right: Previous and Next pills with icons underneath
                // -------------------------------------------------
                MachinedControls(
                    isPlaying = nowPlayingState.isPlaying,
                    onPlayPauseClick = { viewModel.onPlayPauseClicked() },
                    onSkipPreviousClick = { viewModel.onSkipPreviousClicked() },
                    onSkipNextClick = { viewModel.onSkipNextClicked() },
                    rpmMode = rpmMode,
                    onToggleSpeedClick = { viewModel.onToggleSpeedClicked() },
                    volumePercent = volumePercent,
                    onToggleVolumeClick = { viewModel.onToggleVolumeClicked() },
                    isAuditionMode = nowPlayingState.isAuditionMode,
                    onToggleAuditionClick = { viewModel.onToggleAuditionClicked() },
                    palette = palette,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                )
            }
        }
    }
}

/**
 * TurntableDeckView
 *
 * Isolated high-performance Composable hosting the rotating vinyl disc and tone arm.
 * By deferring rotation state reads to [TurntableCanvas]'s internal graphicsLayer,
 * neither [VinylPlayerScreen] nor [TurntableDeckView] recompose at 60 FPS,
 * ensuring rock-solid 60-120 FPS frame rate on Android hardware.
 */
@Composable
private fun TurntableDeckView(
    viewModel: VinylPlayerViewModel,
    artworkBitmap: Bitmap?,
    trackTitle: String,
    trackArtist: String,
    hasActiveTrack: Boolean,
    playbackProgress: Float,
    palette: AlbumColorPalette,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // -------------------------------------------------------------
    // Section: State Observations
    // Collect rotation state without triggering composition-phase invalidations.
    // -------------------------------------------------------------
    val currentAngleState = viewModel.currentAngle.collectAsStateWithLifecycle()
    val tilt by viewModel.tiltState.collectAsStateWithLifecycle()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // ---------------------------------------------------------
        // Section: Rotating Vinyl Disc & Circular Progress Ring
        // Positioned toward left/center, GPU RenderNode rotation via graphicsLayer
        // ---------------------------------------------------------
        TurntableCanvas(
            currentAngleProvider = { currentAngleState.value },
            playbackProgress = playbackProgress,
            tiltX = tilt.first,
            tiltY = tilt.second,
            artworkBitmap = artworkBitmap,
            trackTitle = trackTitle,
            trackArtist = trackArtist,
            palette = palette,
            onSeekFraction = onSeekFraction,
            modifier = Modifier.fillMaxSize()
        )

        // ---------------------------------------------------------
        // Section: Audiophile Tone Arm Assembly
        // Positioned on the right side, deterministic tracking from playback progress
        // Supports interactive drag-to-groove seeking
        // ---------------------------------------------------------
        ToneArmOverlay(
            hasActiveTrack = hasActiveTrack,
            progress = playbackProgress,
            tiltX = tilt.first,
            tiltY = tilt.second,
            palette = palette,
            onSeekFraction = onSeekFraction,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Track title, artist, and secondary typography cluster.
 * Left-aligned below the vinyl record, strictly adhering to the requested hierarchy:
 * Track title = largest (28.sp)
 * Artist = medium (18.sp)
 * Secondary metadata = smallest (13.sp)
 */
@Composable
private fun TrackMetadataTypography(
    title: String,
    artist: String,
    album: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Track title = largest
        AnimatedContent(
            targetState = title.ifEmpty { stringResource(R.string.empty_playback_title) },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "TrackTitleTransition"
        ) { targetTitle ->
            Text(
                text = targetTitle,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = TextPrimary,
                lineHeight = 34.sp,
                letterSpacing = (-0.2).sp,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("track_title_text")
            )
        }

        // Artist = medium
        AnimatedContent(
            targetState = artist.ifEmpty { stringResource(R.string.empty_playback_subtitle) },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "TrackArtistTransition"
        ) { targetArtist ->
            Text(
                text = targetArtist,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                color = TextSecondary,
                lineHeight = 24.sp,
                letterSpacing = 0.1.sp,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("track_artist_text")
            )
        }

        // Secondary metadata = smallest
        if (album.isNotEmpty() && album != artist) {
            AnimatedContent(
                targetState = album,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "TrackAlbumTransition"
            ) { targetAlbum ->
                Text(
                    text = targetAlbum,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.SansSerif,
                    color = TextSecondary.copy(alpha = 0.65f),
                    lineHeight = 18.sp,
                    letterSpacing = 0.2.sp,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("track_album_text")
                )
            }
        }
    }
}
