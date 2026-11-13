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
        assertEquals("Zu dunkel! Bitte für mehr Licht sorgen.", checker.checkQuality(it, hasHugeStacks = false).warningMessage)
    }

    @Test
    fun overexposedPhoto_isRejected() = scaledBrightness(2.0f).use {
        assertEquals("Überbelichtet! Blendendes Licht vermeiden.", checker.checkQuality(it, hasHugeStacks = false).warningMessage)
    }

    @Test
    fun blurryPhoto_isRejected() = blurred().use {
        assertEquals("Foto ist unscharf! Bitte mit ruhiger Hand neu aufnehmen oder ein schärferes Foto wählen.", checker.checkQuality(it, hasHugeStacks = false).warningMessage)
    }

    @Test
    fun stackedPieces_areReportedOnASharpPhoto() {
        assertEquals(
            "Teile liegen übereinander! Bitte flach nebeneinander ausbreiten.",
            checker.checkQuality(photo, hasHugeStacks = true).warningMessage
        )
    }

    @Test
    fun lightAndSharpness_comeBeforeStacks() = blurred().use {
        // a blurry photo merges pieces into blobs: the cause (blur) is reported, not the symptom (stacks)
        assertEquals("Foto ist unscharf! Bitte mit ruhiger Hand neu aufnehmen oder ein schärferes Foto wählen.", checker.checkQuality(it, hasHugeStacks = true).warningMessage)
    }
}
