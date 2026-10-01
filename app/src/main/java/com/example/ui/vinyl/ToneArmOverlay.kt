package com.example.ui.vinyl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.media.AlbumColorPalette
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ToneArmOverlay
 *
 * Precision physical pivoted turntable tonearm viewed directly from above.
 *
 * PHYSICAL GEOMETRY:
 * - Exactly one fixed pivot point: (pivotX, pivotY).
 * - Rigid segment rotating around that pivot.
 * - The cartridge/head is attached to the END of the arm.
 * - The physical head position is ALWAYS calculated from:
 *     headX = pivotX + cos(angle) * armLength
 *     headY = pivotY + sin(angle) * armLength
 * - The cartridge is a realistic top-down rectangle.
 * - Decorative contact shadow is strictly visual and NEVER used in geometry or physics calculations.
 *
 * PLAYBACK POSITIONING:
 * - Strictly driven by `progress = (currentPosition / duration).coerceIn(0f, 1f)`.
 * - Geometrically maps 0% to OUTER_GROOVE_POSITION and 100% to INNER_GROOVE_POSITION.
 * - When playback starts, smoothly rotates from parked cradle directly to the current groove angle.
 * - When paused, stops and rests on the exact groove.
 *
 * INTERACTIVE DRAGGING & RELEASE:
 * - User can touch and drag the cartridge or arm to rotate around the fixed pivot.
 * - Dragging enters manual mode and overrides playback synchronization without fighting the finger.
 * - On release over the playable record:
 *     1. Finds the closest valid groove point.
 *     2. Snaps smoothly without bounce or overshoot.
 *     3. Converts groove position into playback percentage and seeks the media session.
 *     4. Returns source of truth to media playback.
 * - On release outside the playable record:
 *     Smoothly returns to the parked rest cradle.
 *
 * @param hasActiveTrack True if there is an active media session or audition track.
 * @param progress Normalized track completion ratio in range [0.0f, 1.0f].
 * @param tiltX Normalized device horizontal roll for shadow parallax.
 * @param tiltY Normalized device vertical pitch for shadow parallax.
 * @param palette Adaptive color palette derived from album artwork.
 * @param onSeekFraction Optional callback fired when tonearm is released over the record.
 * @param modifier Composable layout modifier.
 */
