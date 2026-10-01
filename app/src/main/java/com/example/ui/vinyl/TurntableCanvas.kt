package com.example.ui.vinyl

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.media.AlbumColorPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * VinylTextureCache
 *
 * Pre-renders and caches reusable static textures for the physical vinyl disc
 * and specular lighting sheen to achieve an authentic rotation illusion at solid 60 FPS.
 * Textures are rendered with extremely subtle, low-contrast opacity and refined physical subtlety.
 */
object VinylTextureCache {
    private var cachedMicrogrooveBitmap: Bitmap? = null
    private var cachedMicrogrooveImageBitmap: ImageBitmap? = null

    private var cachedSheenBitmap: Bitmap? = null
    private var cachedSheenImageBitmap: ImageBitmap? = null
    private var lastSheenColor: Color? = null

    /**
     * Retrieves or generates the static microgroove vinyl texture with extremely subtle contrast.
     */
    fun getOrCreateMicrogrooveTexture(size: Int = 512): ImageBitmap {
        val existingBmp = cachedMicrogrooveBitmap
        val existingImg = cachedMicrogrooveImageBitmap
        if (existingBmp != null && existingImg != null && existingBmp.width == size && !existingBmp.isRecycled) {
            return existingImg
        }

        val bmp = createMicrogrooveBitmap(size)
        val img = bmp.asImageBitmap()
        cachedMicrogrooveBitmap = bmp
        cachedMicrogrooveImageBitmap = img
        return img
    }

    /**
     * Retrieves or generates the static grainy anisotropic specular sheen texture with reduced opacity.
     */
    fun getOrCreateGrainySheenTexture(size: Int = 512, dominantColor: Color): ImageBitmap {
        val existingBmp = cachedSheenBitmap
        val existingImg = cachedSheenImageBitmap
        if (existingBmp != null && existingImg != null && existingBmp.width == size && !existingBmp.isRecycled
            && lastSheenColor == dominantColor
        ) {
            return existingImg
        }

        val bmp = createGrainySheenBitmap(size, dominantColor = dominantColor)
        val img = bmp.asImageBitmap()
        cachedSheenBitmap = bmp
        cachedSheenImageBitmap = img
        lastSheenColor = dominantColor
        return img
    }

    private var cachedAlbumArtGrainBitmap: Bitmap? = null
    private var cachedAlbumArtGrainImageBitmap: ImageBitmap? = null

    /**
     * Retrieves or generates the static fine photographic print grain texture for the album art label.
     */
    fun getOrCreateAlbumArtGrainTexture(size: Int = 512): ImageBitmap {
        val existingBmp = cachedAlbumArtGrainBitmap
        val existingImg = cachedAlbumArtGrainImageBitmap
        if (existingBmp != null && existingImg != null && existingBmp.width == size && !existingBmp.isRecycled) {
            return existingImg
        }

        val bmp = createPhotographicGrainBitmap(size)
        val img = bmp.asImageBitmap()
        cachedAlbumArtGrainBitmap = bmp
        cachedAlbumArtGrainImageBitmap = img
        return img
    }

