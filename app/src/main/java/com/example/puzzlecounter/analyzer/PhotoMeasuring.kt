package com.example.puzzlecounter.analyzer

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A photo as a plane of gray values (ITU-R BT.601 luma, 0 to 255), [width] x [height] pixels, row by row. */
internal class GrayPlane(val width: Int, val height: Int, val grays: IntArray) {
    init {
        require(grays.size == width * height) { "${grays.size} gray values for $width x $height pixels" }
    }
}

/** What [ImageQualityChecker] measures of a photo. */
internal class PhotoMeasurements(
    /** Mean brightness, 0 (black) to 255 (white). */
    val averageLuminance: Int,
    /** Variance of the Laplacian response: low values mean a blurry photo. */
    val sharpness: Double,
    /** True if only the places of the pieces were measured, false if the whole photo was. */
    val measuredOnPieces: Boolean = false,
)

/** The box of a piece in pixels of the photo; [right] and [bottom] are the first pixels outside the box. */
internal class PieceBox(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Brightness and sharpness of a photo, measured over the whole photo or - if a few pieces of normal size cover little
 * of it - only where the pieces are.
 *
 * Both limits of [ImageQualityChecker] were found with piles of about 200 pieces that cover a quarter to a third of the
 * photo (the core of their boxes, see below: 26 to 31 %). There the background hardly matters. A photo of a few pieces
 * on a table is different: the empty table is flat, so the Laplacian variance of the whole photo is diluted by it in
 * proportion to the share it takes, and the brightness is the table's. In experiments with real pieces cut out of the
 * four test photos and put on a real table texture, a white sheet and a gray or dark mat (110 scenes of 3 to 144 pieces,
 * all counted within 3 pieces of the truth), the whole-photo check rejected 81 of them:
 *  - 43 as blurry: the sharpness of the whole photo was 14 to 246 (limit 250), that of the pieces' region 365 to 3 425;
 *    a scene blurred by 2 px measured 80 to 155 in its region, one blurred by 3 px 22 to 36;
 *  - 38 as overexposed: the brightness of the whole photo was 227 to 244 (limit 225), that of the pieces' region 145 to
 *    179; a scene that really is too bright measured 223 to 227 there, one that really is too dark 25.
 *
 * So where the pieces' region covers less than [SPARSE_COVERAGE] of the photo - less than the least covering clean
 * pile (0.26) with a good margin - brightness and sharpness are measured in that region, with the same limits. Where it
 * covers more, the whole photo is measured exactly as before: of the four test piles and 56 degraded versions of them
 * (blurred, darkened, brightened, noisy, shaken) not one verdict changed.
 *
 * The region is the union of the pieces' boxes, each shrunk by [REGION_SHRINK] on every side: it hugs the pieces, so
 * that a white or dark table around them does not shift the brightness, but still holds parts of their outlines, where
 * the sharpness is (also for plain blue pieces on a beech table, which hardly differ in brightness from it: 510 to 860).
 *
 * The whole photo also held back photos with tiny pieces, by accident (a pile photographed from far away is mostly
 * empty table, hence "blurry"). The region is therefore only trusted if the pieces are of a size the model counts
 * reliably ([MIN_BOX_EDGE_SHARE]); with smaller ones the whole photo is measured, as before.
 *
 * What this does not catch:
 *  - A whole pile that fills less than about a seventh of the photo's width may be found by the model as one to five
 *    large blobs, which look like a few ordinary pieces (8 of 72 shrunk piles in the PC experiment, count 1 to 5 for 205
 *    pieces; 0 of 7 on the phone, where the GPU delegate found other blobs).
 *  - The sharpness limit comes from piles on a beech table. On a high-contrast background the region stays sharper than
 *    the count survives: ten pieces on a white sheet blurred by 1.5 px (region 579), by 9 or 13 px of motion (776, 272)
 *    or shrunk to a sixth and enlarged again (287) were counted as 8, 7, 4 and 4 and accepted, ten on a beech table with
 *    13 px of motion (322) as 8. The model's own confidence in these scenes (mean score 0.62 to 0.80, 0.92 for the sharp
 *    ones) would flag them; it is not used because it was calibrated on pieces of one puzzle only.
 */
internal object PhotoMeasuring {

    /** Pieces whose region covers less than this share of the photo are measured on their own region. */
    const val SPARSE_COVERAGE = 0.20

    /** Each side of a piece's box is moved this share of the box inwards. */
    const val REGION_SHRINK = 0.15f

    /** A region of fewer pixels (at the analysis size) says too little about the photo; the whole photo is measured. */
    const val MIN_REGION_PIXELS = 100

    /**
     * The typical box edge, as a share of the long side of the photo, below which the region is not trusted. It is the
     * box edge that goes with the smallest piece edge the model counts reliably (2.2 % of the model input, see
     * [ImageQualityChecker]): the square root of a piece's mask area is 0.77 to 0.78 times that of its box (median over
     * the 790 single pieces of the four test piles), so 0.022 / 0.775 = 2.84 %.
     */
    const val MIN_BOX_EDGE_SHARE = 0.028f