@Composable
fun ToneArmOverlay(
    hasActiveTrack: Boolean,
    progress: Float,
    tiltX: Float,
    tiltY: Float,
    palette: AlbumColorPalette,
    onSeekFraction: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        // ---------------------------------------------------------
        // Section 1: Turntable Geometry & Pivot Location
        // Matching the primary reference image layout
        // ---------------------------------------------------------
        val vinylCenterX = w * 0.39f
        val vinylCenterY = h * 0.48f
        val vinylCenter = Offset(vinylCenterX, vinylCenterY)

        val outerRadius = w * 0.41f
        val outerGrooveRadius = outerRadius * 0.95f
        val innerGrooveRadius = outerRadius * 0.52f

        val pivotX = w * 0.88f
        val pivotY = h * 0.16f
        val pivot = Offset(pivotX, pivotY)

        val armLength = (w * 0.55f).coerceAtLeast(180f)

        // ---------------------------------------------------------
        // Section 2: Rigorous Geometric Groove Angle Mapping
        // Law of Cosines in triangle (Pivot, VinylCenter, CartridgeHead)
        // ---------------------------------------------------------
        val distPV = (vinylCenter - pivot).getDistance()
        val baseAngle = atan2(vinylCenter.y - pivot.y, vinylCenter.x - pivot.x)

        // Calculates exact angle from pivot so head reaches groove radius r
        fun angleForRadius(r: Float): Float {
            val cosAlpha = ((distPV * distPV + armLength * armLength - r * r) / (2f * distPV * armLength))
                .coerceIn(-1f, 1f)
            val alpha = acos(cosAlpha)
            return baseAngle - alpha
        }

        val outerGrooveAngle = remember(distPV, armLength, outerGrooveRadius, baseAngle) {
            angleForRadius(outerGrooveRadius)
        }
        val innerGrooveAngle = remember(distPV, armLength, innerGrooveRadius, baseAngle) {
            angleForRadius(innerGrooveRadius)
        }
        val parkedAngle = remember(outerGrooveAngle) {
            outerGrooveAngle - 18f * (PI.toFloat() / 180f)
        }

        fun angleForProgress(p: Float): Float {
            val clampedP = p.coerceIn(0f, 1f)
            val targetR = outerGrooveRadius - clampedP * (outerGrooveRadius - innerGrooveRadius)
            return angleForRadius(targetR)
        }

        val minValidAngle = parkedAngle - 6f * (PI.toFloat() / 180f)
        val maxValidAngle = innerGrooveAngle + 8f * (PI.toFloat() / 180f)

        // ---------------------------------------------------------
        // Section 3: Tonearm Angle State & Manual Dragging
        // ---------------------------------------------------------
        val coroutineScope = rememberCoroutineScope()
        val armAngleAnim = remember { Animatable(parkedAngle) }
        var isDragging by remember { mutableStateOf(false) }
        var isManualMode by remember { mutableStateOf(false) }

        // Track playback position strictly when NOT in manual drag/snap mode
        LaunchedEffect(hasActiveTrack, progress, isManualMode) {
            if (!isManualMode && !isDragging) {
                val targetAngle = if (hasActiveTrack) {
                    angleForProgress(progress)
                } else {
                    parkedAngle
                }

                // If starting playback from parked rest cradle:
                val wasParked = kotlin.math.abs(armAngleAnim.value - parkedAngle) < 0.04f
                if (wasParked && hasActiveTrack) {
                    // Smoothly rotate the arm directly toward the current song position
                    armAngleAnim.animateTo(
                        targetValue = targetAngle,
                        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                    )
                } else {
                    // Direct deterministic groove tracking without drift
                    armAngleAnim.animateTo(
                        targetValue = targetAngle,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
        }

        // Current physical head coordinates (calculated purely from pivot + cos/sin * length)
        val currentAngle = armAngleAnim.value
        val headX = pivot.x + cos(currentAngle) * armLength
        val headY = pivot.y + sin(currentAngle) * armLength
        val currentHead = Offset(headX, headY)

        // ---------------------------------------------------------
        // Section 4: Gesture Handling — Touch, Drag & Release
        // ---------------------------------------------------------
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pivot, armLength, minValidAngle, maxValidAngle, outerGrooveRadius, innerGrooveRadius) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val touchPos = down.position

                        // Hit test: did the user touch anywhere along the arm or cartridge?
                        val hitDist = distanceToSegment(touchPos, pivot, currentHead)
                        if (hitDist <= 64.dp.toPx()) {
                            down.consume()
                            isDragging = true
                            isManualMode = true

                            // Calculate desired angle from pointer relative to pivot
                            val initialAngle = atan2(touchPos.y - pivot.y, touchPos.x - pivot.x)
                                .coerceIn(minValidAngle, maxValidAngle)

                            coroutineScope.launch {
                                armAngleAnim.snapTo(initialAngle)
                            }

                            // Follow finger continuously while dragged
                            var isReleased = false
                            while (!isReleased) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break

                                if (change.changedToUp()) {
                                    change.consume()
                                    isReleased = true
                                } else if (change.pressed) {
                                    change.consume()
                                    val pos = change.position
                                    val desiredAngle = atan2(pos.y - pivot.y, pos.x - pivot.x)
                                        .coerceIn(minValidAngle, maxValidAngle)

                                    coroutineScope.launch {
                                        armAngleAnim.snapTo(desiredAngle)
                                    }
                                }
                            }

                            // ---------------------------------------------
                            // Release Logic: Snap to groove or return to park
                            // ---------------------------------------------
                            isDragging = false
                            val releaseAngle = armAngleAnim.value
                            val releaseHeadX = pivot.x + cos(releaseAngle) * armLength
                            val releaseHeadY = pivot.y + sin(releaseAngle) * armLength
                            val distToVinylCenter = sqrt(
                                (releaseHeadX - vinylCenterX) * (releaseHeadX - vinylCenterX) +
                                (releaseHeadY - vinylCenterY) * (releaseHeadY - vinylCenterY)
                            )

                            // Playable groove region check (with small tolerance)
                            val isOverRecord = distToVinylCenter in
                                (innerGrooveRadius - 16.dp.toPx())..(outerGrooveRadius + 22.dp.toPx())

                            if (isOverRecord) {
                                // Snap to nearest valid groove position
                                val clampedDist = distToVinylCenter.coerceIn(innerGrooveRadius, outerGrooveRadius)
                                val seekFrac = ((outerGrooveRadius - clampedDist) / (outerGrooveRadius - innerGrooveRadius))
                                    .coerceIn(0f, 1f)
                                val snapTargetAngle = angleForProgress(seekFrac)

                                coroutineScope.launch {
                                    // Smooth subtle snap with NO bouncing or overshoot
                                    armAngleAnim.animateTo(
                                        targetValue = snapTargetAngle,
                                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                    )
                                    onSeekFraction?.invoke(seekFrac)
                                    isManualMode = false
                                }
                            } else {
                                // Outside playable region: smoothly return to parked rest cradle
                                coroutineScope.launch {
                                    armAngleAnim.animateTo(
                                        targetValue = parkedAngle,
                                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                                    )
                                    isManualMode = false
                                }
                            }
                        }
                    }
                }
        ) {
            // ---------------------------------------------------------
            // Section 5: Stationary Mount Base Plate
            // ---------------------------------------------------------
            drawMountBasePlate(pivot, palette)

            // ---------------------------------------------------------
            // Section 6: Small, Soft Decorative Contact Shadow
            // Shifted slightly for depth; NEVER used in calculations
            // ---------------------------------------------------------
            drawSmallDecorativeShadow(
                pivot = pivot,
                angle = currentAngle,
                armLength = armLength,
                tiltX = tiltX,
                tiltY = tiltY
            )

            // ---------------------------------------------------------
            // Section 7: Rotating Rigid Arm Wand
            // ---------------------------------------------------------
            drawRigidArmWand(
                pivot = pivot,
                head = currentHead
            )

            // ---------------------------------------------------------
            // Section 8: Realistic Top-Down Cartridge Rectangle
            // Attached strictly to the END of the arm (currentHead)
            // ---------------------------------------------------------
            drawRectangularCartridge(
                head = currentHead,
                armAngle = currentAngle,
                palette = palette
            )

            // ---------------------------------------------------------
            // Section 9: Gimbal Pivot Bearing & Counterweight
            // ---------------------------------------------------------
            drawGimbalAssembly(
                pivot = pivot,
                armAngle = currentAngle
            )
        }
    }
}

