package com.example.ui.vinyl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.media.AlbumColorPalette
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * MachinedControls
 *
 * Skeuomorphic hardware control cluster strictly matching the primary reference image:
 *
 * 1. Physical Pill Button Architecture (matching reference):
 *    - Left Side: Tactile pill capsule button with text label "PLAY" / "PAUSE" printed underneath.
 *    - Right Side: Two tactile pill capsule buttons placed side-by-side with Previous (⏮) and Next (⏭)
 *      printed directly underneath.
 * 2. Dark Edition Materials:
 *    - Matte dark graphite / charcoal body (#1E1E22 to #141417).
 *    - Subtle metallic chamfered borders capturing overhead studio lighting.
 *    - Soft physical contact shadows cast onto the deck surface.
 *    - Tactile spring depression on press (scale compression, shadow collapse).
 * 3. Preserved Audio Hardware Capabilities:
 *    - RPM Speed selector (33 ⅓ / 45 RPM) and Audition selector (LIVE / DEMO) cleanly integrated.
 */
@Composable
fun MachinedControls(
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    rpmMode: RpmMode,
    onToggleSpeedClick: () -> Unit,
    volumePercent: Int = 50,
    onToggleVolumeClick: () -> Unit = {},
    isAuditionMode: Boolean,
    onToggleAuditionClick: () -> Unit,
    palette: AlbumColorPalette = AlbumColorPalette.MonochromeDefault,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------------------------------------------------------
        // Section: Primary Transport Cluster
        // Left: Play/Pause capsule button with label underneath
        // Center: Tactile Physical Control Dials (RPM & Volume)
        // Right: Previous and Next capsule buttons with icons underneath
        // ---------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left: Pill Play/Pause button with label underneath
            PillPlayPauseButton(
                isPlaying = isPlaying,
                palette = palette,
                onClick = onPlayPauseClick,
                testTag = "play_pause_button"
            )

            // Center: Dual Physical Hardware Dials (RPM & Volume)
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Tactile Physical RPM Control Knob (Click-Only)
                PhysicalRpmKnob(
                    rpmMode = rpmMode,
                    palette = palette,
                    onClick = onToggleSpeedClick,
                    testTag = "rpm_knob_button"
                )

                // Tactile Physical Volume Control Knob (Click-Only)
                PhysicalVolumeKnob(
                    volumePercent = volumePercent,
                    palette = palette,
                    onClick = onToggleVolumeClick,
                    testTag = "volume_knob_button"
                )
            }

            // Right: Previous and Next pill buttons side-by-side
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Previous Pill Button
                PillSkipButton(
                    isNext = false,
                    contentDescription = stringResource(R.string.cd_skip_previous),
                    onClick = onSkipPreviousClick,
                    testTag = "skip_previous_button"
                )

                // Next Pill Button
                PillSkipButton(
                    isNext = true,
                    contentDescription = stringResource(R.string.cd_skip_next),
                    onClick = onSkipNextClick,
                    testTag = "skip_next_button"
                )
            }
        }
    }
}

/**
 * Tactile Pill Play/Pause Button matching the reference image.
 * Capsule shape with "PLAY" / "PAUSE" typography underneath.
 */
@Composable
fun PillPlayPauseButton(
    isPlaying: Boolean,
    palette: AlbumColorPalette,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile physical depression on press
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1.0f,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 450f),
        label = "PillButtonPress"
    )

    val elevationDp = if (isPressed) 2.dp else 7.dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Physical Pill Button
        Box(
            modifier = Modifier
                .width(76.dp)
                .height(36.dp)
                .scale(animatedScale)
                .shadow(
                    elevation = elevationDp,
                    shape = RoundedCornerShape(percent = 50),
                    ambientColor = Color(0x99000000),
                    spotColor = Color(0xDD000000)
                )
                .clip(RoundedCornerShape(percent = 50))
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = if (isPressed) {
                            listOf(Color(0xFF26262B), Color(0xFF383840))
                        } else {
                            listOf(Color(0xFF4C4C55), Color(0xFF24242A), Color(0xFF36363E))
                        }
                    ),
                    shape = RoundedCornerShape(percent = 50)
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isPressed) {
                            listOf(Color(0xFF151518), Color(0xFF101012))
                        } else {
                            listOf(Color(0xFF26262C), Color(0xFF1C1C20), Color(0xFF161619))
                        }
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            // Subtle top highlight catch reflection
            Box(
                modifier = Modifier
                    .width(62.dp)
                    .height(2.dp)
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .background(Color(0x20FFFFFF), shape = CircleShape)
            )

            // Very subtle ambient optical gleam when active
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 16.dp)
                        .background(palette.ambientGlow.copy(alpha = 0.12f), shape = CircleShape)
                )
            }
        }

        // Clean typography label underneath (matching reference image)
        Text(
            text = if (isPlaying) "PAUSE" else "PLAY",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            color = if (isPressed) TextPrimary else TextSecondary,
            letterSpacing = 2.0.sp
        )
    }
}

