package com.example.puzzlecounter.analyzer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** A detection with its mask and contour, before the pieces in it are counted. [rect] is in pixels of the source bitmap. */
private class Blob(val score: Float, val rect: Rect, val mask: BooleanArray, val contour: PieceContour)

/** Bounding box in model input (letterbox) coordinates. [anchor] is the index of the source prediction. */
private class BBox(
    val xmin: Float,
    val ymin: Float,
    val xmax: Float,
    val ymax: Float,
    val score: Float,
    val anchor: Int,
)

/**
 * Pre- and post-processing for the YOLO26-seg puzzle model.
 *
 * Pipeline: letterbox -> inference ([PuzzleModel]) -> decode boxes -> NMS ->
 * instance masks (mask coefficients x prototypes) -> contours -> piece count ([PieceCounting]).
 *
 * Must be called on the thread that owns [model].
 */
class TFLitePieceDetector(private val model: PuzzleModel) {

    /** Scale and padding that map the source bitmap into the model input. */
    private class Letterbox(val scale: Float, val padX: Int, val padY: Int)

    /**
     * Finds the pieces in [source] (the bitmap is not recycled).
     * Throws if the model cannot be run.
     */
    fun detectPieces(source: Bitmap): DetectionResult {
        val size = model.inputSize
        val pixels = IntArray(size * size)
        val letterbox = letterbox(source, size, pixels)
        val output = model.run(pixels)
        val boxes = applyNMS(decodeCandidates(output.detections))
        return classify(source, boxes, letterbox, output)
    }