    /**
     * Generates a static monochrome fine photographic print grain texture.
     * Balanced light and dark microscopic flecks evoke physical printed label paper
     * without altering image color, brightness, or saturation.
     */
    private fun createPhotographicGrainBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)

        for (y in 0 until size) {
            val rowOffset = y * size
            for (x in 0 until size) {
                // High-dispersion integer hash for uniform pseudo-random noise
                var h = (x * 374761393 + y * 668265263) xor 0x5DEECE6D
                h = (h xor (h ushr 13)) * 1274126177
                val n = (h xor (h ushr 16)) and 0x7FFFFFFF
                val norm = (n % 1000) / 1000.0f

                // Fine monochrome grain with balanced light and dark flecks
                // Low opacity: peak alpha ~0.08 (8%), average ~0.038
                val alpha: Int
                val rgb: Int
                if (norm > 0.5f) {
                    rgb = 255 // subtle white paper fleck
                    alpha = (((norm - 0.5f) * 2f) * 20f).toInt().coerceIn(0, 24)
                } else {
                    rgb = 0 // subtle dark toner fleck
                    alpha = (((0.5f - norm) * 2f) * 20f).toInt().coerceIn(0, 24)
                }

                pixels[rowOffset + x] = (alpha shl 24) or (rgb shl 16) or (rgb shl 8) or rgb
            }
        }

        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /**
     * Generates a static vinyl texture featuring clean, prominent concentric circular grooves
     * with an extremely subtle sinusoidal disturbance to avoid mechanical perfection,
     * maintaining a deep obsidian black vinyl surface free of micro-noise or radial clutter.
     */
    private fun createMicrogrooveBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val cx = size / 2f
        val cy = size / 2f
        val radiusMax = size / 2f
        val deadWaxR = radiusMax * 0.46f
        val runInR = radiusMax * 0.985f
        val span = runInR - deadWaxR

        for (y in 0 until size) {
            val dy = y - cy
            val dySq = dy * dy
            val rowOffset = y * size
            for (x in 0 until size) {
                val dx = x - cx
                val r = sqrt(dx * dx + dySq)

                if (r > radiusMax) {
                    pixels[rowOffset + x] = 0
                    continue
                }

                // Deep obsidian black base (#0E0E12)
                var lum = 13.5f

                if (r in deadWaxR..runInR) {
                    val theta = atan2(dy, dx)

                    // Extremely subtle sinusoidal disturbance:
                    // r_disturbed = r + A * sin(f1 * theta) * sin(f2 * theta)
                    // A is extremely small (~0.55px), preventing mathematical stiffness without visible distortion
                    val aDisturb = 0.55f
                    val f1 = 4.0
                    val f2 = 9.0
                    val rDisturbed = r + aDisturb * sin(f1 * theta) * sin(f2 * theta)

                    // Dominant original large-scale concentric vinyl grooves
                    val groove1 = sin(rDisturbed * 1.48) * 3.8
                    val groove2 = sin(rDisturbed * 2.96) * 1.4
                    val grooveTotal = (groove1 + groove2).toFloat()

                    lum = (lum + grooveTotal).coerceIn(9.0f, 22.0f)
                } else if (r > runInR) {
                    // Outer rim run-in bevel
                    val lipDist = abs(r - (runInR + 2.0f))
                    if (lipDist < 2.5f) {
                        lum += 5.0f * (1.0f - lipDist / 2.5f)
                    }
                } else {
                    // Run-out spiral in dead wax
                    val theta = atan2(dy, dx)
                    val spiral = (sin(r * 0.8 + theta * 1.0) * 1.6).toFloat()
                    lum = (lum + spiral).coerceIn(10.0f, 17.0f)
                }

                val v = lum.toInt().coerceIn(0, 255)
                val rCol = v
                val gCol = v
                val bCol = (v + 2).coerceIn(0, 255)
                pixels[rowOffset + x] = (0xFF shl 24) or (rCol shl 16) or (gCol shl 8) or bCol
            }
        }

        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /**
     * Generates a pre-rendered static lighting texture representing the vinyl's reflected light.
     * Features a clearly visible broad soft reflection, subtle fine grain, concentric groove catch,
     * and 5–10% subtle album-color influence in the sheen while remaining deep black vinyl.
     */
    private fun createGrainySheenBitmap(
        size: Int,
        lightAngleDeg: Float = -50f,
        dominantColor: Color
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val cx = size / 2f
        val cy = size / 2f
        val radiusMax = size / 2f
        val deadWaxR = radiusMax * 0.46f
        val runInR = radiusMax * 0.985f
        val span = runInR - deadWaxR

        val lightRad = lightAngleDeg * (PI.toFloat() / 180f)
        val bounceRad = lightRad + PI.toFloat()

        // 92% neutral highlight, 8% subtle album-color influence in reflected light
        val albumR = (dominantColor.red * 255).toInt().coerceIn(0, 255)
        val albumG = (dominantColor.green * 255).toInt().coerceIn(0, 255)
        val albumB = (dominantColor.blue * 255).toInt().coerceIn(0, 255)

        val tintR = (226 * 0.92f + albumR * 0.08f).toInt().coerceIn(0, 255)
        val tintG = (226 * 0.92f + albumG * 0.08f).toInt().coerceIn(0, 255)
        val tintB = (236 * 0.92f + albumB * 0.08f).toInt().coerceIn(0, 255)

        fun angleDiff(a: Float, b: Float): Float {
            var diff = abs(a - b) % (2f * PI.toFloat())
            if (diff > PI.toFloat()) diff = 2f * PI.toFloat() - diff
            return diff
        }

        for (y in 0 until size) {
            val dy = y - cy
            val dySq = dy * dy
            val rowOffset = y * size
            for (x in 0 until size) {
                val dx = x - cx
                val r = sqrt(dx * dx + dySq)

                if (r < deadWaxR || r > runInR) {
                    pixels[rowOffset + x] = 0
                    continue
                }

                val theta = atan2(dy, dx)
                val wobble = (sin(r * 0.12) * 0.04 + sin(r * 0.28) * 0.02).toFloat()

                val d1 = angleDiff(theta + wobble, lightRad)
                val d2 = angleDiff(theta + wobble, bounceRad)

                // Broad soft anisotropic reflection lobes
                val sigma = 26f * (PI.toFloat() / 180f)
                val cone1 = exp(-((d1 * d1) / (2f * sigma * sigma)))
                val cone2 = exp(-((d2 * d2) / (2f * sigma * sigma))) * 0.70f
                val baseCone = (cone1 + cone2).coerceIn(0.0f, 1.0f)

                if (baseCone < 0.01f) {
                    pixels[rowOffset + x] = 0
                    continue
                }

                val normR = (r - deadWaxR) / span
                val radialEnvelope = sin(normR * PI.toFloat()).coerceIn(0f, 1f)

                // Subtle fine grain in the reflection
                val hash = (x * 48271 xor y * 39916801) and 0x7FFFFFFF
                val grain = ((hash % 100) / 100.0f - 0.5f) * 0.16f

                // Concentric grooves catching the reflection
                val grooveCatch = 1.0f + 0.12f * (sin(r * 1.48)).toFloat()

                // More visible sheen (peak alpha ~0.15 = 15% opacity), noticeably stronger yet restrained
                val alphaNorm = (baseCone * radialEnvelope * (1.0f + grain) * grooveCatch * 0.15f).coerceIn(0f, 0.22f)
                val alphaInt = (alphaNorm * 255).toInt().coerceIn(0, 255)

                if (alphaInt == 0) {
                    pixels[rowOffset + x] = 0
                } else {
                    pixels[rowOffset + x] = (alphaInt shl 24) or (tintR shl 16) or (tintG shl 8) or tintB
                }
            }
        }

        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }
}