/**
 * Tactile Pill Skip Button (Previous / Next) matching the reference image.
 * Capsule shape with ⏮ or ⏭ icon underneath.
 */
@Composable
fun PillSkipButton(
    isNext: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1.0f,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 450f),
        label = "PillSkipPress"
    )

    val elevationDp = if (isPressed) 2.dp else 6.dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Physical Pill Button
        Box(
            modifier = Modifier
                .width(54.dp)
                .height(36.dp)
                .scale(animatedScale)
                .shadow(
                    elevation = elevationDp,
                    shape = RoundedCornerShape(percent = 50),
                    ambientColor = Color(0x88000000),
                    spotColor = Color(0xCC000000)
                )
                .clip(RoundedCornerShape(percent = 50))
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = if (isPressed) {
                            listOf(Color(0xFF242429), Color(0xFF34343C))
                        } else {
                            listOf(Color(0xFF484852), Color(0xFF222228), Color(0xFF32323A))
                        }
                    ),
                    shape = RoundedCornerShape(percent = 50)
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isPressed) {
                            listOf(Color(0xFF141416), Color(0xFF0F0F11))
                        } else {
                            listOf(Color(0xFF24242A), Color(0xFF1B1B1F), Color(0xFF141417))
                        }
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            // Top specular catch highlight
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(2.dp)
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .background(Color(0x1AFFFFFF), shape = CircleShape)
            )
        }

        // Icon underneath (matching reference image)
        Icon(
            imageVector = if (isNext) Icons.Default.SkipNext else Icons.Default.SkipPrevious,
            contentDescription = contentDescription,
            tint = if (isPressed) TextPrimary else TextSecondary,
            modifier = Modifier.size(19.dp)
        )
    }
}

/**
 * Physical RPM Control Knob
 *
 * Real small hardware control viewed from directly above:
 * - Dark black/graphite machined material (#26262C to #121215)
 * - Subtle edge highlight ring and machined metallic bevel
 * - Small contact shadow cast downwards for realistic physical depth
 * - Recessed pointer notch indicating the active RPM setting
 * - 5 perimeter tick markings for the exact 5 settings:
 *   13⅓ RPM, 23⅓ RPM, 33⅓ RPM, 45 RPM, 52⅔ RPM
 * - Subtle readout underneath indicating the currently selected RPM
 * - Strictly CLICK-ONLY (advances on each click, no dragging, no swiping, no slider)
 */