    /**
     * Measures [plane], the photo of [photoWidth] x [photoHeight] pixels shrunk for the analysis, in which [boxes]
     * (given in pixels of the photo) mark the pieces. Without boxes, with pieces that cover a large part of the photo
     * or with pieces that are too small, the whole photo is measured.
     */
    fun measure(plane: GrayPlane, photoWidth: Int, photoHeight: Int, boxes: List<PieceBox>): PhotoMeasurements {
        val whole = wholePhoto(plane)
        if (boxes.isEmpty() || typicalBoxEdge(boxes) < MIN_BOX_EDGE_SHARE * max(photoWidth, photoHeight)) return whole
        val region = pieceRegion(plane, photoWidth, photoHeight, boxes)
        val covered = region.count { it }
        if (covered < MIN_REGION_PIXELS || covered.toDouble() / region.size >= SPARSE_COVERAGE) return whole
        return measureRegion(plane, region) ?: whole
    }

    /** Brightness and sharpness of all of [plane]. */
    fun wholePhoto(plane: GrayPlane): PhotoMeasurements {
        val width = plane.width
        val height = plane.height
        val grays = plane.grays
        var totalLuminance = 0L
        for (gray in grays) totalLuminance += gray

        var laplaceSum = 0L
        var laplaceSqSum = 0L
        var laplaceCount = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val laplace = abs(grays[i - width] + grays[i + width] + grays[i - 1] + grays[i + 1] - 4 * grays[i])
                laplaceSum += laplace
                laplaceSqSum += laplace * laplace
                laplaceCount++
            }
        }
        val mean = laplaceSum.toDouble() / laplaceCount
        return PhotoMeasurements(
            averageLuminance = (totalLuminance / grays.size).toInt(),
            sharpness = (laplaceSqSum.toDouble() / laplaceCount) - (mean * mean),
        )
    }

    /**
     * Brightness and sharpness of the pixels of [plane] that [region] marks (the Laplacian of a pixel may reach one
     * pixel beyond the region). Null if the region holds no pixel whose Laplacian can be formed.
     */
    private fun measureRegion(plane: GrayPlane, region: BooleanArray): PhotoMeasurements? {
        val width = plane.width
        val height = plane.height
        val grays = plane.grays
        var totalLuminance = 0L
        var pixels = 0
        for (i in grays.indices) {
            if (region[i]) {
                totalLuminance += grays[i]
                pixels++
            }
        }
        if (pixels == 0) return null

        var laplaceSum = 0L
        var laplaceSqSum = 0L
        var laplaceCount = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                if (!region[i]) continue
                val laplace = abs(grays[i - width] + grays[i + width] + grays[i - 1] + grays[i + 1] - 4 * grays[i])
                laplaceSum += laplace
                laplaceSqSum += laplace * laplace
                laplaceCount++
            }
        }
        if (laplaceCount == 0) return null
        val mean = laplaceSum.toDouble() / laplaceCount
        return PhotoMeasurements(
            averageLuminance = (totalLuminance / pixels).toInt(),
            sharpness = (laplaceSqSum.toDouble() / laplaceCount) - (mean * mean),
            measuredOnPieces = true,
        )
    }

    /** The median edge of the [boxes] (square root of the box area), in pixels of the photo. */
    private fun typicalBoxEdge(boxes: List<PieceBox>): Double {
        val edges = boxes.map { sqrt((it.right - it.left).toDouble() * (it.bottom - it.top)) }.sorted()
        val middle = edges.size / 2
        return if (edges.size % 2 == 1) edges[middle] else (edges[middle - 1] + edges[middle]) / 2
    }

    /** The pixels of [plane] that lie in one of the [boxes] (shrunk by [REGION_SHRINK]); true where a piece is. */
    private fun pieceRegion(plane: GrayPlane, photoWidth: Int, photoHeight: Int, boxes: List<PieceBox>): BooleanArray {
        val width = plane.width
        val height = plane.height
        val scaleX = width.toFloat() / photoWidth
        val scaleY = height.toFloat() / photoHeight
        val region = BooleanArray(width * height)
        for (box in boxes) {
            val boxWidth = (box.right - box.left) * scaleX
            val boxHeight = (box.bottom - box.top) * scaleY
            val left = max(0, (box.left * scaleX + REGION_SHRINK * boxWidth).toInt())
            val top = max(0, (box.top * scaleY + REGION_SHRINK * boxHeight).toInt())
            val right = min(width, ceil(box.right * scaleX - REGION_SHRINK * boxWidth).toInt())
            val bottom = min(height, ceil(box.bottom * scaleY - REGION_SHRINK * boxHeight).toInt())
            if (right <= left) continue
            for (y in top until bottom) region.fill(true, y * width + left, y * width + right)
        }
        return region
    }
}
