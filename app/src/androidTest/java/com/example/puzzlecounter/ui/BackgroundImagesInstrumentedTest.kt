package com.example.puzzlecounter.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The decoded background images (see [BackgroundImages]): every background of the app can be decoded for a screen and
 * for a thumbnail, the thumbnail is a small copy, and what was decoded once is handed out again, not decoded again.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundImagesInstrumentedTest {

    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

    /** A phone screen of 1080 px: the backgrounds (1408 px wide) are needed in full size. */
    private val screenWidth = 1080

    /** The preview of a background in the settings: 100 dp at the density 3. */
    private val thumbnailWidth = 300

    @Test
    fun everyBackground_isDecodedInFullSizeForAScreen_andKept() {
        for (item in availableBackgrounds) {
            val image = BackgroundImages.load(resources, item.drawableRes, screenWidth)
            assertNotNull("${item.id}: Bild konnte nicht dekodiert werden", image)
            assertEquals("${item.id}: Breite", 1408, image!!.width)
            assertSame("${item.id}: das nächste Bild kommt aus dem Speicher", image, BackgroundImages.cached(item.drawableRes, screenWidth))
            assertSame("${item.id}: auch wenn es noch einmal verlangt wird", image, BackgroundImages.load(resources, item.drawableRes, screenWidth))
        }
    }

    @Test
    fun aThumbnail_isASmallCopy() {
        for (item in availableBackgrounds) {
            val thumbnail = BackgroundImages.load(resources, item.drawableRes, thumbnailWidth)
            assertNotNull("${item.id}: Vorschau konnte nicht dekodiert werden", thumbnail)
            assertEquals("${item.id}: Breite der Vorschau", 1408 / sampleSizeFor(1408, thumbnailWidth), thumbnail!!.width)
            assertTrue("${item.id}: Vorschau ist ${thumbnail.allocationByteCount} Byte groß", thumbnail.allocationByteCount < 2 * 1024 * 1024)
            assertTrue("${item.id}: Vorschau reicht für die Breite", thumbnail.width >= thumbnailWidth * 0.9f)
        }
    }

    @Test
    fun theSameImageForAnotherWidth_isAnotherBitmap() {
        val id = availableBackgrounds.first().drawableRes
        val full = BackgroundImages.load(resources, id, screenWidth)!!
        val small = BackgroundImages.load(resources, id, thumbnailWidth)!!
        assertTrue(small.width < full.width)
        assertSame(full, BackgroundImages.cached(id, screenWidth))
        assertSame(small, BackgroundImages.cached(id, thumbnailWidth))
    }
}