@Composable
fun PhysicalRpmKnob(
    rpmMode: RpmMode,
    palette: AlbumColorPalette,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile physical click depression
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1.0f,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 450f),
        label = "RpmKnobPress"
    )

    // Smooth physical rotation of the pointer notch to the target RPM angle
    val animatedAngle by animateFloatAsState(
        targetValue = rpmMode.indicatorAngleDeg,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
        label = "RpmKnobAngle"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Physical Hardware Knob
        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(animatedScale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val center = Offset(cx, cy)
                val knobRadius = 18.dp.toPx()

                // 1. Small soft contact shadow cast downwards
                drawCircle(
                    color = Color(0x65000000),
                    radius = knobRadius + 1.5.dp.toPx(),
                    center = Offset(cx, cy + 3.dp.toPx())
                )

                // 2. Outer beveled graphite bezel
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF383842),
                            Color(0xFF202026),
                            Color(0xFF141418)
                        ),
                        startY = cy - knobRadius,
                        endY = cy + knobRadius
                    ),
                    radius = knobRadius,
                    center = center
                )

                // 3. Crisp metallic edge highlight ring
                drawCircle(
                    color = Color(0x35FFFFFF),
                    radius = knobRadius - 0.6.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.0.dp.toPx())
                )

                // 4. Cylindrical knob top face (dark obsidian graphite with slight upper highlight)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF282830),
                            Color(0xFF1B1B20),
                            Color(0xFF121215)
                        ),
                        center = Offset(cx, cy - 2.dp.toPx()),
                        radius = knobRadius - 2.dp.toPx()
                    ),
                    radius = knobRadius - 2.dp.toPx(),
                    center = center
                )

                // 5. Subtle inner circular machining groove
                drawCircle(
                    color = Color(0x18FFFFFF),
                    radius = (knobRadius - 2.dp.toPx()) * 0.70f,
                    center = center,
                    style = Stroke(width = 0.8.dp.toPx())
                )

                // 6. Five subtle perimeter setting tick marks (13⅓, 23⅓, 33⅓, 45, 52⅔)
                val allModes = RpmMode.entries
                val tickDist = knobRadius + 3.2.dp.toPx()
                for (mode in allModes) {
                    val isSelected = mode == rpmMode
                    // Angle relative to 12 o'clock (-90 degrees)
                    val rad = (mode.indicatorAngleDeg - 90f) * (PI.toFloat() / 180f)
                    val tickPos = Offset(cx + cos(rad) * tickDist, cy + sin(rad) * tickDist)

                    drawCircle(
                        color = if (isSelected) palette.dominantTint.copy(alpha = 0.90f) else Color(0x35FFFFFF),
                        radius = if (isSelected) 1.5.dp.toPx() else 1.0.dp.toPx(),
                        center = tickPos
                    )
                }

                // 7. Rotating indicator notch etched into the knob surface
                rotate(degrees = animatedAngle, pivot = center) {
                    val notchStartX = cx
                    val notchStartY = cy - (knobRadius - 2.dp.toPx()) * 0.35f
                    val notchEndX = cx
                    val notchEndY = cy - (knobRadius - 2.dp.toPx()) * 0.88f

                    // Notch groove
                    drawLine(
                        color = Color(0xFFF0F0F5),
                        start = Offset(notchStartX, notchStartY),
                        end = Offset(notchEndX, notchEndY),
                        strokeWidth = 2.0.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Accent glint at the tip of the notch
                    drawCircle(
                        color = palette.dominantTint.copy(alpha = 0.85f),
                        radius = 1.2.dp.toPx(),
                        center = Offset(notchEndX, notchEndY)
                    )
                }
            }
        }

        // Clean typography readout underneath displaying the currently selected RPM
        Text(
            text = "${rpmMode.displayName} RPM",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            color = if (isPressed) TextPrimary else TextSecondary,
            letterSpacing = 1.2.sp
        )
    }
}

/**
 * Physical Volume Control Knob
 *
 * Visually matches the RPM knob with the exact same dark graphite material language:
 * - Dark black/graphite machined material (#26262C to #121215)
 * - Subtle metallic edge highlight ring and machined chamfered bevel
 * - Small downward contact shadow for realistic physical depth
 * - Recessed pointer notch indicating the active volume position
 * - Subtle perimeter tick markings for the 10% volume increments (0% to 100%)
 * - Clean typography readout underneath displaying the active volume percent (e.g. "70% VOL")
 * - Strictly CLICK-ONLY (advances +10% on each click: 0% -> 10% -> ... -> 100% -> wraps to 0%)
 * - Instantaneous and lightweight
 */
