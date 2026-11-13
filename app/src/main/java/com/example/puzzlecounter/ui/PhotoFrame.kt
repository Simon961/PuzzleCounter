package com.example.puzzlecounter.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Where a photo lies in the square frame the user moves it in (see [PhotoFrameScreen]). [zoom] 1 means that the
 * shorter side of the photo fills the frame, below that the photo is shown whole with empty space around it (the
 * long side fits at [PhotoFrame.minZoom]), above it the photo is magnified. The center of the photo is
 * ([offsetX], [offsetY]) away from the center of the frame, measured in frame sides.
 */
internal data class PhotoPlacement(val zoom: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f)

/** The square part of a photo that the frame shows, in photo pixels. It reaches beyond the photo where the frame is empty. */
data class PhotoRegion(val left: Float, val top: Float, val side: Float)

/**
 * The geometry of moving and zooming a photo of [photoWidth] x [photoHeight] px in a square frame. Pure maths, so
 * that what the user sees in the frame and what is cut out of the photo afterwards cannot drift apart: both come
 * from [regionAt].
 */
internal class PhotoFrame(private val photoWidth: Int, private val photoHeight: Int) {

    private val shortSide = min(photoWidth, photoHeight).toFloat()

    /** The zoom at which the whole photo fits into the frame. */
    val minZoom = shortSide / max(photoWidth, photoHeight)

    /** Frame sides per photo pixel. */
    private fun scale(zoom: Float) = zoom / shortSide

    /**
     * [from] after the user moved the photo by ([panX], [panY]) and magnified it by [zoomChange] around the point
     * ([pivotX], [pivotY]), which is relative to the center of the frame; all lengths in frame sides. The photo
     * cannot leave the frame: one larger than the frame keeps covering it, a smaller one stays inside it.
     */
    fun moved(
        from: PhotoPlacement,
        panX: Float,
        panY: Float,
        zoomChange: Float = 1f,
        pivotX: Float = 0f,
        pivotY: Float = 0f,
    ): PhotoPlacement {
        val zoom = (from.zoom * zoomChange).coerceIn(minZoom, MAX_ZOOM)
        val change = zoom / from.zoom
        return PhotoPlacement(
            zoom = zoom,
            offsetX = limited(pivotX + (from.offsetX - pivotX) * change + panX, photoWidth * scale(zoom)),
            offsetY = limited(pivotY + (from.offsetY - pivotY) * change + panY, photoHeight * scale(zoom)),
        )
    }

    private fun limited(offset: Float, shownSize: Float): Float {
        val limit = abs(shownSize - 1f) / 2f
        return offset.coerceIn(-limit, limit)
    }

    /** The part of the photo that the frame shows with [placement]. */
    fun regionAt(placement: PhotoPlacement): PhotoRegion {
        val side = 1f / scale(placement.zoom)
        return PhotoRegion(
            left = photoWidth / 2f - placement.offsetX * side - side / 2f,
            top = photoHeight / 2f - placement.offsetY * side - side / 2f,
            side = side,
        )
    }

    companion object {
        /** Magnified this far, the frame shows a quarter of the photo's shorter side: more would only blur it. */
        const val MAX_ZOOM = 4f
    }
}
