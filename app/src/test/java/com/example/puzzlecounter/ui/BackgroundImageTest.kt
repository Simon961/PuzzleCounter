package com.example.puzzlecounter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** By how much a background image is shrunk while it is decoded (the backgrounds of the app are 1408 px wide). */
class BackgroundImageTest {

    private val image = 1408

    @Test
    fun aScreenAsWideAsTheImageOrWiderGetsItInFullSize() {
        assertEquals(1, sampleSizeFor(image, 1408))
        assertEquals(1, sampleSizeFor(image, 1440))
        assertEquals(1, sampleSizeFor(image, 2560))
        assertEquals(1, sampleSizeFor(500, 1080))
    }

    @Test
    fun aCurrentPhoneScreenGetsTheFullImage() {
        // 1080 px: half of the image (704 px) would have to be enlarged by more than a tenth
        assertEquals(1, sampleSizeFor(image, 1080))
        assertEquals(1, sampleSizeFor(image, 1220)) // 704 px is 58 % of 1220 px
    }

    @Test
    fun aNarrowScreenGetsAHalfSizedImage() {
        assertEquals(2, sampleSizeFor(image, 720)) // 704 px for 720 px
        assertEquals(2, sampleSizeFor(image, 780)) // 704 px is 90 % of 780 px: just enough
        assertEquals(1, sampleSizeFor(image, 790))
    }

    @Test
    fun aThumbnailGetsASmallCopy() {
        assertEquals(4, sampleSizeFor(image, 300)) // 100 dp at the density 3: 352 px
        assertEquals(2, sampleSizeFor(image, 400))
        assertEquals(8, sampleSizeFor(image, 150)) // 176 px
    }

    @Test
    fun theResultIsAPowerOfTwo_andNeverTooSmallForThePlace() {
        for (target in 1..3000) {
            val sample = sampleSizeFor(image, target)
            assertEquals("target $target", 1, Integer.bitCount(sample))
            val decoded = image / sample
            // never less than 90 % of the place, unless the image is smaller than that anyway or the floor applies
            assertTrue("target $target: $decoded px", decoded >= minOf(image, (target * 0.9f).toInt()) || decoded >= 64)
        }
    }

    @Test
    fun noPlaceAtAllDoesNotShrinkTheImageToNothing() {
        for (target in listOf(0, -5)) {
            val decoded = image / sampleSizeFor(image, target)
            assertTrue("target $target: $decoded px", decoded >= 64)
        }
    }
}
