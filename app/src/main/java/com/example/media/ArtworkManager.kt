package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ArtworkManager
 *
 * Efficiently loads, scales, and caches album artwork images for circular vinyl center label rendering.
 *
 * Performance Safeguards:
 * - Downsamples large bitmaps to a maximum of 512x512 pixels to preserve GPU texture memory.
 * - Utilizes an in-memory LruCache to prevent redundant network and disk decoding.
 * - Prioritizes direct in-memory [Bitmap] if already extracted by [MediaBridgeManager].
 * - Handles Android content:// URIs and remote https:// URLs seamlessly via Coil.
 */
object ArtworkManager {

    // 16MB in-memory LRU cache for downsampled circular label textures
    private val memoryCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    /**
     * Resolves and returns a downsampled, ready-to-render [Bitmap] from either
     * direct in-memory bitmap or URI.
     */
    suspend fun resolveArtworkBitmap(
        context: Context,
        directBitmap: Bitmap?,
        artworkUri: String?,
        targetSizePx: Int = 512
    ): Bitmap? = withContext(Dispatchers.IO) {
        // 1. Direct bitmap from MediaMetadata
        if (directBitmap != null) {
            val cacheKey = "bitmap_${directBitmap.generationId}"
            val cached = memoryCache.get(cacheKey)
            if (cached != null) return@withContext cached

            val scaled = if (directBitmap.width > targetSizePx || directBitmap.height > targetSizePx) {
                Bitmap.createScaledBitmap(directBitmap, targetSizePx, targetSizePx, true)
            } else {
                directBitmap
            }
            memoryCache.put(cacheKey, scaled)
            return@withContext scaled
        }

        // 2. Load from URI via Coil
        if (!artworkUri.isNullOrEmpty()) {
            val cached = memoryCache.get(artworkUri)
            if (cached != null) return@withContext cached

            val imageLoader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(artworkUri)
                .size(targetSizePx, targetSizePx)
                .scale(Scale.FIT)
                .allowHardware(false) // Must be software bitmap for Compose Skia drawImage
                .build()

            val result = imageLoader.execute(request)
            val drawable = result.drawable
            if (drawable is BitmapDrawable) {
                val bmp = drawable.bitmap
                memoryCache.put(artworkUri, bmp)
                return@withContext bmp
            }
        }

        null
    }
}

/**
 * Composable helper remembering and observing the resolved album artwork bitmap
 * for the current [NowPlayingState].
 */
@Composable
fun rememberResolvedArtwork(state: NowPlayingState): Bitmap? {
    val context = LocalContext.current
    var resolvedBitmap by remember(state.artworkBitmap, state.artworkUri) {
        mutableStateOf(state.artworkBitmap)
    }

    LaunchedEffect(state.artworkBitmap, state.artworkUri) {
        resolvedBitmap = ArtworkManager.resolveArtworkBitmap(
            context = context,
            directBitmap = state.artworkBitmap,
            artworkUri = state.artworkUri
        )
    }

    return resolvedBitmap
}

/**
 * Composable helper extracting and remembering the [AlbumColorPalette]
 * for the current artwork bitmap.
 */
@Composable
fun rememberResolvedPalette(
    artworkBitmap: Bitmap?,
    trackTitle: String = "",
    artist: String = ""
): AlbumColorPalette {
    var palette by remember(artworkBitmap, trackTitle, artist) {
        mutableStateOf(ColorExtractor.extractPalette(artworkBitmap, trackTitle, artist))
    }

    LaunchedEffect(artworkBitmap, trackTitle, artist) {
        palette = withContext(Dispatchers.Default) {
            ColorExtractor.extractPalette(artworkBitmap, trackTitle, artist)
        }
    }

    return palette
}