/**
 * TurntableCanvas
 *
 * Skeuomorphic vinyl turntable canvas matching the reference image,
 * engineered with low-opacity pre-rendered textures for a subtle, natural rotation illusion.
 */
@Composable
fun TurntableCanvas(
    currentAngle: Float = 0f,
    currentAngleProvider: () -> Float = { currentAngle },
    playbackProgress: Float,
    tiltX: Float,
    tiltY: Float,
    artworkBitmap: Bitmap?,
    trackTitle: String,
    trackArtist: String,
    palette: AlbumColorPalette,
    onSeekFraction: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hasArtwork = artworkBitmap != null
    val artworkAlpha by animateFloatAsState(
        targetValue = if (hasArtwork) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 350),
        label = "ArtworkAlphaTransition"
    )

    val microgrooveTexture = remember {
        VinylTextureCache.getOrCreateMicrogrooveTexture()
    }
    val sheenTexture = remember(palette.dominantTint) {
        VinylTextureCache.getOrCreateGrainySheenTexture(dominantColor = palette.dominantTint)
    }
    val albumArtGrainTexture = remember {
        VinylTextureCache.getOrCreateAlbumArtGrainTexture()
    }

    BoxWithConstraints(
        modifier = modifier
    ) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()

        val centerX = canvasWidth * 0.39f
        val centerY = canvasHeight * 0.48f
        val center = Offset(centerX, centerY)

        val outerRadius = canvasWidth * 0.41f
        val labelRadius = outerRadius * 0.46f
        val ringRadius = outerRadius * 1.018f
        val wellRadius = outerRadius * 1.045f

        val cachedPlatterBloomBrush = remember(outerRadius, palette, center) {
            val bloomRadius = outerRadius * 1.12f
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.80f to palette.ambientGlow,
                    0.96f to palette.ambientGlow.copy(alpha = 0.015f),
                    1.00f to Color.Transparent
                ),
                center = center,
                radius = bloomRadius
            )
        }

        val cachedWellShadowBrush = remember(outerRadius, center) {
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.86f to Color(0xFF070709),
                    0.96f to Color(0xFF040405),
                    1.00f to Color(0xFF101014)
                ),
                center = center,
                radius = wellRadius
            )
        }

        val cachedLabelPaperBrush = remember(outerRadius, center) {
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color(0xFF1C1C20),
                    0.85f to Color(0xFF121215),
                    1.00f to Color(0xFF0A0A0C)
                ),
                center = center,
                radius = labelRadius
            )
        }

        val cachedSpindleBrush = remember(outerRadius, center) {
            Brush.sweepGradient(
                colorStops = arrayOf(
                    0.00f to Color(0xFF77777D),
                    0.25f to Color(0xFFD0D0D8),
                    0.50f to Color(0xFF55555A),
                    0.75f to Color(0xFFC0C0C8),
                    1.00f to Color(0xFF77777D)
                ),
                center = center
            )
        }

        // LAYER 1: Stationary Deck Surface & Progress Ring
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onSeekFraction, centerX, centerY, outerRadius) {
                    if (onSeekFraction == null) return@pointerInput

                    detectTapGestures { offset ->
                        val dx = offset.x - centerX
                        val dy = offset.y - centerY
                        val touchRadius = sqrt(dx * dx + dy * dy)

                        if (touchRadius in (outerRadius * 0.85f)..(wellRadius * 1.30f)) {
                            var angleDeg = (atan2(dy, dx) * (180f / PI.toFloat())) + 90f
                            if (angleDeg < 0f) angleDeg += 360f
                            val fraction = (angleDeg / 360f).coerceIn(0f, 1f)
                            onSeekFraction(fraction)
                        }
                    }
                }
                .pointerInput(onSeekFraction, centerX, centerY, outerRadius) {
                    if (onSeekFraction == null) return@pointerInput

                    detectDragGestures { change, _ ->
                        change.consume()
                        val dx = change.position.x - centerX
                        val dy = change.position.y - centerY
                        val touchRadius = sqrt(dx * dx + dy * dy)

                        if (touchRadius in (outerRadius * 0.80f)..(wellRadius * 1.35f)) {
                            var angleDeg = (atan2(dy, dx) * (180f / PI.toFloat())) + 90f
                            if (angleDeg < 0f) angleDeg += 360f
                            val fraction = (angleDeg / 360f).coerceIn(0f, 1f)
                            onSeekFraction(fraction)
                        }
                    }
                }
        ) {
            drawCircle(
                brush = cachedPlatterBloomBrush,
                radius = outerRadius * 1.12f,
                center = center,
                blendMode = BlendMode.Screen
            )

            drawCircle(
                brush = cachedWellShadowBrush,
                radius = wellRadius,
                center = center
            )
            drawCircle(
                color = Color(0x16FFFFFF),
                radius = wellRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            val shadowOffsetX = centerX + 6.dp.toPx() - tiltX * 4.dp.toPx()
            val shadowOffsetY = centerY + 10.dp.toPx() - tiltY * 4.dp.toPx()
            drawCircle(
                color = Color(0x95000000),
                radius = outerRadius * 1.01f,
                center = Offset(shadowOffsetX, shadowOffsetY)
            )

            drawOrganicWavyProgressIndicator(
                cx = centerX,
                cy = centerY,
                ringRadius = ringRadius,
                playbackProgress = playbackProgress,
                dominantTint = palette.dominantTint
            )
        }

        // LAYER 2: Rotating Physical Vinyl Surface (GPU-Accelerated)
        val pivotFractionX = (centerX / canvasWidth).coerceIn(0.01f, 0.99f)
        val pivotFractionY = (centerY / canvasHeight).coerceIn(0.01f, 0.99f)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("vinyl_canvas_surface")
                .graphicsLayer {
                    transformOrigin = TransformOrigin(pivotFractionX, pivotFractionY)
                    rotationZ = currentAngleProvider()
                }
        ) {
            val discLeft = (centerX - outerRadius).toInt()
            val discTop = (centerY - outerRadius).toInt()
            val discDim = (outerRadius * 2f).toInt()

            drawImage(
                image = microgrooveTexture,
                dstOffset = IntOffset(discLeft, discTop),
                dstSize = IntSize(discDim, discDim)
            )

            drawCircle(
                color = palette.vinylWashColor,
                radius = outerRadius,
                center = center
            )

            drawCircle(
                color = Color(0x1CFFFFFF),
                radius = outerRadius * 0.985f,
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )

            drawCenterLabel(
                cx = centerX,
                cy = centerY,
                labelRadius = labelRadius,
                artworkBitmap = artworkBitmap,
                artworkAlpha = artworkAlpha,
                labelPaperBrush = cachedLabelPaperBrush,
                palette = palette,
                albumArtGrainTexture = albumArtGrainTexture
            )
        }

        // LAYER 3: Stationary Overhead Lighting & Sheen
        Canvas(modifier = Modifier.fillMaxSize()) {
            val discLeft = (centerX - outerRadius).toInt()
            val discTop = (centerY - outerRadius).toInt()
            val discDim = (outerRadius * 2f).toInt()

            drawImage(
                image = sheenTexture,
                dstOffset = IntOffset(discLeft, discTop),
                dstSize = IntSize(discDim, discDim),
                blendMode = BlendMode.Screen
            )

            drawCircle(
                color = Color(0x22FFFFFF),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.0.dp.toPx())
            )

            drawSpindlePin(centerX, centerY, outerRadius * 0.055f, cachedSpindleBrush)
        }
    }
}

