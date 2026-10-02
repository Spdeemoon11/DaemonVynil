package com.example.ui.vinyl

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import com.example.ui.theme.EditorialDisplayFontFamily
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

    val displayDurationMs = nowPlayingState.durationMs

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
            tiltProvider = { tilt },
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
                        durationMs = displayDurationMs,
                        palette = palette,
                        onSeekRequested = { pos -> viewModel.onSeekRequested(pos) },
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
                        .weight(1.50f, fill = true)
                        .testTag("vinyl_turntable_container"),
                    contentAlignment = Alignment.Center
                ) {
                    TurntableDeckView(
                        viewModel = viewModel,
                        artworkBitmap = artworkBitmap,
                        trackTitle = nowPlayingState.title,
                        trackArtist = nowPlayingState.artist,
                        hasActiveTrack = !nowPlayingState.isEmpty,
                        durationMs = displayDurationMs,
                        palette = palette,
                        onSeekRequested = { pos -> viewModel.onSeekRequested(pos) },
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
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(2.dp))

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
                        .padding(bottom = 16.dp)
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
    durationMs: Long,
    palette: AlbumColorPalette,
    onSeekRequested: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val tilt by viewModel.tiltState.collectAsStateWithLifecycle()
    val swipeDirection by viewModel.swipeDirection.collectAsStateWithLifecycle()
    val swipeTrigger by viewModel.swipeTrigger.collectAsStateWithLifecycle()
    val nowPlayingState by viewModel.nowPlayingState.collectAsStateWithLifecycle()
    val optimisticSeekPos by viewModel.optimisticSeekPositionMs.collectAsStateWithLifecycle()

    // Smooth real-time playback position interpolation localized to TurntableDeckView
    var interpolatedPositionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(nowPlayingState.isPlaying, nowPlayingState.positionMs, nowPlayingState.lastPositionUpdateTime) {
        while (isActive) {
            interpolatedPositionMs = nowPlayingState.calculateCurrentPositionMs()
            delay(100L)
        }
    }

    val displayPositionMs = optimisticSeekPos ?: interpolatedPositionMs
    val playbackProgress = if (durationMs > 0L) {
        (displayPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0.0f, 1.0f)
    } else {
        0.0f
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // ---------------------------------------------------------
        // Section: Rotating Vinyl Disc & Circular Progress Ring
        // Positioned toward left/center, GPU RenderNode rotation via graphicsLayer
        // Supports physical touch-to-pause and swipe-to-change-track gestures
        // ---------------------------------------------------------
        TurntableCanvas(
            currentAngleProvider = { viewModel.rotationAngle.floatValue },
            playbackProgress = playbackProgress,
            tiltX = tilt.first,
            tiltY = tilt.second,
            artworkBitmap = artworkBitmap,
            trackTitle = trackTitle,
            trackArtist = trackArtist,
            palette = palette,
            onSeekFraction = { frac ->
                if (durationMs > 0L) {
                    onSeekRequested((frac * durationMs).toLong())
                }
            },
            onVinylTap = { viewModel.onVinylTapped() },
            onVinylSwipeUp = { viewModel.onVinylSwipeUp() },
            onVinylSwipeDown = { viewModel.onVinylSwipeDown() },
            onPlatterMove = { velocity -> viewModel.onPlatterMoved(velocity) },
            onPlatterTouchEnd = { viewModel.onPlatterTouchEnded() },
            swipeDirection = swipeDirection,
            swipeTrigger = swipeTrigger,
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
            onSeekFraction = { frac ->
                if (durationMs > 0L) {
                    onSeekRequested((frac * durationMs).toLong())
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Cleans a raw media-session track title for UI presentation by completely removing
 * any text contained within parentheses, including multiple and nested parentheses,
 * collapsing redundant whitespace, and trimming dangling punctuation.
 *
 * Examples:
 * - "Jaana Samjho Na (From Bhool Bhulaiyaa 3)" -> "Jaana Samjho Na"
 * - "Song Name (Official Audio) (Remastered)" -> "Song Name"
 * - "Track Name (Live) (2025 Edition)" -> "Track Name"
 * - "Song Name (feat. Artist (Remix))" -> "Song Name"
 */
internal fun cleanDisplayTrackTitle(rawTitle: String): String {
    if (rawTitle.isEmpty()) return rawTitle

    val sb = StringBuilder(rawTitle.length)
    var parenDepth = 0

    for (ch in rawTitle) {
        when (ch) {
            '(' -> parenDepth++
            ')' -> {
                if (parenDepth > 0) parenDepth--
            }
            else -> {
                if (parenDepth == 0) {
                    sb.append(ch)
                }
            }
        }
    }

    // Collapse multiple consecutive spaces and trim leading/trailing whitespace
    var cleaned = sb.toString().replace(Regex("\\s+"), " ").trim()

    // Clean up any dangling trailing punctuation caused by removing a parenthetical suffix
    // e.g. "Track Name - (Remastered)" -> "Track Name - " -> "Track Name"
    cleaned = cleaned.trimEnd(' ', '-', '–', '—', ':', ',', ';', '/', '\\').trim()

    return if (cleaned.isNotEmpty()) {
        cleaned
    } else {
        // If the entire title was inside parentheses, e.g. "(Untitled)", strip parens
        rawTitle.replace("(", "").replace(")", "").trim()
    }
}

/**
 * Track title, artist, and secondary typography cluster.
 * Left-aligned below the vinyl record, strictly adhering to the requested hierarchy:
 * Track title = MASSIVE, distinctive editorial font inspired by TAN Nimbus (58.sp)
 * Artist = smaller, restrained font (16.sp)
 * Secondary metadata = smallest (12.sp)
 * Animated with a subtle, premium fade/slide transition without excessive bounce.
 */
@Composable
private fun TrackMetadataTypography(
    title: String,
    artist: String,
    album: String,
    modifier: Modifier = Modifier
) {
    // Pure presentation-layer cleaning: removes all parenthetical suffixes (e.g. (From Movie), (Official Audio))
    val displayTitle = remember(title) { cleanDisplayTrackTitle(title) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Track title = large, distinctive editorial font (~2x visual size: 58.sp)
        AnimatedContent(
            targetState = displayTitle.ifEmpty { stringResource(R.string.empty_playback_title) },
            transitionSpec = {
                (fadeIn(animationSpec = tween(280)) + slideInVertically(animationSpec = tween(280)) { it / 5 }) togetherWith
                (fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(200)) { -it / 5 })
            },
            label = "TrackTitleTransition"
        ) { targetTitle ->
            Text(
                text = targetTitle,
                fontSize = 58.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = EditorialDisplayFontFamily,
                color = TextPrimary,
                lineHeight = 60.sp,
                letterSpacing = (-1.0).sp,
                textAlign = TextAlign.Start,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("track_title_text")
            )
        }

        // Artist = smaller, restrained font
        AnimatedContent(
            targetState = artist.ifEmpty { stringResource(R.string.empty_playback_subtitle) },
            transitionSpec = {
                (fadeIn(animationSpec = tween(280)) + slideInVertically(animationSpec = tween(280)) { it / 5 }) togetherWith
                (fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(200)) { -it / 5 })
            },
            label = "TrackArtistTransition"
        ) { targetArtist ->
            Text(
                text = targetArtist,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                color = TextSecondary,
                lineHeight = 22.sp,
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
                transitionSpec = {
                    (fadeIn(animationSpec = tween(280)) + slideInVertically(animationSpec = tween(280)) { it / 5 }) togetherWith
                    (fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(200)) { -it / 5 })
                },
                label = "TrackAlbumTransition"
            ) { targetAlbum ->
                Text(
                    text = targetAlbum,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.SansSerif,
                    color = TextSecondary.copy(alpha = 0.65f),
                    lineHeight = 16.sp,
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
