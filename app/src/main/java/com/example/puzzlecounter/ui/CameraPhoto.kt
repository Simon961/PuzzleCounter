package com.example.puzzlecounter.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageProxy

private const val TAG = "CameraPhoto"

/**
 * The photo of a CameraX capture as the upright, square bitmap the app analyses.
 *
 * The viewfinder is a square (see [CaptureScreen]) and a CameraX `ViewPort` makes the photo show exactly that
 * area. For in-memory captures CameraX hands over the *full* JPEG plus the viewport as [ImageProxy.getCropRect],
 * and [ImageProxy.toBitmap] ignores that rect - so the viewport is cut out here.
 */
internal fun ImageProxy.toSquareBitmap(): Bitmap {
    val full = toBitmap()
    val rotation = imageInfo.rotationDegrees
    val photo = uprightSquare(full, cropRect, rotation)
    Log.d(TAG, "Foto ${full.width}x${full.height}, Ausschnitt $cropRect, Drehung $rotation° -> ${photo.width}x${photo.height}")
    if (photo !== full) full.recycle()
    return photo
}

/**
 * Cuts the square viewport [crop] (pixels of [full]) out of [full] and rotates it clockwise by [rotationDegrees],
 * which is what CameraX asks for to make the image buffer upright (crop first, then rotate).
 */
internal fun uprightSquare(full: Bitmap, crop: Rect, rotationDegrees: Int): Bitmap {
    val region = squareRegion(crop, full.width, full.height)
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(full, region.left, region.top, region.width(), region.height(), matrix, true)
}

/**
 * The square that is cut out of a [width] x [height] photo: the viewport [crop], or - if a device reports
 * no usable or a non-square viewport - the largest square centered in it (the whole photo if [crop] does not fit).
 */
internal fun squareRegion(crop: Rect, width: Int, height: Int): Rect {
    val bounds = Rect(0, 0, width, height)
    val area = if (!crop.isEmpty && bounds.contains(crop)) crop else bounds
    val side = minOf(area.width(), area.height())
    val left = area.left + (area.width() - side) / 2
    val top = area.top + (area.height() - side) / 2
    return Rect(left, top, left + side, top + side)
}