/**
 * Calculates distance from point p to line segment ab.
 */
private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val ap = p - a
    val abLenSq = ab.x * ab.x + ab.y * ab.y
    if (abLenSq <= 0.0001f) return (p - a).getDistance()
    val t = ((ap.x * ab.x + ap.y * ab.y) / abLenSq).coerceIn(0f, 1f)
    val proj = a + Offset(ab.x * t, ab.y * t)
    return (p - proj).getDistance()
}

/**
 * Draws the vertical pill mounting plate on the deck surface.
 */
private fun DrawScope.drawMountBasePlate(
    pivot: Offset,
    palette: AlbumColorPalette
) {
    val plateWidth = 44.dp.toPx()
    val plateHeight = 116.dp.toPx()
    val cornerRadius = plateWidth / 2f

    val plateLeft = pivot.x - plateWidth / 2f
    val plateTop = pivot.y - 26.dp.toPx()

    // Base plate contact shadow
    drawRoundRect(
        color = Color(0x65000000),
        topLeft = Offset(plateLeft + 3.dp.toPx(), plateTop + 4.dp.toPx()),
        size = Size(plateWidth, plateHeight),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )

    // Dark graphite plate body
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF222226),
                Color(0xFF18181B),
                Color(0xFF131316)
            ),
            startY = plateTop,
            endY = plateTop + plateHeight
        ),
        topLeft = Offset(plateLeft, plateTop),
        size = Size(plateWidth, plateHeight),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )

    // Metallic chamfer outline
    drawRoundRect(
        color = Color(0x28FFFFFF),
        topLeft = Offset(plateLeft, plateTop),
        size = Size(plateWidth, plateHeight),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        style = Stroke(width = 1.dp.toPx())
    )

    // Rest cradle notch at bottom of plate
    val cradleY = plateTop + plateHeight - 18.dp.toPx()
    drawRoundRect(
        color = Color(0xFF0E0E10),
        topLeft = Offset(pivot.x - 9.dp.toPx(), cradleY - 3.5.dp.toPx()),
        size = Size(18.dp.toPx(), 7.dp.toPx()),
        cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
    )
}

