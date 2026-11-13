package com.example.puzzlecounter.analyzer

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.scale
import kotlin.math.abs
import kotlin.math.max

/** [warningMessage] is null if the photo is good enough to be counted. */
data class QualityResult(val warningMessage: String? = null) {
    val isAcceptable: Boolean get() = warningMessage == null
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
     * The first problem found. Light and sharpness come before [hasHugeStacks]: a dark or blurry photo makes the
     * detector merge pieces into blobs, so stacks seen in it are a symptom, not the cause.
     */
    fun checkQuality(bitmap: Bitmap, hasHugeStacks: Boolean): QualityResult {
        val measurements = measure(bitmap)
        return when {
            measurements.averageLuminance < MIN_LUMINANCE -> QualityResult("Zu dunkel! Bitte für mehr Licht sorgen.")
            measurements.averageLuminance > MAX_LUMINANCE -> QualityResult("Überbelichtet! Blendendes Licht vermeiden.")
            measurements.sharpness < MIN_SHARPNESS -> QualityResult("Foto ist unscharf! Bitte mit ruhiger Hand neu aufnehmen oder ein schärferes Foto wählen.")
            hasHugeStacks -> QualityResult("Teile liegen übereinander! Bitte flach nebeneinander ausbreiten.")
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
     * about 1800) that were darkened, brightened and blurred (Gaussian) to see where the counted number of pieces
     * goes wrong (it varies by about 3 even for harmless changes): at brightness 45 it was off by up to 11 pieces,
     * at 30 by up to 17, at 235 by up to 9; with 1 px of blur (sharpness 620) it was off by up to 10, with 1.5 px
     * (290) by up to 9, with 2 px (140) by up to 13.
     */
    private companion object {
        /** The photo is shrunk to this edge length before it is analysed. */
        const val ANALYSIS_SIZE = 400f
        const val MIN_LUMINANCE = 50
        const val MAX_LUMINANCE = 225
        const val MIN_SHARPNESS = 250.0
    }
}
