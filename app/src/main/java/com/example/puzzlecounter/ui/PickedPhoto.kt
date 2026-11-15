package com.example.puzzlecounter.ui

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.example.puzzlecounter.analyzer.PuzzleModel
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "PickedPhoto"

/**
 * A picked photo is decoded with its long side at most this long. The analysis works with 1024 px (the model input),
 * so even half of such a photo is sharp enough, and every region that can be cut out is shrunk by a factor of two at
 * most (a bigger factor would skip pixels instead of averaging them).
 */
private const val MAX_PICKED_SIDE = 2048

/** The photo the user picked from the gallery, on its way into the square frame (see [PhotoFrameScreen]). */
sealed interface PickedPhoto {
    data object None : PickedPhoto

    data object Loading : PickedPhoto

    data object Failed : PickedPhoto

    /** The decoded, upright photo. It is drawn on the screen, so it is never recycled but left to the garbage collector. */
    class Ready(val bitmap: Bitmap) : PickedPhoto
}

/**
 * Decodes the photo at [uri] upright (its EXIF rotation applied) with the long side at most [maxSide] px,
 * or returns null if it cannot be read as an image.
 */
internal fun decodePickedPhoto(context: Context, uri: Uri, maxSide: Int = MAX_PICKED_SIDE): Bitmap? {
    val resolver = context.contentResolver
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > maxSide) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        upright(decoded, exifOrientation(resolver, uri))
    } catch (e: Exception) {
        Log.w(TAG, "Foto konnte nicht gelesen werden: $uri", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Foto ist zu groß: $uri", e)
        null
    }
}

/** The EXIF orientation of the photo at [uri]; a photo without readable EXIF data is taken as it is. */
private fun exifOrientation(resolver: ContentResolver, uri: Uri): Int = try {
    resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
} catch (e: Exception) {
    Log.w(TAG, "EXIF-Daten nicht lesbar: $uri", e)
    ExifInterface.ORIENTATION_NORMAL
}

/** [bitmap] turned and mirrored the way its EXIF [orientation] asks for (cameras store their photos sideways). */
private fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply { postRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply { postRotate(270f); postScale(-1f, 1f) }
        else -> return bitmap
    }
    val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (turned !== bitmap) bitmap.recycle()
    return turned
}

/**
 * The square [region] of [photo] as a bitmap of at most [maxSide] px. Where the region reaches beyond the photo
 * the bitmap has the gray of the model's own padding, so the empty space does not look like a part of the pile.
 */
internal fun cropSquare(photo: Bitmap, region: PhotoRegion, maxSide: Int): Bitmap {
    val side = region.side.roundToInt().coerceIn(1, maxSide)
    val scale = side / region.side
    val square = createBitmap(side, side)
    val canvas = Canvas(square)
    canvas.drawColor(PuzzleModel.LETTERBOX_COLOR)
    val matrix = Matrix().apply {
        postTranslate(-region.left, -region.top)
        postScale(scale, scale)
    }
    canvas.drawBitmap(photo, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    return square
}