/**
 * Draws small, soft decorative contact shadow.
 * Purely decorative; never affects physical head or arm calculations.
 * Deliberately restrained so cartridge is clearly distinguishable from its shadow.
 */
private fun DrawScope.drawSmallDecorativeShadow(
    pivot: Offset,
    angle: Float,
    armLength: Float,
    tiltX: Float,
    tiltY: Float
) {
    val shadowOffsetX = 4.dp.toPx() - tiltX * 3.dp.toPx()
    val shadowOffsetY = 6.dp.toPx() - tiltY * 3.dp.toPx()
    val shadowPivot = Offset(pivot.x + shadowOffsetX, pivot.y + shadowOffsetY)
    val shadowHead = Offset(
        shadowPivot.x + cos(angle) * armLength,
        shadowPivot.y + sin(angle) * armLength
    )

    // Wand shadow (kept restrained, not enlarged to compensate)
    drawLine(
        color = Color(0x35000000),
        start = shadowPivot,
        end = shadowHead,
        strokeWidth = 6.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Cartridge shadow (kept restrained, not enlarged to compensate)
    val trackingOffset = 22f * (PI.toFloat() / 180f)
    val cartAngle = (angle + trackingOffset) * (180f / PI.toFloat())
    val cartW = 14.dp.toPx()
    val cartH = 28.dp.toPx()

    rotate(degrees = cartAngle, pivot = shadowHead) {
        drawRoundRect(
            color = Color(0x40000000),
            topLeft = Offset(shadowHead.x - cartW / 2f, shadowHead.y - cartH / 2f),
            size = Size(cartW, cartH),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
    }
}

/**
 * Draws the rigid cylindrical dark graphite tonearm wand.
 * Visibly larger with substantial width/thickness like a real physical turntable component.
 * Stretches strictly between pivot and physical head.
 */
private fun DrawScope.drawRigidArmWand(
    pivot: Offset,
    head: Offset
) {
    val wandWidth = 10.dp.toPx()

    // 1. Dark cylindrical graphite rod body with substantial thickness
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF383840),
                Color(0xFF24242A),
                Color(0xFF161619)
            ),
            start = Offset(pivot.x - wandWidth, pivot.y),
            end = Offset(pivot.x + wandWidth, pivot.y)
        ),
        start = pivot,
        end = head,
        strokeWidth = wandWidth,
        cap = StrokeCap.Round
    )

    // 2. Crisp specular highlight line along the arm length
    drawLine(
        color = Color(0x48FFFFFF),
        start = Offset(pivot.x - 2.dp.toPx(), pivot.y + 4.dp.toPx()),
        end = Offset(head.x - 2.dp.toPx(), head.y - 2.dp.toPx()),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )
}

/**
 * Draws the realistic top-down rectangular cartridge.
 * Visibly larger simple realistic rectangle viewed from directly above.
 * Attached strictly to the physical end of the arm (head).
 */
