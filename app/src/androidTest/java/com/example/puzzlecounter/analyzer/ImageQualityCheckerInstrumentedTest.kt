package com.example.puzzlecounter.analyzer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ------------------------------------------------------------------ a few pieces on a plain table

    /** Where the six sharp snapshots of the photo (70 px squares of the pile) are put on the table: well apart. */
    private val spots = listOf(120 to 150, 520 to 90, 800 to 300, 200 to 520, 600 to 640, 860 to 820)

    /** The six snapshots (pieces with their bit of table) of [photo] at those [spots] on a [table] of 1000 x 1000 px. */
    private fun fewPiecesOn(table: Int): Pair<Bitmap, List<Rect>> {
        val scene = createBitmap(1000, 1000)
        val canvas = Canvas(scene)
        canvas.drawColor(table)
        val sources = listOf(240 to 140, 560 to 300, 120 to 600, 700 to 520, 330 to 760, 880 to 140)
        val boxes = spots.zip(sources).map { (spot, source) ->
            val from = Rect(source.first, source.second, source.first + 70, source.second + 70)
            val to = Rect(spot.first, spot.second, spot.first + 70, spot.second + 70)
            canvas.drawBitmap(photo, from, to, null)
            to
        }
        return scene to boxes
    }

    @Test
    fun fewPiecesOnAWhiteSheet_areAcceptedWhenTheirBoxesAreKnown() {
        val (scene, boxes) = fewPiecesOn(Color.rgb(245, 245, 245))
        scene.use {
            // the empty sheet makes the photo bright: judged as a whole it is "overexposed" ...
            assertEquals(PhotoIssue.OVEREXPOSED, checker.checkQuality(it, hasHugeStacks = false).issue)
            // ... but the pieces on it are sharp and well lit
            val onPieces = checker.measure(it, boxes)
            assertTrue(onPieces.measuredOnPieces)
            assertTrue("Helligkeit ${onPieces.averageLuminance}", onPieces.averageLuminance in 100..200)
            assertTrue("Schärfe ${onPieces.sharpness}", onPieces.sharpness >= 250)
            assertTrue(checker.checkQuality(it, hasHugeStacks = false, pieceBoxes = boxes).isAcceptable)
        }
    }

    @Test
    fun fewPiecesOnAGrayTable_areAcceptedWhenTheirBoxesAreKnown() {
        val (scene, boxes) = fewPiecesOn(Color.rgb(160, 130, 100))
        scene.use {
            assertEquals(PhotoIssue.BLURRY, checker.checkQuality(it, hasHugeStacks = false).issue)
            assertTrue(checker.checkQuality(it, hasHugeStacks = false, pieceBoxes = boxes).isAcceptable)
        }
    }

    @Test
    fun fewBlurredOrDarkPieces_areStillRejected() {
        val (scene, boxes) = fewPiecesOn(Color.rgb(160, 130, 100))
        scene.use {
            val small = Bitmap.createScaledBitmap(it, it.width / 6, it.height / 6, true)
            val blurry = Bitmap.createScaledBitmap(small, it.width, it.height, true)
            small.recycle()
            blurry.use { b ->
                assertEquals(PhotoIssue.BLURRY, checker.checkQuality(b, hasHugeStacks = false, pieceBoxes = boxes).issue)
            }
            val dark = createBitmap(it.width, it.height).also { out ->
                val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.15f, 0.15f, 0.15f, 1f) }) }
                Canvas(out).drawBitmap(it, 0f, 0f, paint)
            }
            dark.use { d ->
                assertEquals(PhotoIssue.TOO_DARK, checker.checkQuality(d, hasHugeStacks = false, pieceBoxes = boxes).issue)
            }
        }
    }

    @Test
    fun aDensePile_isStillMeasuredAsAWhole() {
        // pieces that cover a large part of the photo: the same numbers as without boxes, to the last digit
        val boxes = (0 until 12).flatMap { row -> (0 until 12).map { col -> Rect(col * 85, row * 85, col * 85 + 80, row * 85 + 80) } }
        val whole = checker.measure(photo)
        val withBoxes = checker.measure(photo, boxes)
        assertFalse(withBoxes.measuredOnPieces)
        assertEquals(whole.averageLuminance, withBoxes.averageLuminance)
        assertEquals(whole.sharpness, withBoxes.sharpness, 0.0)
    }
}
