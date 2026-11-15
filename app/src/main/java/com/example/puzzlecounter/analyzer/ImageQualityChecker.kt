package com.example.puzzlecounter.analyzer

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.scale
import kotlin.math.abs
import kotlin.math.max

/**
 * What stands in the way of counting a photo. The analysis only names the problem; the screen shows it in the
 * language of the app (see `PhotoIssueTexts`).
 */
enum class PhotoIssue {
    TOO_DARK,
    OVEREXPOSED,
    BLURRY,
    PIECES_TOO_SMALL,
    STACKED_PIECES,
    /** The photo could not be analysed. */
    ANALYSIS_FAILED,
    /** The model itself cannot run (Google services missing or out of date): another photo will not help. */
    MODEL_UNAVAILABLE,
    OUT_OF_MEMORY,
}

/** [issue] is null if the photo is good enough to be counted. */
data class QualityResult(val issue: PhotoIssue? = null) {
    val isAcceptable: Boolean get() = issue == null
}

/** What [ImageQualityChecker] measures of a photo. */
internal class PhotoMeasurements(
    /** Mean brightness, 0 (black) to 255 (white). */
    val averageLuminance: Int,
    /** Variance of the Laplacian response: low values mean a blurry photo. */
    val sharpness: Double,
)

/** Cheap sanity checks of a photo (stacked pieces, brightness, sharpness) before the user confirms it. */
class ImageQualityChecker {

    /**
     * The first problem found. Light, sharpness and the size of the pieces come before [hasHugeStacks]: a dark, blurry
     * or distant photo makes the detector merge pieces into blobs, so stacks seen in it are a symptom, not the cause.
     * [pieceEdgeShare] is [DetectionResult.typicalPieceEdgeShare], null if unknown.
     */
    fun checkQuality(bitmap: Bitmap, hasHugeStacks: Boolean, pieceEdgeShare: Float? = null): QualityResult {
        val measurements = measure(bitmap)
        return when {
            measurements.averageLuminance < MIN_LUMINANCE -> QualityResult(PhotoIssue.TOO_DARK)
            measurements.averageLuminance > MAX_LUMINANCE -> QualityResult(PhotoIssue.OVEREXPOSED)
            measurements.sharpness < MIN_SHARPNESS -> QualityResult(PhotoIssue.BLURRY)
            pieceEdgeShare != null && pieceEdgeShare < MIN_PIECE_EDGE_SHARE -> QualityResult(PhotoIssue.PIECES_TOO_SMALL)
            hasHugeStacks -> QualityResult(PhotoIssue.STACKED_PIECES)
            else -> QualityResult()
        }
    }

    internal fun measure(bitmap: Bitmap): PhotoMeasurements {
        val scale = ANALYSIS_SIZE / max(bitmap.width, bitmap.height)
        val small = if (scale < 1f) {
            bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
        } else {
            bitmap
        }

        val width = small.width
        val height = small.height
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== bitmap) small.recycle()

        var totalLuminance = 0L
        val grays = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            // ITU-R BT.601 luma weights
            val lum = (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toInt()
            grays[i] = lum
            totalLuminance += lum
        }

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
            averageLuminance = (totalLuminance / pixels.size).toInt(),
            sharpness = (laplaceSqSum.toDouble() / laplaceCount) - (mean * mean),
        )
    }

    /**
     * The limits come from four sharp, well lit photos of a pile of 200 pieces (brightness about 150, sharpness
     * about 1400 at 1024 px) that were darkened, brightened and blurred to see where the counted number of pieces
     * goes wrong (it varies by about 3 even for harmless changes). The model takes much more than the limits allow:
     * from brightness 30 to 210 and up to 2 px of Gaussian blur (sharpness 130) the count is off by 4 pieces at most,
     * but 3 px of blur (sharpness 35) cost up to 14 pieces, brightness 235 added up to 16, and at 17 or 250 between
     * a quarter and two thirds of the pieces are lost. The limits stay narrower on purpose: a really dark photo is
     * also noisy (noise of 16 gray levels added up to 16 pieces) and usually blurred by the long exposure, and the
     * shake of the hand hurts the count far more than the sharpness shows (13 px of motion blur cost up to 23 pieces
     * at sharpness 140 to 280, 9 px up to 7 at 230 to 430).
     *
     * The size of the pieces comes from the same kind of experiment: the pile was shrunk on the table, as if it was
     * photographed from farther away. A piece edge (square root of the typical piece area) of 3.8 to 4.1 % of the
     * model input is what a pile of 200 pieces filling the photo shows. The count held within 3 pieces down to 2.0 %
     * in every one of the four photos, but the step from fine to failing is steep: at 1.8 to 1.9 % three of the four
     * photos were already off by 7 to 13 pieces, at 1.7 % by 11 to 21, and below that nothing was left of it. The
     * limit lies a tenth above the smallest size that counted well (2.2 % against 2.0 %), because other puzzles and
     * lighting may move the step upwards.
     */
    private companion object {
        /** The photo is shrunk to this edge length before it is analysed. */
        const val ANALYSIS_SIZE = 400f
        const val MIN_LUMINANCE = 50
        const val MAX_LUMINANCE = 225
        const val MIN_SHARPNESS = 250.0

        /** Smallest typical piece edge, as a share of the model input, that the model counts reliably. */
        const val MIN_PIECE_EDGE_SHARE = 0.022f
    }
}