private fun DrawScope.drawRectangularCartridge(
    head: Offset,
    armAngle: Float,
    palette: AlbumColorPalette
) {
    // Realistic tracking offset angle (~22 degrees inward to tangentially track grooves)
    val trackingOffset = 22f * (PI.toFloat() / 180f)
    val cartAngleDeg = (armAngle + trackingOffset) * (180f / PI.toFloat())

    // Cartridge dimensions: visibly larger realistic top-down rectangle
    val cartW = 22.dp.toPx()
    val cartH = 46.dp.toPx()

    rotate(degrees = cartAngleDeg, pivot = head) {
        val rectTopLeft = Offset(head.x - cartW / 2f, head.y - cartH / 2f)

        // 1. Main rectangular cartridge body: dark graphite (#1C1C20)
        drawRoundRect(
            color = Color(0xFF1C1C20),
            topLeft = rectTopLeft,
            size = Size(cartW, cartH),
            cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
        )

        // 2. Chamfered metallic edge bevel
        drawRoundRect(
            color = Color(0x35FFFFFF),
            topLeft = rectTopLeft,
            size = Size(cartW, cartH),
            cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx()),
            style = Stroke(width = 1.2.dp.toPx())
        )

        // 3. Center alignment stripe on cartridge top (tinted with album accent)
        drawLine(
            color = palette.dominantTint.copy(alpha = 0.85f),
            start = Offset(head.x, rectTopLeft.y + 5.dp.toPx()),
            end = Offset(head.x, rectTopLeft.y + cartH - 5.dp.toPx()),
            strokeWidth = 2.dp.toPx()
        )

        // 4. Stylus needle indicator dot at the front edge of the cartridge
        drawCircle(
            color = Color(0xFFE4E4E8),
            radius = 2.4.dp.toPx(),
            center = Offset(head.x, rectTopLeft.y + cartH - 2.5.dp.toPx())
        )

        // 5. Stylus diamond tip highlight
        drawCircle(
            color = Color(0xFFFFFFFF),
            radius = 1.2.dp.toPx(),
            center = Offset(head.x, rectTopLeft.y + cartH - 2.5.dp.toPx())
        )
    }
}

/**
 * Draws the gimbal pivot bearing, counterweight, and center cap jewel.
 */
private fun DrawScope.drawGimbalAssembly(
    pivot: Offset,
    armAngle: Float
) {
    // 1. Counterweight extending behind the pivot (opposite to arm direction)
    val counterAngle = armAngle + PI.toFloat()
    val counterDist = 24.dp.toPx()
    val counterCenter = Offset(
        pivot.x + cos(counterAngle) * counterDist,
        pivot.y + sin(counterAngle) * counterDist
    )

    // Counterweight stub
    drawLine(
        color = Color(0xFF28282E),
        start = pivot,
        end = counterCenter,
        strokeWidth = 5.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Counterweight cylinder
    val cwRadius = 11.dp.toPx()
    drawCircle(
        color = Color(0xFF323238),
        radius = cwRadius,
        center = counterCenter
    )
    drawCircle(
        color = Color(0x40FFFFFF),
        radius = cwRadius,
        center = counterCenter,
        style = Stroke(width = 1.dp.toPx())
    )

    // 2. Gimbal bearing housing at pivot
    val gimbalRadius = 15.dp.toPx()
    drawCircle(
        color = Color(0xFF24242A),
        radius = gimbalRadius,
        center = pivot
    )
    drawCircle(
        color = Color(0x35FFFFFF),
        radius = gimbalRadius,
        center = pivot,
        style = Stroke(width = 1.2.dp.toPx())
    )

    // 3. Top metallic jewel cap
    drawCircle(
        color = Color(0xFF141416),
        radius = 5.5.dp.toPx(),
        center = pivot
    )
    drawCircle(
        color = Color(0x55FFFFFF),
        radius = 4.dp.toPx(),
        center = pivot,
        style = Stroke(width = 0.8.dp.toPx())
    )
}
