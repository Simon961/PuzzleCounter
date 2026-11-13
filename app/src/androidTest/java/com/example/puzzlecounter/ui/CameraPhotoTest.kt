package com.example.puzzlecounter.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The cut-out and rotation of a camera photo: the viewfinder is a square, so the analysed image must be exactly
 * the viewport square of the sensor image, upright, on every phone (sensors differ in orientation and size).
 */
@RunWith(AndroidJUnit4::class)
class CameraPhotoTest {

    /** A "sensor image" whose pixel (x, y) has the color rgb(x, y, 0): every pixel tells where it came from. */
    private fun sensorImage(width: Int, height: Int): Bitmap = createBitmap(width, height).also { bitmap ->
        for (y in 0 until height) for (x in 0 until width) bitmap.setPixel(x, y, Color.rgb(x, y, 0))
    }

    private fun Bitmap.origin(x: Int, y: Int): Pair<Int, Int> = getPixel(x, y).let { Color.red(it) to Color.green(it) }

    /** Source pixel of output pixel ([x], [y]) for a square of [side] at ([left], [top]) rotated clockwise. */
    private fun expectedOrigin(rotation: Int, x: Int, y: Int, left: Int, top: Int, side: Int): Pair<Int, Int> = when (rotation) {
        0 -> (left + x) to (top + y)
        90 -> (left + y) to (top + side - 1 - x)
        180 -> (left + side - 1 - x) to (top + side - 1 - y)
        270 -> (left + side - 1 - y) to (top + x)
        else -> error("Drehung $rotation")
    }

    private fun assertUpright(crop: Rect, left: Int, top: Int, side: Int, width: Int = 200, height: Int = 150) {
        val sensor = sensorImage(width, height)
        for (rotation in listOf(0, 90, 180, 270)) {
            val photo = uprightSquare(sensor, crop, rotation)
            assertEquals("Breite bei $rotation°", side, photo.width)
            assertEquals("Höhe bei $rotation°", side, photo.height)
            for ((x, y) in listOf(0 to 0, side - 1 to 0, 0 to side - 1, side - 1 to side - 1, side / 2 to side / 3)) {
                assertEquals(
                    "Pixel ($x, $y) bei $rotation° und Ausschnitt $crop",
                    expectedOrigin(rotation, x, y, left, top, side),
                    photo.origin(x, y),
                )
            }
        }
    }

    @Test
    fun viewportInTheMiddleOfALandscapeSensorImage_isCutOutAndRotatedUpright() =
        assertUpright(crop = Rect(25, 0, 175, 150), left = 25, top = 0, side = 150)

    @Test
    fun viewportAtTheSideOfTheSensorImage_isUsedAsReported() =
        assertUpright(crop = Rect(0, 0, 150, 150), left = 0, top = 0, side = 150)

    @Test
    fun viewportInAPortraitSensorImage_isCutOutAndRotatedUpright() =
        assertUpright(crop = Rect(0, 25, 150, 175), left = 0, top = 25, side = 150, width = 150, height = 200)

    @Test
    fun missingViewport_fallsBackToTheCenteredSquare() =
        assertUpright(crop = Rect(0, 0, 200, 150), left = 25, top = 0, side = 150)

    @Test
    fun squareRegion_keepsAValidSquareViewport() {
        assertEquals(Rect(25, 0, 175, 150), squareRegion(Rect(25, 0, 175, 150), 200, 150))
        assertEquals(Rect(0, 0, 150, 150), squareRegion(Rect(0, 0, 150, 150), 200, 150))
    }

    @Test
    fun squareRegion_fallsBackToACenteredSquare() {
        // whole photo, empty or out-of-photo viewports -> largest centered square of the photo
        assertEquals(Rect(25, 0, 175, 150), squareRegion(Rect(0, 0, 200, 150), 200, 150))
        assertEquals(Rect(25, 0, 175, 150), squareRegion(Rect(), 200, 150))
        assertEquals(Rect(25, 0, 175, 150), squareRegion(Rect(-10, 0, 300, 150), 200, 150))
        assertEquals(Rect(0, 25, 150, 175), squareRegion(Rect(0, 0, 150, 200), 150, 200))
        // a non-square viewport -> largest square centered in it
        assertEquals(Rect(50, 10, 150, 110), squareRegion(Rect(20, 10, 180, 110), 200, 150))
    }
}
