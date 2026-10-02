package com.example.ui.vinyl

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
    private var lastDominantColor: Color? = null

    /**
     * Retrieves or generates the ultra-high-definition (1024x1024) static microgroove vinyl texture combining:
     * 1. Deep obsidian black base vinyl (#0C0C10)
     * 2. High-precision concentric circular grooves with physical waviness and multi-harmonic ridge profile
     * 3. Directional micro-facet groove reflections moving with the rotating vinyl
     * 4. Deep album-art tint integrated into the vinyl material midtones
     */
    fun getOrCreateMicrogrooveTexture(size: Int = 1024, dominantColor: Color = Color.White): ImageBitmap {
        val existingBmp = cachedMicrogrooveBitmap
        val existingImg = cachedMicrogrooveImageBitmap
        if (existingBmp != null && existingImg != null && existingBmp.width == size && !existingBmp.isRecycled
            && lastDominantColor == dominantColor
        ) {
            return existingImg
        }

        val bmp = createMicrogrooveBitmap(size, dominantColor)
        val img = bmp.asImageBitmap()
        cachedMicrogrooveBitmap = bmp
        cachedMicrogrooveImageBitmap = img
        lastDominantColor = dominantColor
        return img
    }

    private var cachedVinylSheenBitmap: Bitmap? = null
    private var cachedVinylSheenImageBitmap: ImageBitmap? = null

    /**
     * Retrieves or generates the ultra-high-definition (1024x1024) static detached overhead vinyl sheen texture.
     * Floats stationary in Layer 3 above the rotating disc, capturing physical overhead lighting.
     */
    fun getOrCreateVinylSheenTexture(size: Int = 1024): ImageBitmap {
        val existingBmp = cachedVinylSheenBitmap
        val existingImg = cachedVinylSheenImageBitmap
        if (existingBmp != null && existingImg != null && existingBmp.width == size && !existingBmp.isRecycled) {
            return existingImg
        }

        val bmp = createVinylSheenBitmap(size)
        val img = bmp.asImageBitmap()
        cachedVinylSheenBitmap = bmp
        cachedVinylSheenImageBitmap = img
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
     * with physical waviness, concentric ridges, rich dark album-art-derived tint integrated into the vinyl material,
     * and directional anisotropic specular groove highlights that MOVE WITH THE ROTATING VINYL.
     *
     * Texture components:
     * - BASE DISC: deep obsidian matte black (#0C0C10)
     * - CONCENTRIC GROOVE TEXTURE: multi-harmonic concentric circular microgrooves
     * - PHYSICAL IRREGULARITY: minute sinusoidal disturbance preventing mechanical stiffness
     * - ALBUM-ART TINT: integrated into vinyl midtones (dark blue-black, dark burgundy-black, etc.)
     * - DIRECTIONAL SPECULAR HIGHLIGHTS: neutral graphite/white-gray anisotropic reflection lobes
     *   concentrated specifically on groove ridges, moving with the rotating vinyl
     */
    private fun createMicrogrooveBitmap(size: Int, dominantColor: Color): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val cx = size / 2f
        val cy = size / 2f
        val radiusMax = size / 2f
        val deadWaxR = radiusMax * 0.46f
        val runInR = radiusMax * 0.985f
        val span = runInR - deadWaxR

        val lightAngleDeg = -50f
        val lightRad = lightAngleDeg * (PI.toFloat() / 180f)
        val bounceRad = lightRad + PI.toFloat()
        val sideRad = lightRad + (PI.toFloat() * 0.5f)

        fun angleDiff(a: Float, b: Float): Float {
            var diff = abs(a - b) % (2f * PI.toFloat())
            if (diff > PI.toFloat()) diff = 2f * PI.toFloat() - diff
            return diff
        }

        // Extract normalized chromatic directions from dominantColor
        val domR = dominantColor.red
        val domG = dominantColor.green
        val domB = dominantColor.blue
        val maxDom = maxOf(domR, domG, domB).coerceAtLeast(0.001f)
        val minDom = minOf(domR, domG, domB)
        val sat = ((maxDom - minDom) / maxDom).coerceIn(0f, 1f)
        val normR = domR / maxDom
        val normG = domG / maxDom
        val normB = domB / maxDom
        val meanDom = (normR + normG + normB) / 3.0f
        val chromR = normR - meanDom
        val chromG = normG - meanDom
        val chromB = normB - meanDom

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

                // Deep obsidian black base (#0C0C10)
                val baseLum = 12.0f

                if (r in deadWaxR..runInR) {
                    val theta = atan2(dy, dx)

                    // 1. Organic physical irregularity (micro-eccentricity and pitch modulation)
                    val aDisturb = 0.45f
                    val rDisturbed = r + aDisturb * sin(3.0 * theta) * sin(7.0 * theta + 0.4)

                    // 2. Concentric microgroove profile (multi-harmonic circular ridges)
                    val g1 = sin(rDisturbed * 1.58)
                    val g2 = sin(rDisturbed * 3.16) * 0.45
                    val g3 = sin(rDisturbed * 6.32) * 0.20
                    val grooveRidge = (g1 + g2 + g3).toFloat()
                    val unlitGroove = grooveRidge * 3.4f

                    // 3. Recorded song track bands across LP radius
                    val normSpan = (r - deadWaxR) / span
                    val trackBand = sin(normSpan * PI * 6.0).toFloat()
                    val trackMod = if (trackBand > 0.88f) -2.0f else 0.5f

                    // 4. Directional Anisotropic Specular Highlight Lobes (rotates WITH vinyl)
                    val thetaWobble = theta + (sin(r * 0.16) * 0.04 + sin(r * 0.38) * 0.02).toFloat()
                    val d1 = angleDiff(thetaWobble, lightRad)
                    val d2 = angleDiff(thetaWobble, bounceRad)
                    val d3 = angleDiff(thetaWobble, sideRad)

                    val sigma = 34f * (PI.toFloat() / 180f)
                    val cone1 = exp(-((d1 * d1) / (2f * sigma * sigma)))
                    val cone2 = exp(-((d2 * d2) / (2f * sigma * sigma))) * 0.72f
                    val cone3 = exp(-((d3 * d3) / (2f * (sigma * 1.4f) * (sigma * 1.4f)))) * 0.20f
                    val specularEnvelope = (cone1 + cone2 + cone3).coerceIn(0f, 1f)

                    val radialEnv = sin(normSpan * PI.toFloat()).coerceIn(0f, 1f)

                    // 5. Specular highlight response concentrated on groove ridges
                    val ridgeFactor = ((grooveRidge + 1.65f) / 3.30f).coerceIn(0f, 1f)
                    val ridgeCatch = ridgeFactor * ridgeFactor
                    val elongatedBase = specularEnvelope * radialEnv
                    val specularIntensity = elongatedBase * (ridgeCatch * 34.0f + 6.0f)

                    // Fine microscopic grain fleck
                    val hash = (x * 48271 xor y * 39916801) and 0x7FFFFFFF
                    val grain = ((hash % 100) / 100.0f - 0.5f) * 0.14f
                    val specularAdd = (specularIntensity * (1.0f + grain)).coerceIn(0f, 44.0f)

                    // 6. Integrated Album-Art Tint in the Vinyl Material
                    // Deep black shadows stay black; midtones reveal the rich, dark album-derived hue
                    val bodyLum = (baseLum + unlitGroove + trackMod).coerceIn(7.0f, 22.0f)
                    val midtoneWeight = ((bodyLum - 7.0f) / 15.0f).coerceIn(0f, 1f)
                    val tintChroma = 24.0f * midtoneWeight * sat

                    val baseR = (bodyLum + chromR * tintChroma).coerceIn(6.0f, 34.0f)
                    val baseG = (bodyLum + chromG * tintChroma).coerceIn(6.0f, 34.0f)
                    val baseB = (bodyLum + chromB * tintChroma + 1.0f).coerceIn(6.0f, 34.0f)

                    // 7. Neutral Graphite/White-Gray Specular Highlights composited on top
                    val rCol = (baseR + specularAdd).toInt().coerceIn(0, 255)
                    val gCol = (baseG + specularAdd).toInt().coerceIn(0, 255)
                    val bCol = (baseB + specularAdd).toInt().coerceIn(0, 255)

                    pixels[rowOffset + x] = (0xFF shl 24) or (rCol shl 16) or (gCol shl 8) or bCol
                } else if (r < deadWaxR) {
                    // Dead wax run-out spiral
                    val theta = atan2(dy, dx)
                    val spiral = (sin(r * 0.75 + theta * 1.0) * 1.8).toFloat()
                    val dLum = (baseLum - 1.0f + spiral).coerceIn(8.0f, 18.0f)
                    val midtoneWeight = ((dLum - 7.0f) / 11.0f).coerceIn(0f, 1f)
                    val tintChroma = 14.0f * midtoneWeight * sat
                    val rVal = (dLum + chromR * tintChroma).toInt().coerceIn(0, 255)
                    val gVal = (dLum + chromG * tintChroma).toInt().coerceIn(0, 255)
                    val bVal = (dLum + chromB * tintChroma + 1.0f).toInt().coerceIn(0, 255)
                    pixels[rowOffset + x] = (0xFF shl 24) or (rVal shl 16) or (gVal shl 8) or bVal
                } else {
                    // Outer rim run-in bevel
                    val lipDist = abs(r - (runInR + 2.0f))
                    var rimLum = baseLum
                    if (lipDist < 2.5f) {
                        rimLum += 5.5f * (1.0f - lipDist / 2.5f)
                    }
                    val rLum = rimLum.toInt().coerceIn(0, 255)
                    pixels[rowOffset + x] = (0xFF shl 24) or (rLum shl 16) or (rLum shl 8) or (rLum + 2)
                }
            }
        }

        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /**
     * Pre-bakes an ultra-high-definition (1024x1024) stationary anisotropic overhead lighting sheen.
     * Features:
     * - Anisotropic dual-cone reflection lobes centered at key light angle (-50°) and bounce angle (130°)
     * - Soft diffuse fill light cone
     * - Physical microgroove reflection envelope tapering across playable disc radius
     * - Subpixel microscopic grain preventing gradient banding
     * - Fully transparent over center label and outside record edge
     */
    private fun createVinylSheenBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val cx = size / 2f
        val cy = size / 2f
        val radiusMax = size / 2f
        val deadWaxR = radiusMax * 0.46f
        val runInR = radiusMax * 0.985f
        val span = runInR - deadWaxR

        val lightAngleDeg = -50f
        val lightRad = lightAngleDeg * (PI.toFloat() / 180f)
        val bounceRad = lightRad + PI.toFloat()
        val sideRad = lightRad + (PI.toFloat() * 0.5f)

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

                if (r !in deadWaxR..runInR) {
                    pixels[rowOffset + x] = 0
                    continue
                }

                val theta = atan2(dy, dx)
                val normSpan = (r - deadWaxR) / span
                val radialEnvelope = sin(normSpan * PI.toFloat()).coerceIn(0f, 1f)

                // High-precision anisotropic highlight lobes
                val d1 = angleDiff(theta, lightRad)
                val d2 = angleDiff(theta, bounceRad)
                val d3 = angleDiff(theta, sideRad)

                val sigma = 32f * (PI.toFloat() / 180f)
                val cone1 = exp(-((d1 * d1) / (2f * sigma * sigma)))
                val cone2 = exp(-((d2 * d2) / (2f * sigma * sigma))) * 0.72f
                val cone3 = exp(-((d3 * d3) / (2f * (sigma * 1.5f) * (sigma * 1.5f)))) * 0.22f

                val sheenEnvelope = (cone1 + cone2 + cone3).coerceIn(0f, 1f)

                // Microgroove specular ridge modulation
                val ridgeHarmonics = 0.88f + 0.12f * sin(normSpan * 180.0 * 2.0 * PI).toFloat()

                // Subpixel micro-grain preventing gradient banding
                val hash = (x * 48271 xor y * 39916801) and 0x7FFFFFFF
                val grain = ((hash % 100) / 100.0f - 0.5f) * 0.08f

                val intensity = (sheenEnvelope * radialEnvelope * ridgeHarmonics * (1.0f + grain)).coerceIn(0f, 1f)
                val alpha = (intensity * 105f).toInt().coerceIn(0, 115)

                if (alpha > 0) {
                    // Crisp neutral silver-white studio lighting
                    pixels[rowOffset + x] = (alpha shl 24) or (0xEB shl 16) or (0xEE shl 8) or 0xF5
                } else {
                    pixels[rowOffset + x] = 0
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
    onVinylTap: (() -> Unit)? = null,
    onVinylSwipeUp: (() -> Unit)? = null,
    onVinylSwipeDown: (() -> Unit)? = null,
    onPlatterMove: ((velocityPxPerSec: Float) -> Unit)? = null,
    onPlatterTouchEnd: (() -> Unit)? = null,
    swipeDirection: Int = 0,
    swipeTrigger: Long = 0L,
    modifier: Modifier = Modifier
) {
    val hasArtwork = artworkBitmap != null
    val artworkAlpha by animateFloatAsState(
        targetValue = if (hasArtwork) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 350),
        label = "ArtworkAlphaTransition"
    )

    // Smooth directional album art transition on track swipe
    var displayedArtwork by remember { mutableStateOf(artworkBitmap) }
    var outgoingArtwork by remember { mutableStateOf<Bitmap?>(null) }
    var activeSwipeDirection by remember { mutableIntStateOf(-1) }
    val artworkTransitionAnim = remember { Animatable(1f) }

    LaunchedEffect(artworkBitmap) {
        if (artworkBitmap != displayedArtwork) {
            outgoingArtwork = displayedArtwork
            displayedArtwork = artworkBitmap
            activeSwipeDirection = if (swipeDirection != 0) swipeDirection else -1
            artworkTransitionAnim.snapTo(0f)
            artworkTransitionAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
            )
            outgoingArtwork = null
        }
    }

    // Physical vinyl feedback shift on track swipe (250-450ms total response)
    val density = LocalDensity.current
    val vinylShiftAnim = remember { Animatable(0f) }
    LaunchedEffect(swipeTrigger) {
        if (swipeTrigger > 0L) {
            val shiftPx = with(density) { 8.dp.toPx() }
            val targetShift = if (swipeDirection < 0) -shiftPx else shiftPx
            vinylShiftAnim.animateTo(
                targetValue = targetShift,
                animationSpec = tween(durationMillis = 130, easing = FastOutSlowInEasing)
            )
            vinylShiftAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 230, easing = FastOutSlowInEasing)
            )
        }
    }

    val microgrooveTexture = remember(palette.dominantTint) {
        VinylTextureCache.getOrCreateMicrogrooveTexture(dominantColor = palette.dominantTint)
    }
    val vinylSheenTexture = remember {
        VinylTextureCache.getOrCreateVinylSheenTexture()
    }
    val albumArtGrainTexture = remember {
        VinylTextureCache.getOrCreateAlbumArtGrainTexture()
    }

    BoxWithConstraints(
        modifier = modifier.testTag("vinyl_visualizer_component")
    ) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()

        // Vinyl Diameter = 85% of available display width (Target: 85% of viewport width)
        val outerRadius = canvasWidth * 0.425f
        val labelRadius = outerRadius * 0.46f
        val ringRadius = outerRadius * 1.018f
        val wellRadius = outerRadius * 1.045f

        val centerX = canvasWidth * 0.44f
        val centerY = canvasHeight * 0.49f
        val center = Offset(centerX, centerY)

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

        // Cache static background track path for progress indicator (computed only on layout change)
        val cachedTrackPath = remember(centerX, centerY, ringRadius) {
            val path = Path()
            val totalSteps = 240
            val waveFrequency = 24f
            val waveAmplitude = with(density) { 1.6.dp.toPx() }
            val startTheta = -PI.toFloat() / 2f
            val startR = ringRadius
            path.moveTo(centerX + startR * cos(startTheta), centerY + startR * sin(startTheta))
            for (i in 1..totalSteps) {
                val frac = i.toFloat() / totalSteps
                val theta = startTheta + frac * (2f * PI.toFloat())
                val waveOffset = waveAmplitude * sin(frac * waveFrequency * (2f * PI.toFloat()))
                val r = ringRadius + waveOffset
                path.lineTo(centerX + r * cos(theta), centerY + r * sin(theta))
            }
            path.close()
            path
        }
        val reusableActivePath = remember { Path() }

        // Cache center label clip path and radial vignette brush
        val cachedLabelClipPath = remember(centerX, centerY, labelRadius) {
            Path().apply {
                addOval(Rect(centerX - labelRadius, centerY - labelRadius, centerX + labelRadius, centerY + labelRadius))
            }
        }
        val cachedLabelVignetteBrush = remember(centerX, centerY, labelRadius) {
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.75f to Color(0x10000000),
                    1.00f to Color(0x45000000)
                ),
                center = center,
                radius = labelRadius
            )
        }

        val cachedDisplayedArtworkImage = remember(displayedArtwork) {
            displayedArtwork?.asImageBitmap()
        }
        val cachedOutgoingArtworkImage = remember(outgoingArtwork) {
            outgoingArtwork?.asImageBitmap()
        }

        // LAYER 1: Stationary Deck Surface & Progress Ring
        Canvas(
            modifier = Modifier.fillMaxSize()
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
                dominantTint = palette.dominantTint,
                trackPath = cachedTrackPath,
                activePath = reusableActivePath
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
                    translationY = vinylShiftAnim.value
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
                color = Color(0x1CFFFFFF),
                radius = outerRadius * 0.985f,
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )

            drawCenterLabel(
                cx = centerX,
                cy = centerY,
                labelRadius = labelRadius,
                displayedArtworkImage = cachedDisplayedArtworkImage,
                outgoingArtworkImage = cachedOutgoingArtworkImage,
                artworkAlpha = artworkAlpha,
                transitionProgress = artworkTransitionAnim.value,
                swipeDirection = activeSwipeDirection,
                labelPaperBrush = cachedLabelPaperBrush,
                palette = palette,
                albumArtGrainTexture = albumArtGrainTexture,
                labelClipPath = cachedLabelClipPath,
                labelVignetteBrush = cachedLabelVignetteBrush
            )
        }

        // LAYER 3: Stationary Detached Overhead Vinyl Sheen, Spindle Pin & Platter Rim Catch
        Canvas(modifier = Modifier.fillMaxSize()) {
            val discLeft = (centerX - outerRadius).toInt()
            val discTop = (centerY - outerRadius).toInt()
            val discDim = (outerRadius * 2f).toInt()

            // 1. Detached stationary overhead vinyl sheen floating above the rotating disc
            drawImage(
                image = vinylSheenTexture,
                dstOffset = IntOffset(discLeft, discTop),
                dstSize = IntSize(discDim, discDim)
            )

            // 2. Stationary platter rim catch
            drawCircle(
                color = Color(0x18FFFFFF),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.0.dp.toPx())
            )

            // 3. Stationary spindle pin
            drawSpindlePin(centerX, centerY, outerRadius * 0.055f, cachedSpindleBrush)
        }

        // -------------------------------------------------------------
        // UNIFIED VINYL VISUALIZER TOUCH INTERACTION LAYER
        // Implemented specifically on the vinyl visualizer to accurately
        // capture initial touch coordinates, enabling robust differentiation
        // between tap, vertical swipe-up, and vertical swipe-down gestures.
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("vinyl_visualizer_surface")
                .pointerInput(centerX, centerY, outerRadius, ringRadius, wellRadius, onVinylTap, onVinylSwipeUp, onVinylSwipeDown, onSeekFraction, onPlatterMove, onPlatterTouchEnd) {
                    val thresholdPx = 80.dp.toPx()
                    val minSwipeTimeMs = 30L
                    val maxSwipeTimeMs = 1200L

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.isConsumed) {
                            return@awaitEachGesture
                        }

                        // Capture exact initial touch coordinates relative to the vinyl visualizer
                        val initialX = down.position.x
                        val initialY = down.position.y
                        val startTime = SystemClock.uptimeMillis()

                        val dx = initialX - centerX
                        val dy = initialY - centerY
                        val distFromCenter = sqrt(dx * dx + dy * dy)

                        // 1. Initial touch point inside the Physical Vinyl Disc
                        if (distFromCenter <= outerRadius) {
                            down.consume()
                            var currentX = initialX
                            var currentY = initialY
                            var lastMoveTime = startTime
                            var isUp = false

                            while (!isUp) {
                                val event = awaitPointerEvent()
                                val change = event.changes.find { it.id == down.id } ?: event.changes.firstOrNull() ?: break
                                val newX = change.position.x
                                val newY = change.position.y
                                val now = SystemClock.uptimeMillis()
                                val dt = (now - lastMoveTime).coerceAtLeast(1L)
                                val moveDx = newX - currentX
                                val moveDy = newY - currentY
                                val moveDist = sqrt(moveDx * moveDx + moveDy * moveDy)

                                if (moveDist > 1.0f) {
                                    val velocity = (moveDist / dt.toFloat()) * 1000f
                                    onPlatterMove?.invoke(velocity)
                                }

                                currentX = newX
                                currentY = newY
                                lastMoveTime = now
                                change.consume()

                                if (change.changedToUp() || !change.pressed) {
                                    isUp = true
                                }
                            }

                            onPlatterTouchEnd?.invoke()

                            val elapsedMs = SystemClock.uptimeMillis() - startTime
                            val deltaX = currentX - initialX
                            val deltaY = currentY - initialY
                            val absX = abs(deltaX)
                            val absY = abs(deltaY)

                            val isVerticalDominant = absY > absX * 1.25f
                            val isHorizontalDominant = absX > absY * 1.25f
                            val isSwipeDistance = absY >= thresholdPx
                            val isReasonableTime = elapsedMs in minSwipeTimeMs..maxSwipeTimeMs

                            // Differentiate between tap, vertical swipe-up, and vertical swipe-down:
                            if (isSwipeDistance && isVerticalDominant && isReasonableTime) {
                                if (deltaY < -thresholdPx) {
                                    // Vertical swipe-up -> NEXT TRACK
                                    onVinylSwipeUp?.invoke()
                                } else if (deltaY > thresholdPx) {
                                    // Vertical swipe-down -> PREVIOUS TRACK
                                    onVinylSwipeDown?.invoke()
                                }
                            } else if (isHorizontalDominant && absX >= thresholdPx) {
                                // Horizontal swipe: do nothing
                            } else if (!isVerticalDominant && (absX > thresholdPx * 0.5f || absY > thresholdPx * 0.5f)) {
                                // Dominant diagonal: do nothing
                            } else {
                                // Tap (short movement or below swipe threshold): pause if playing
                                onVinylTap?.invoke()
                            }
                        } else if (distFromCenter in (outerRadius * 1.0f)..(wellRadius * 1.30f) && onSeekFraction != null) {
                            // 2. Initial touch point on the Circular Progress Ring (outside the vinyl disc)
                            down.consume()
                            var angleDeg = (atan2(dy, dx) * (180f / PI.toFloat())) + 90f
                            if (angleDeg < 0f) angleDeg += 360f
                            onSeekFraction((angleDeg / 360f).coerceIn(0f, 1f))

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.find { it.id == down.id } ?: event.changes.firstOrNull() ?: break
                                change.consume()
                                val cdx = change.position.x - centerX
                                val cdy = change.position.y - centerY
                                var curAngle = (atan2(cdy, cdx) * (180f / PI.toFloat())) + 90f
                                if (curAngle < 0f) curAngle += 360f
                                onSeekFraction((curAngle / 360f).coerceIn(0f, 1f))
                                if (change.changedToUp() || !change.pressed) break
                            }
                        }
                    }
                }
        )
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
    dominantTint: Color,
    trackPath: Path,
    activePath: Path
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

    // 1. Inactive background track path: precomputed cached path
    drawPath(
        path = trackPath,
        color = trackColor,
        style = Stroke(width = 1.0.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // 2. Active playback progress path: exactly represents currentPosition / duration
    // At 0%: empty; at 50%: half complete; at 100%: completely filled
    if (clampedProgress > 0.002f) {
        activePath.rewind()
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
    displayedArtworkImage: ImageBitmap?,
    outgoingArtworkImage: ImageBitmap?,
    artworkAlpha: Float,
    transitionProgress: Float,
    swipeDirection: Int,
    labelPaperBrush: Brush,
    palette: AlbumColorPalette,
    albumArtGrainTexture: ImageBitmap,
    labelClipPath: Path,
    labelVignetteBrush: Brush
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

    if ((displayedArtworkImage != null || outgoingArtworkImage != null) && artworkAlpha > 0.01f) {
        clipPath(labelClipPath) {
            val dstOffset = IntOffset((cx - labelRadius).toInt(), (cy - labelRadius).toInt())
            val dstSize = IntSize((labelRadius * 2f).toInt(), (labelRadius * 2f).toInt())

            // 1. Draw outgoing artwork if transitioning between tracks
            if (outgoingArtworkImage != null && transitionProgress < 0.99f) {
                val outAlpha = (1f - transitionProgress) * artworkAlpha
                val outShiftY = if (swipeDirection < 0) {
                    -transitionProgress * labelRadius * 0.40f
                } else {
                    transitionProgress * labelRadius * 0.40f
                }
                drawImage(
                    image = outgoingArtworkImage,
                    dstOffset = IntOffset(dstOffset.x, (dstOffset.y + outShiftY).toInt()),
                    dstSize = dstSize,
                    alpha = outAlpha
                )
            }

            // 2. Draw incoming artwork settling into the center
            if (displayedArtworkImage != null) {
                val inAlpha = if (outgoingArtworkImage != null) {
                    transitionProgress * artworkAlpha
                } else {
                    artworkAlpha
                }
                val inShiftY = if (outgoingArtworkImage != null && transitionProgress < 0.99f) {
                    if (swipeDirection < 0) {
                        (1f - transitionProgress) * labelRadius * 0.35f
                    } else {
                        -(1f - transitionProgress) * labelRadius * 0.35f
                    }
                } else {
                    0f
                }
                drawImage(
                    image = displayedArtworkImage,
                    dstOffset = IntOffset(dstOffset.x, (dstOffset.y + inShiftY).toInt()),
                    dstSize = dstSize,
                    alpha = inAlpha
                )
            }

            // 3. Subtle monochrome photographic print grain overlay over the album artwork
            drawImage(
                image = albumArtGrainTexture,
                dstOffset = dstOffset,
                dstSize = dstSize,
                alpha = artworkAlpha
            )

            // 4. Subtle radial paper vignette
            drawCircle(
                brush = labelVignetteBrush,
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