@Composable
fun PhysicalVolumeKnob(
    volumePercent: Int,
    palette: AlbumColorPalette,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile physical click depression
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1.0f,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 450f),
        label = "VolKnobPress"
    )

    // Calculate angle: 0% is -135° (7 o'clock), 100% is +135° (5 o'clock)
    val clampedVol = volumePercent.coerceIn(0, 100)
    val targetAngleDeg = -135f + (clampedVol / 100f) * 270f

    // Smooth physical rotation of the pointer notch to target angle
    val animatedAngle by animateFloatAsState(
        targetValue = targetAngleDeg,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
        label = "VolKnobAngle"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Physical Hardware Knob
        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(animatedScale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val center = Offset(cx, cy)
                val knobRadius = 18.dp.toPx()

                // 1. Small soft contact shadow cast downwards
                drawCircle(
                    color = Color(0x65000000),
                    radius = knobRadius + 1.5.dp.toPx(),
                    center = Offset(cx, cy + 3.dp.toPx())
                )

                // 2. Outer beveled graphite bezel
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF383842),
                            Color(0xFF202026),
                            Color(0xFF141418)
                        ),
                        startY = cy - knobRadius,
                        endY = cy + knobRadius
                    ),
                    radius = knobRadius,
                    center = center
                )

                // 3. Crisp metallic edge highlight ring
                drawCircle(
                    color = Color(0x35FFFFFF),
                    radius = knobRadius - 0.6.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.0.dp.toPx())
                )

                // 4. Cylindrical knob top face (dark obsidian graphite with slight upper highlight)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF282830),
                            Color(0xFF1B1B20),
                            Color(0xFF121215)
                        ),
                        center = Offset(cx, cy - 2.dp.toPx()),
                        radius = knobRadius - 2.dp.toPx()
                    ),
                    radius = knobRadius - 2.dp.toPx(),
                    center = center
                )

                // 5. Subtle inner circular machining groove
                drawCircle(
                    color = Color(0x18FFFFFF),
                    radius = (knobRadius - 2.dp.toPx()) * 0.70f,
                    center = center,
                    style = Stroke(width = 0.8.dp.toPx())
                )

                // 6. Perimeter tick marks for 10% increments (0% to 100%)
                val tickDist = knobRadius + 3.2.dp.toPx()
                for (step in 0..10) {
                    val stepVol = step * 10
                    val isLit = stepVol <= clampedVol
                    val tickAngle = -135f + (step / 10f) * 270f
                    val rad = (tickAngle - 90f) * (PI.toFloat() / 180f)
                    val tickPos = Offset(cx + cos(rad) * tickDist, cy + sin(rad) * tickDist)

                    drawCircle(
                        color = if (isLit) palette.dominantTint.copy(alpha = 0.90f) else Color(0x35FFFFFF),
                        radius = if (step == 0 || step == 10 || stepVol == clampedVol) 1.5.dp.toPx() else 1.0.dp.toPx(),
                        center = tickPos
                    )
                }

                // 7. Rotating indicator notch etched into the knob surface
                rotate(degrees = animatedAngle, pivot = center) {
                    val notchStartX = cx
                    val notchStartY = cy - (knobRadius - 2.dp.toPx()) * 0.35f
                    val notchEndX = cx
                    val notchEndY = cy - (knobRadius - 2.dp.toPx()) * 0.88f

                    // Notch groove
                    drawLine(
                        color = Color(0xFFF0F0F5),
                        start = Offset(notchStartX, notchStartY),
                        end = Offset(notchEndX, notchEndY),
                        strokeWidth = 2.0.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Accent glint at the tip of the notch
                    drawCircle(
                        color = palette.dominantTint.copy(alpha = 0.85f),
                        radius = 1.2.dp.toPx(),
                        center = Offset(notchEndX, notchEndY)
                    )
                }
            }
        }

        // Clean typography readout underneath displaying the currently selected Volume
        Text(
            text = "$clampedVol% VOL",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            color = if (isPressed) TextPrimary else TextSecondary,
            letterSpacing = 1.2.sp
        )
    }
}

/**
 * Minimalist Hardware Status & Mode Bar
 * Compact unobtrusive switch for RPM (33 ⅓ / 45) and Input Mode (Live / Demo).
 */
@Composable
fun HardwareStatusBar(
    isPlaying: Boolean,
    rpmMode: RpmMode,
    onToggleSpeedClick: () -> Unit,
    isAuditionMode: Boolean,
    onToggleAuditionClick: () -> Unit,
    dominantTint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Minimal RPM Switch
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onToggleSpeedClick)
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .testTag("speed_toggle_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(
                        if (rpmMode != RpmMode.RPM_33) dominantTint else Color(0x60FFFFFF),
                        CircleShape
                    )
            )
            Text(
                text = "${rpmMode.displayName} RPM",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary,
                letterSpacing = 0.8.sp
            )
        }

        // Minimal Live / Demo Mode Switch
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onToggleAuditionClick)
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .testTag("audition_toggle_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(
                        if (isAuditionMode) dominantTint else Color(0x60FFFFFF),
                        CircleShape
                    )
            )
            Text(
                text = if (isAuditionMode) "DEMO" else "LIVE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary,
                letterSpacing = 0.8.sp
            )
        }
    }
}
