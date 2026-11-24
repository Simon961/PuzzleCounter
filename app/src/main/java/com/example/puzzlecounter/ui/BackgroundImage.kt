package com.example.puzzlecounter.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * By how much an image of [imageWidth] px is shrunk while it is decoded (a power of two, as `inSampleSize` wants it)
 * so that it is still about as wide as the [targetWidth] px it is shown in. Up to a tenth of the target may be missing,
 * which is not seen under the dark layer that lies on every background; sharper than the screen is never needed.
 */
internal fun sampleSizeFor(imageWidth: Int, targetWidth: Int): Int {
    val needed = maxOf(targetWidth * MIN_SHARE_OF_TARGET, MIN_DECODED_WIDTH)
    var sample = 1
    while (imageWidth / (sample * 2) >= needed) sample *= 2
    return sample
}

/** The decoded image may be this share as wide as the place it is shown in (it is then enlarged by a tenth). */
private const val MIN_SHARE_OF_TARGET = 0.9f

/** No image is shrunk below this width, whatever place (or none) it is for. */
private const val MIN_DECODED_WIDTH = 64f

/**
 * The background images of the app, decoded once and kept.
 *
 * The backgrounds are 1408 x 3040 px JPEGs of 2 to 2.6 MB: decoding one takes tens of milliseconds and 17 MB of memory
 * (more on a slow phone), and `painterResource` does that again for every screen that is composed - on the main thread,
 * in the first frame of every transition, and five times when the background settings with their four thumbnails
 * open. Here every image is decoded off the main thread, no larger than the place it is shown in needs (the thumbnails
 * get a small copy), and the last ones are kept for the next screen.
 */
internal object BackgroundImages {

    private data class Key(@param:DrawableRes val id: Int, val targetWidth: Int)

    private const val CACHE_BYTES = 40 * 1024 * 1024

    private val cache = object : LruCache<Key, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: Key, value: Bitmap) = value.allocationByteCount
    }

    /** The image if it was decoded before, otherwise null. Cheap, for the main thread. */
    fun cached(@DrawableRes id: Int, targetWidth: Int): Bitmap? = cache.get(Key(id, targetWidth))

    /**
     * The image [id] for a place [targetWidth] px wide: from the cache, or decoded now. Slow, not for the main thread.
     * Null if it cannot be decoded (not enough memory, for example): the screen then has no background image.
     * The bitmaps are drawn on the screen and shared, so they are never recycled but left to the garbage collector.
     */
    fun load(resources: Resources, @DrawableRes id: Int, targetWidth: Int): Bitmap? {
        val key = Key(id, targetWidth)
        cache.get(key)?.let { return it }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, id, bounds)
            if (bounds.outWidth <= 0) return null
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inSampleSize = sampleSizeFor(bounds.outWidth, targetWidth)
            }
            BitmapFactory.decodeResource(resources, id, options)?.also { cache.put(key, it) }
        } catch (_: OutOfMemoryError) {
            null
        }
    }
}

/**
 * The background image [id] for a place [targetWidthPx] wide, or null while it is being decoded. A screen that comes
 * after another one finds the image in the cache at once; after the choice of another background the old image stays
 * until the new one is there.
 */
@Composable
internal fun rememberBackgroundBitmap(@DrawableRes id: Int, targetWidthPx: Int): Bitmap? {
    val resources = LocalResources.current
    var bitmap by remember { mutableStateOf(BackgroundImages.cached(id, targetWidthPx)) }
    LaunchedEffect(id, targetWidthPx) {
        val ready = BackgroundImages.cached(id, targetWidthPx)
            ?: withContext(Dispatchers.IO) { BackgroundImages.load(resources, id, targetWidthPx) }
        if (ready != null) bitmap = ready
    }
    return bitmap
}

/** A background image that fills its place (cropped, not distorted) and fades in when it is ready. */
@Composable
internal fun BackgroundImage(
    @DrawableRes id: Int,
    targetWidthPx: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val bitmap = rememberBackgroundBitmap(id, targetWidthPx)
    Crossfade(targetState = bitmap, modifier = modifier, animationSpec = tween(durationMillis = 250)) { shown ->
        if (shown != null) {
            val image = remember(shown) { shown.asImageBitmap() }
            Image(bitmap = image, contentDescription = contentDescription, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/**
 * The width of the screen in pixels, which is how wide the background of a screen is shown. From the configuration,
 * which has the width of the window (also in split screen) from the first composition on; the container size of the
 * window info that Compose's lint check would like instead is not guaranteed to be known by then, and a background that
 * is decoded for a width of 0 would be a tiny one.
 */
@Suppress("ConfigurationScreenWidthHeight")
@Composable
internal fun screenWidthPx(): Int = (LocalConfiguration.current.screenWidthDp * LocalDensity.current.density).toInt()
