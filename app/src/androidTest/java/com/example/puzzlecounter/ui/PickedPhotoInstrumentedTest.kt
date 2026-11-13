package com.example.puzzlecounter.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.puzzlecounter.analyzer.PuzzleModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Reading a photo from the gallery (rotation, size) and cutting the square out of it that the user framed. */
@RunWith(AndroidJUnit4::class)
class PickedPhotoInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()

    @After
    fun deleteFiles() = files.forEach { it.delete() }

    /** A [width] x [height] photo whose four quarters are red (top left), green (top right), blue (bottom left), white (bottom right). */
    private fun quarters(width: Int, height: Int): Bitmap = createBitmap(width, height).also {
        val canvas = Canvas(it)
        val paint = Paint()
        fun fill(color: Int, left: Int, top: Int) {
            paint.color = color
            canvas.drawRect(left.toFloat(), top.toFloat(), (left + width / 2).toFloat(), (top + height / 2).toFloat(), paint)
        }
        fill(Color.RED, 0, 0)
        fill(Color.GREEN, width / 2, 0)
        fill(Color.BLUE, 0, height / 2)
        fill(Color.WHITE, width / 2, height / 2)
    }

    private fun saveJpeg(bitmap: Bitmap, exifOrientation: Int? = null): Uri {
        val file = File(context.cacheDir, "picked_${files.size}.jpg").also { files += it }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        if (exifOrientation != null) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
                saveAttributes()
            }
        }
        return Uri.fromFile(file)
    }

    /** The colors at the centers of the four quarters, as a list: top left, top right, bottom left, bottom right. */
    private fun Bitmap.quarterColors(): List<Int> = listOf(
        getPixel(width / 4, height / 4), getPixel(width * 3 / 4, height / 4),
        getPixel(width / 4, height * 3 / 4), getPixel(width * 3 / 4, height * 3 / 4),
    )

    private fun assertColors(message: String, expected: List<Int>, actual: List<Int>) {
        expected.zip(actual).forEachIndexed { i, (want, got) ->
            val close = listOf(Color.red(want) - Color.red(got), Color.green(want) - Color.green(got), Color.blue(want) - Color.blue(got)).all { Math.abs(it) < 40 }
            assertTrue("$message: Viertel $i ist ${Integer.toHexString(got)} statt ${Integer.toHexString(want)}", close)
        }
    }

    @Test
    fun photoWithoutRotation_isReadAsItIs() {
        val photo = decodePickedPhoto(context, saveJpeg(quarters(400, 300)))!!
        assertEquals(400, photo.width)
        assertEquals(300, photo.height)
        assertColors("ohne EXIF", listOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE), photo.quarterColors())
    }

    /** What every EXIF orientation must do to the quarters (red, green, blue, white as stored), and whether it swaps width and height. */
    @Test
    fun everyExifOrientation_turnsThePhotoUpright() {
        val r = Color.RED
        val g = Color.GREEN
        val b = Color.BLUE
        val w = Color.WHITE
        val cases = listOf(
            Triple(ExifInterface.ORIENTATION_NORMAL, false, listOf(r, g, b, w)),
            Triple(ExifInterface.ORIENTATION_FLIP_HORIZONTAL, false, listOf(g, r, w, b)),
            Triple(ExifInterface.ORIENTATION_ROTATE_180, false, listOf(w, b, g, r)),
            Triple(ExifInterface.ORIENTATION_FLIP_VERTICAL, false, listOf(b, w, r, g)),
            Triple(ExifInterface.ORIENTATION_TRANSPOSE, true, listOf(r, b, g, w)),
            Triple(ExifInterface.ORIENTATION_ROTATE_90, true, listOf(b, r, w, g)),
            Triple(ExifInterface.ORIENTATION_TRANSVERSE, true, listOf(w, g, b, r)),
            Triple(ExifInterface.ORIENTATION_ROTATE_270, true, listOf(g, w, r, b)),
        )
        for ((orientation, swapped, expected) in cases) {
            val photo = decodePickedPhoto(context, saveJpeg(quarters(400, 300), orientation))!!
            assertEquals("Breite bei EXIF $orientation", if (swapped) 300 else 400, photo.width)
            assertEquals("Höhe bei EXIF $orientation", if (swapped) 400 else 300, photo.height)
            assertColors("EXIF $orientation", expected, photo.quarterColors())
        }
    }

    @Test
    fun largePhoto_isReadSmallerButKeepsItsShape() {
        val photo = decodePickedPhoto(context, saveJpeg(quarters(4000, 3000)))!!
        assertTrue("Lange Seite ${photo.width}", photo.width <= 2048)
        assertEquals(4f / 3f, photo.width.toFloat() / photo.height, 0.01f)
        assertColors("verkleinert", listOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE), photo.quarterColors())
    }

    @Test
    fun smallPhoto_isNotEnlarged() {
        val photo = decodePickedPhoto(context, saveJpeg(quarters(640, 480)))!!
        assertEquals(640, photo.width)
        assertEquals(480, photo.height)
    }

    @Test
    fun whatIsNoImage_isNotRead() {
        val text = File(context.cacheDir, "picked_text.txt").also { files += it; it.writeText("kein Foto") }
        assertNull(decodePickedPhoto(context, Uri.fromFile(text)))
        assertNull(decodePickedPhoto(context, Uri.fromFile(File(context.cacheDir, "gibt_es_nicht.jpg"))))
    }

    // --- cutting out the square ---

    /** A "photo" whose pixel (x, y) has the color rgb(x, y, 0): every pixel tells where it came from. */
    private fun coordinates(width: Int, height: Int): Bitmap = createBitmap(width, height).also { bitmap ->
        for (y in 0 until height) for (x in 0 until width) bitmap.setPixel(x, y, Color.rgb(x, y, 0))
    }

    @Test
    fun regionInsideThePhoto_isCutOutExactly() {
        val photo = coordinates(200, 150)
        val square = cropSquare(photo, PhotoRegion(left = 25f, top = 0f, side = 150f), maxSide = 1080)
        assertEquals(150, square.width)
        assertEquals(150, square.height)
        for ((x, y) in listOf(0 to 0, 149 to 0, 0 to 149, 149 to 149, 70 to 40)) {
            assertEquals("Pixel ($x, $y)", photo.getPixel(25 + x, y), square.getPixel(x, y))
        }
    }

    @Test
    fun regionBeyondThePhoto_isPaddedWithTheModelsGray() {
        val photo = coordinates(200, 150)
        val square = cropSquare(photo, PhotoRegion(left = -50f, top = -20f, side = 100f), maxSide = 1080)
        assertEquals(PuzzleModel.LETTERBOX_COLOR, square.getPixel(10, 10)) // left of and above the photo
        assertEquals(PuzzleModel.LETTERBOX_COLOR, square.getPixel(30, 10)) // above it
        assertEquals(photo.getPixel(10, 10), square.getPixel(60, 30)) // on the photo
    }

    @Test
    fun largeRegion_isShrunkToTheLimit() {
        val photo = quarters(400, 300)
        val square = cropSquare(photo, PhotoRegion(left = 0f, top = -50f, side = 400f), maxSide = 100)
        assertEquals(100, square.width)
        assertEquals(100, square.height)
        // the whole photo fits, with gray bars above and below it
        assertEquals(PuzzleModel.LETTERBOX_COLOR, square.getPixel(50, 3))
        assertColors("verkleinert", listOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE), listOf(square.getPixel(25, 35), square.getPixel(75, 35), square.getPixel(25, 65), square.getPixel(75, 65)))
    }
}