/**
 * Draws a subtle wavy/organic playback position indicator around the platter perimeter,
 * inspired by Material 3 Expressive progress indicators.
 *
 * NOTE: This is strictly a playback-position indicator (progress = currentPosition / duration).
 * It is NOT an audio waveform, does not analyze audio, and has no amplitude or frequency reactivity.
 */
private fun DrawScope.drawOrganicWavyProgressIndicator(
    cx: Float,
    cy: Float,
    ringRadius: Float,
    playbackProgress: Float,
    dominantTint: Color
) {
    val clampedProgress = playbackProgress.coerceIn(0.0f, 1.0f)
    val totalSteps = 240
    val waveFrequency = 24f // 24 subtle organic sine undulations around full 360 degrees
    val waveAmplitude = 1.6.dp.toPx() // Small, restrained amplitude
    val strokeWidthPx = 1.3.dp.toPx() // Thin, unobtrusive stroke

    // Subtle album-art-derived accent (extremely subtle, restrained, not bright neon)
    val activeColor = dominantTint.copy(alpha = 0.42f)
    val trackColor = Color(0x12FFFFFF)

    val startTheta = -PI.toFloat() / 2f // Top (12 o'clock)
    val startR = ringRadius

    // 1. Inactive background track path: full subtle wavy guide
    val trackPath = Path()
    trackPath.moveTo(cx + startR * cos(startTheta), cy + startR * sin(startTheta))

    for (i in 1..totalSteps) {
        val frac = i.toFloat() / totalSteps
        val theta = startTheta + frac * (2f * PI.toFloat())
        val waveOffset = waveAmplitude * sin(frac * waveFrequency * (2f * PI.toFloat()))
        val r = ringRadius + waveOffset
        trackPath.lineTo(cx + r * cos(theta), cy + r * sin(theta))
    }
    trackPath.close()

    drawPath(
        path = trackPath,
        color = trackColor,
        style = Stroke(width = 1.0.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // 2. Active playback progress path: exactly represents currentPosition / duration
    // At 0%: empty; at 50%: half complete; at 100%: completely filled
    if (clampedProgress > 0.002f) {
        val activePath = Path()
        activePath.moveTo(cx + startR * cos(startTheta), cy + startR * sin(startTheta))

        val activeSteps = (clampedProgress * totalSteps).toInt()
        for (i in 1..activeSteps) {
            val frac = i.toFloat() / totalSteps
            val theta = startTheta + frac * (2f * PI.toFloat())
            val waveOffset = waveAmplitude * sin(frac * waveFrequency * (2f * PI.toFloat()))
            val r = ringRadius + waveOffset
            activePath.lineTo(cx + r * cos(theta), cy + r * sin(theta))
        }

        // Exact fractional progress tip
        val tipTheta = startTheta + clampedProgress * (2f * PI.toFloat())
        val tipWaveOffset = waveAmplitude * sin(clampedProgress * waveFrequency * (2f * PI.toFloat()))
        val tipR = ringRadius + tipWaveOffset
        val tipX = cx + tipR * cos(tipTheta)
        val tipY = cy + tipR * sin(tipTheta)

        activePath.lineTo(tipX, tipY)

        drawPath(
            path = activePath,
            color = activeColor,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 3. Delicate tip bead at current playback position
        drawCircle(
            color = Color(0xB0FFFFFF),
            radius = 1.6.dp.toPx(),
            center = Offset(tipX, tipY)
        )
    }
}

private fun DrawScope.drawCenterLabel(
    cx: Float,
    cy: Float,
    labelRadius: Float,
    artworkBitmap: Bitmap?,
    artworkAlpha: Float,
    labelPaperBrush: Brush,
    palette: AlbumColorPalette,
    albumArtGrainTexture: ImageBitmap
) {
    drawCircle(
        color = Color(0x95000000),
        radius = labelRadius * 1.025f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.5.dp.toPx())
    )

    drawCircle(
        brush = labelPaperBrush,
        radius = labelRadius,
        center = Offset(cx, cy)
    )

    if (artworkBitmap != null && artworkAlpha > 0.01f) {
        val clipPath = Path().apply {
            addOval(Rect(cx - labelRadius, cy - labelRadius, cx + labelRadius, cy + labelRadius))
        }

        clipPath(clipPath) {
            val imageBitmap = artworkBitmap.asImageBitmap()
            val srcSize = IntSize(imageBitmap.width, imageBitmap.height)
            val dstOffset = IntOffset((cx - labelRadius).toInt(), (cy - labelRadius).toInt())
            val dstSize = IntSize((labelRadius * 2f).toInt(), (labelRadius * 2f).toInt())

            drawImage(
                image = imageBitmap,
                srcOffset = IntOffset.Zero,
                srcSize = srcSize,
                dstOffset = dstOffset,
                dstSize = dstSize,
                alpha = artworkAlpha
            )

            // TASK 5D: Subtle monochrome photographic print grain overlay over the album artwork
            drawImage(
                image = albumArtGrainTexture,
                dstOffset = dstOffset,
                dstSize = dstSize,
                alpha = artworkAlpha
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.75f to Color(0x10000000),
                        1.00f to Color(0x45000000)
                    ),
                    center = Offset(cx, cy),
                    radius = labelRadius
                ),
                radius = labelRadius,
                center = Offset(cx, cy)
            )
        }
    }

    drawCircle(
        color = Color(0x18FFFFFF),
        radius = labelRadius * 0.94f,
        center = Offset(cx, cy),
        style = Stroke(width = 0.8.dp.toPx())
    )
    drawCircle(
        color = palette.dominantTint.copy(alpha = 0.30f),
        radius = labelRadius * 0.52f,
        center = Offset(cx, cy),
        style = Stroke(width = 0.8.dp.toPx())
    )
}

private fun DrawScope.drawSpindlePin(
    cx: Float,
    cy: Float,
    pinRadius: Float,
    spindleBrush: Brush
) {
    val holeRadius = pinRadius * 1.35f

    drawCircle(
        color = Color(0xFF040404),
        radius = holeRadius,
        center = Offset(cx, cy)
    )

    drawCircle(
        color = Color(0x28FFFFFF),
        radius = holeRadius,
        center = Offset(cx, cy),
        style = Stroke(width = 0.8.dp.toPx())
    )

    drawCircle(
        brush = spindleBrush,
        radius = pinRadius,
        center = Offset(cx, cy)
    )

    drawCircle(
        color = Color(0x70FFFFFF),
        radius = pinRadius * 0.6f,
        center = Offset(cx - pinRadius * 0.15f, cy - pinRadius * 0.15f),
        style = Stroke(width = 0.75.dp.toPx())
    )

    drawCircle(
        color = Color(0xFF202020),
        radius = pinRadius * 0.22f,
        center = Offset(cx, cy)
    )
}
