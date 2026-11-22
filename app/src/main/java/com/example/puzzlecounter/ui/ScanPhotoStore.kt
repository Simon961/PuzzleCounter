package com.example.puzzlecounter.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import com.example.puzzlecounter.analyzer.DetectionResult
import java.io.File
import kotlin.math.sqrt

/**
 * The photos of a check that are on the device, and only as long as they are needed.
 *
 * A photo is analysed in memory; the only thing written to storage is its preview, the photo with every counted
 * piece outlined and numbered (see [save]), which the user looks at while deciding whether to keep the photo. The
 * preview is deleted as soon as the photo is confirmed or replaced, and everything that is left is deleted when the
 * check ends - on the result screen, when another check starts, when the app is closed and, for what a process that was
 * ended in the middle of a check left behind, at the next start. What a check keeps are its counts (see
 * [HistoryManager]), never its photos.
 *
 * The previews are in the cache of the app: that is where the system may also delete them when storage runs low.
 */
open class ScanPhotoStore(context: Context) {
    private val dir = File(context.cacheDir, DIR_NAME)

    /**
     * Writes the preview of [photo] and returns the path of the file. The numbers run from top to bottom and from
     * left to right (in rows as tall as a piece); a group of overlapping pieces carries the numbers of all pieces it
     * stands for ("18-20"), the last number is the total. Thin outlines mark blobs that lie on a larger one and are
     * counted with it, so that the count can be checked by eye.
     */
    open fun save(photo: Bitmap, result: DetectionResult): String {
        dir.mkdirs()
        val file = File.createTempFile("preview_", ".jpg", dir)
        val annotated = annotatedCopy(photo, result)
        try {
            file.outputStream().use { annotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            annotated.recycle()
        }
        return file.absolutePath
    }

    /** Deletes the preview at [path] (nothing happens for another file, or if the path is null). */
    open fun delete(path: String?) {
        if (path == null) return
        val file = File(path)
        if (file.parentFile?.canonicalFile == dir.canonicalFile && !file.delete() && file.exists()) {
            Log.w(TAG, "Vorschau konnte nicht gelöscht werden: $path")
        }
    }

    /** Deletes every preview. */
    open fun clear() {
        dir.listFiles()?.forEach { if (!it.delete() && it.exists()) Log.w(TAG, "Vorschau konnte nicht gelöscht werden: ${it.name}") }
    }

    private fun annotatedCopy(original: Bitmap, result: DetectionResult): Bitmap {
        val bmp = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)

        val shadow = strokePaint(Color.BLACK, 5f)
        val singleFill = fillPaint(SINGLE_COLOR, 28)
        val singleStroke = strokePaint(SINGLE_COLOR, 2.5f)
        val clusterFill = fillPaint(CLUSTER_COLOR, 50)
        val clusterStroke = strokePaint(CLUSTER_COLOR, 3.5f)
        val insideStroke = strokePaint(INSIDE_COLOR, 2f)
        val numberText = textPaint(15f).apply { typeface = Typeface.DEFAULT_BOLD }

        val medianArea = result.pieces.map { it.area }.sorted().let { it.getOrElse(it.size / 2) { 0 } }
        val rowHeight = sqrt(medianArea.toFloat()).coerceAtLeast(1f)
        val inReadingOrder = result.pieces.sortedWith(compareBy({ (it.rect.exactCenterY() / rowHeight).toInt() }, { it.rect.exactCenterX() }))

        var number = 1
        for (piece in inReadingOrder) {
            val label = when (piece.pieceCount) {
                0 -> null
                1 -> "$number"
                else -> "$number-${number + piece.pieceCount - 1}"
            }
            when (piece.pieceCount) {
                0 -> canvas.drawPath(piece.path, insideStroke)
                1 -> {
                    canvas.drawPath(piece.path, singleFill)
                    canvas.drawPath(piece.path, shadow)
                    canvas.drawPath(piece.path, singleStroke)
                }
                else -> {
                    canvas.drawPath(piece.path, clusterFill)
                    canvas.drawPath(piece.path, shadow)
                    canvas.drawPath(piece.path, clusterStroke)
                }
            }
            if (label != null) {
                canvas.drawText(label, piece.rect.exactCenterX() - numberText.measureText(label) / 2f, piece.rect.exactCenterY() + numberText.textSize / 3f, numberText)
            }
            number += piece.pieceCount
        }
        return bmp
    }

    private fun strokePaint(argb: Int, width: Float) =
        Paint().apply { color = argb; style = Paint.Style.STROKE; strokeWidth = width; isAntiAlias = true }

    private fun fillPaint(argb: Int, alphaValue: Int) =
        Paint().apply { color = argb; alpha = alphaValue; style = Paint.Style.FILL }

    private fun textPaint(size: Float) =
        Paint().apply { color = Color.WHITE; textSize = size; isAntiAlias = true; setShadowLayer(3f, 1f, 1f, Color.BLACK) }

    internal companion object {
        private const val TAG = "ScanPhotoStore"
        const val DIR_NAME = "scan_photos"
        private const val JPEG_QUALITY = 92

        private const val SINGLE_COLOR = 0xFF00FF66.toInt()
        private const val CLUSTER_COLOR = 0xFFFFB300.toInt()
        private const val INSIDE_COLOR = 0xFFFF00D4.toInt()
    }
}