    /** Scales [source] into a square model input with gray padding and writes its pixels to [pixels]. */
    private fun letterbox(source: Bitmap, size: Int, pixels: IntArray): Letterbox {
        val scale = minOf(size.toFloat() / source.width, size.toFloat() / source.height)
        val newW = (source.width * scale).roundToInt().coerceIn(1, size)
        val newH = (source.height * scale).roundToInt().coerceIn(1, size)
        val padX = (size - newW) / 2
        val padY = (size - newH) / 2

        val padded = createBitmap(size, size)
        Canvas(padded).apply {
            drawColor(PuzzleModel.LETTERBOX_COLOR)
            drawBitmap(
                source,
                null,
                RectF(padX.toFloat(), padY.toFloat(), (padX + newW).toFloat(), (padY + newH).toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            )
        }
        padded.getPixels(pixels, 0, size, 0, 0, size, size)
        padded.recycle()
        return Letterbox(scale, padX, padY)
    }

    /** Boxes (in letterbox pixels) of all anchors that are confident enough to count as a piece. */
    private fun decodeCandidates(det: FloatArray): List<BBox> {
        val anchors = model.numAnchors
        val size = model.inputSize.toFloat()
        val boxes = ArrayList<BBox>()
        for (a in 0 until anchors) {
            val score = det[PuzzleModel.SCORE_CHANNEL * anchors + a]
            if (score < CONFIDENCE_THRESHOLD) continue
            // Coordinates are normalized to 0..1.
            val cx = det[a] * size
            val cy = det[anchors + a] * size
            val w = det[2 * anchors + a] * size
            val h = det[3 * anchors + a] * size
            if (w < MIN_BOX_SIZE_PX || h < MIN_BOX_SIZE_PX) continue
            boxes += BBox(
                xmin = (cx - w / 2f).coerceIn(0f, size),
                ymin = (cy - h / 2f).coerceIn(0f, size),
                xmax = (cx + w / 2f).coerceIn(0f, size),
                ymax = (cy + h / 2f).coerceIn(0f, size),
                score = score,
                anchor = a
            )
        }
        return boxes
    }

    /** Builds the contour of every box, counts the pieces in them and draws all contours into the mask image. */
    private fun classify(source: Bitmap, boxes: List<BBox>, lb: Letterbox, output: ModelOutput): DetectionResult {
        val blobs = boxes.mapNotNull { box ->
            val rect = Rect(
                ((box.xmin - lb.padX) / lb.scale).toInt().coerceIn(0, source.width),
                ((box.ymin - lb.padY) / lb.scale).toInt().coerceIn(0, source.height),
                ceil((box.xmax - lb.padX) / lb.scale).toInt().coerceIn(0, source.width),
                ceil((box.ymax - lb.padY) / lb.scale).toInt().coerceIn(0, source.height)
            )
            if (rect.isEmpty) null else buildBlob(box, rect, lb, output)
        }

        val counts = PieceCounting.countPieces(blobs.map {
            BlobMask(it.rect.left, it.rect.top, it.rect.width(), it.rect.height(), it.mask, it.contour.area)
        })
        val pieces = blobs.mapIndexed { i, blob ->
            DetectedPiece(blob.contour.path, blob.rect, blob.contour.area, blob.score, counts[i])
        }

        val maskImage = createBitmap(source.width, source.height)
        val maskCanvas = Canvas(maskImage).apply { drawColor(Color.BLACK) }
        for (piece in pieces) {
            val paint = when {
                piece.pieceCount > 1 -> clusterPaint
                piece.pieceCount == 1 -> singlePaint
                else -> insidePaint
            }
            maskCanvas.drawPath(piece.path, paint.fill)
            maskCanvas.drawPath(piece.path, paint.stroke)
        }
        return DetectionResult(pieces, maskImage, source.width * source.height)
    }

    /**
     * Decodes the instance mask of [box] (sigmoid(coeffs · protos) > 0.5, cropped to the box),
     * samples it bilinearly at original image resolution inside [rect] and traces its contour.
     */
    private fun buildBlob(box: BBox, rect: Rect, lb: Letterbox, output: ModelOutput): Blob {
        val protos = output.protos
        val det = output.detections
        val anchors = model.numAnchors
        val maskDim = model.maskDim
        val pW = model.protoWidth
        val pH = model.protoHeight
        val sx = pW / model.inputSize.toFloat()
        val sy = pH / model.inputSize.toFloat()

        // Proto grid region covering the box (+1 cell for interpolation).
        val gx0 = floor(box.xmin * sx - 1f).toInt().coerceIn(0, pW - 1)
        val gx1 = ceil(box.xmax * sx + 1f).toInt().coerceIn(0, pW - 1)
        val gy0 = floor(box.ymin * sy - 1f).toInt().coerceIn(0, pH - 1)
        val gy1 = ceil(box.ymax * sy + 1f).toInt().coerceIn(0, pH - 1)
        val gw = gx1 - gx0 + 1
        val gh = gy1 - gy0 + 1

        // Mask logits = Σ coeff_k * proto_k
        val logits = FloatArray(gw * gh)
        val planeSize = pW * pH
        for (k in 0 until maskDim) {
            val c = det[(PuzzleModel.FIRST_MASK_CHANNEL + k) * anchors + box.anchor]
            if (c == 0f) continue
            for (gy in 0 until gh) {
                val row = gy0 + gy
                for (gx in 0 until gw) {
                    logits[gy * gw + gx] += c * protos[k * planeSize + row * pW + gx0 + gx]
                }
            }
        }

        // Bilinear sampling at original resolution, cropped to the box.
        val rw = rect.width()
        val rh = rect.height()
        val mask = BooleanArray(rw * rh)
        for (oy in 0 until rh) {
            val iy = (rect.top + oy + 0.5f) * lb.scale + lb.padY
            if (iy < box.ymin || iy > box.ymax) continue
            val py = (iy * sy - 0.5f - gy0).coerceIn(0f, (gh - 1).toFloat())
            val y0 = py.toInt()
            val y1 = minOf(y0 + 1, gh - 1)
            val fy = py - y0
            for (ox in 0 until rw) {
                val ix = (rect.left + ox + 0.5f) * lb.scale + lb.padX
                if (ix < box.xmin || ix > box.xmax) continue
                val px = (ix * sx - 0.5f - gx0).coerceIn(0f, (gw - 1).toFloat())
                val x0 = px.toInt()
                val x1 = minOf(x0 + 1, gw - 1)
                val fx = px - x0
                val top = logits[y0 * gw + x0] * (1 - fx) + logits[y0 * gw + x1] * fx
                val bottom = logits[y1 * gw + x0] * (1 - fx) + logits[y1 * gw + x1] * fx
                // sigmoid(v) > 0.5  <=>  v > 0
                mask[oy * rw + ox] = top * (1 - fy) + bottom * fy > 0f
            }
        }
        return Blob(box.score, rect, mask, PieceContourTracer.contourFromMask(mask, rw, rh, rect.left, rect.top, rect))
    }

    /**
     * Non-maximum suppression: keeps the best box of every group of overlapping boxes.
     * Only the [MAX_CANDIDATES] most confident boxes take part, the algorithm is quadratic.
     */
    private fun applyNMS(boxes: List<BBox>): List<BBox> {
        val byScore = boxes.sortedByDescending { it.score }
        val sorted = if (byScore.size > MAX_CANDIDATES) byScore.subList(0, MAX_CANDIDATES) else byScore
        val suppressed = BooleanArray(sorted.size)
        val selected = mutableListOf<BBox>()
        for (i in sorted.indices) {
            if (suppressed[i]) continue
            val best = sorted[i]
            selected += best
            if (selected.size >= MAX_DETECTIONS) break
            for (j in i + 1 until sorted.size) {
                if (!suppressed[j] && iou(best, sorted[j]) > NMS_IOU_THRESHOLD) suppressed[j] = true
            }
        }
        return selected
    }

    private fun iou(b1: BBox, b2: BBox): Float {
        val interW = minOf(b1.xmax, b2.xmax) - maxOf(b1.xmin, b2.xmin)
        val interH = minOf(b1.ymax, b2.ymax) - maxOf(b1.ymin, b2.ymin)
        if (interW <= 0f || interH <= 0f) return 0f
        val interArea = interW * interH
        val union = (b1.xmax - b1.xmin) * (b1.ymax - b1.ymin) + (b2.xmax - b2.xmin) * (b2.ymax - b2.ymin) - interArea
        return if (union > 0f) interArea / union else 0f
    }

    private class MaskPaint(baseColor: Int, fillAlpha: Int) {
        val fill = Paint().apply { color = baseColor; style = Paint.Style.FILL; alpha = fillAlpha; isAntiAlias = true }
        val stroke = Paint().apply { color = baseColor; style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
    }

    private val singlePaint = MaskPaint(Color.WHITE, 255)
    private val clusterPaint = MaskPaint(Color.YELLOW, 200)
    private val insidePaint = MaskPaint(Color.MAGENTA, 120)

    private companion object {
        /**
         * Detections below this confidence are not counted: they are small visible parts (a fifth to a half of a
         * piece) of pieces that other pieces cover, and the score cannot tell whether such a part is a piece of its
         * own. 0.40 keeps the counts of earlier versions; lowering it adds a few such parts per photo, raising it
         * drops covered pieces that really are there.
         */
        const val CONFIDENCE_THRESHOLD = 0.40f

        /**
         * Overlap above which two boxes are treated as the same piece. The count is stable between 0.40 and 0.45;
         * from 0.50 on, duplicate detections of one piece get through, below 0.40 neighbours start to get lost.
         */
        const val NMS_IOU_THRESHOLD = 0.45f

        /** Minimum box edge in model input pixels. */
        const val MIN_BOX_SIZE_PX = 6f
        const val MAX_CANDIDATES = 3000
        const val MAX_DETECTIONS = 1000
    }
}
