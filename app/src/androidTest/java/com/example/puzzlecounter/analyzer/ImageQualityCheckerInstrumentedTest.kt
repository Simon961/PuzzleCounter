package com.example.puzzlecounter.analyzer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The photo checks, on a real photo of a pile of pieces (sharp, brightness about 150) and degraded copies of it. */
@RunWith(AndroidJUnit4::class)
class ImageQualityCheckerInstrumentedTest {

    private val checker = ImageQualityChecker()
    private lateinit var photo: Bitmap

    @Before
    fun loadPhoto() {
        photo = InstrumentationRegistry.getInstrumentation().context.assets.open("real_piles/pile_1.jpg").use {
            BitmapFactory.decodeStream(it)!!
        }
    }

    @After
    fun recyclePhoto() = photo.recycle()

    /** Every colour channel multiplied by [factor]. */
    private fun scaledBrightness(factor: Float): Bitmap = createBitmap(photo.width, photo.height).also { out ->
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(factor, factor, factor, 1f) }) }
        Canvas(out).drawBitmap(photo, 0f, 0f, paint)
    }

    /** Shrunk to a sixth and enlarged again: strongly blurred. */
    private fun blurred(): Bitmap {
        val small = Bitmap.createScaledBitmap(photo, photo.width / 6, photo.height / 6, true)
        return Bitmap.createScaledBitmap(small, photo.width, photo.height, true).also { small.recycle() }
    }

    private inline fun <T> Bitmap.use(block: (Bitmap) -> T): T = try { block(this) } finally { recycle() }

    @Test
    fun sharpWellLitPhoto_isAccepted() {
        val measurements = checker.measure(photo)
        assertTrue("Helligkeit ${measurements.averageLuminance}", measurements.averageLuminance in 120..190)
        assertTrue("Schärfe ${measurements.sharpness}", measurements.sharpness > 1000)
        assertTrue(checker.checkQuality(photo, hasHugeStacks = false).isAcceptable)
    }

    @Test
    fun darkPhoto_isRejected() = scaledBrightness(0.2f).use {
        assertEquals(PhotoIssue.TOO_DARK, checker.checkQuality(it, hasHugeStacks = false).issue)
    }

    @Test
    fun overexposedPhoto_isRejected() = scaledBrightness(2.0f).use {
        assertEquals(PhotoIssue.OVEREXPOSED, checker.checkQuality(it, hasHugeStacks = false).issue)
    }

    @Test
    fun blurryPhoto_isRejected() = blurred().use {
        assertEquals(PhotoIssue.BLURRY, checker.checkQuality(it, hasHugeStacks = false).issue)
    }

    @Test
    fun stackedPieces_areReportedOnASharpPhoto() {
        assertEquals(PhotoIssue.STACKED_PIECES, checker.checkQuality(photo, hasHugeStacks = true).issue)
    }

    @Test
    fun lightAndSharpness_comeBeforeStacks() = blurred().use {
        // a blurry photo merges pieces into blobs: the cause (blur) is reported, not the symptom (stacks)
        assertEquals(PhotoIssue.BLURRY, checker.checkQuality(it, hasHugeStacks = true).issue)
    }

    @Test
    fun tooSmallPieces_areReported_butNotWhenTheyAreBigEnough() {
        assertEquals(PhotoIssue.PIECES_TOO_SMALL, checker.checkQuality(photo, hasHugeStacks = false, pieceEdgeShare = 0.015f).issue)
        // a pile of 200 pieces that fills the photo: 4 % of the model input
        assertTrue(checker.checkQuality(photo, hasHugeStacks = false, pieceEdgeShare = 0.039f).isAcceptable)
        // not known (too few pieces to tell): no judgement
        assertTrue(checker.checkQuality(photo, hasHugeStacks = false, pieceEdgeShare = null).isAcceptable)
    }

    @Test
    fun smallPieces_comeBeforeStacks_andAfterLightAndSharpness() {
        // distant pieces make the detector merge pieces into blobs: the cause is reported, not the symptom
        assertEquals(PhotoIssue.PIECES_TOO_SMALL, checker.checkQuality(photo, hasHugeStacks = true, pieceEdgeShare = 0.015f).issue)
        blurred().use {
            assertEquals(PhotoIssue.BLURRY, checker.checkQuality(it, hasHugeStacks = true, pieceEdgeShare = 0.015f).issue)
        }
    }
}
