package com.example.puzzlecounter.ui

import android.graphics.BitmapFactory
import android.util.TypedValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for the background images of the app.
 *
 * 1. A real crash: `painterResource()` threw `IllegalArgumentException: Only VectorDrawables and
 *    rasterized asset types are supported` because the `app_background*` files were JPEG data saved
 *    under a `.png` extension. `painterResource()` decides vector vs. raster purely by the resource
 *    path suffix, so every background must resolve to a raster file that [BitmapFactory] can decode.
 * 2. Memory: images in a density-less `drawable` folder count as mdpi and are upscaled by the screen
 *    density (3x on most phones = 9x the pixels, 150 MB per image). They belong into `drawable-nodpi`.
 *
 * Does not use the Compose UI test framework (its Espresso link is broken on very new Android versions).
 */
@RunWith(AndroidJUnit4::class)
class BackgroundDrawableAssetTest {

    @Test
    fun allBackgroundDrawables_resolveAsRasterAssets_andDecodeWithoutDensityScaling() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

        assertTrue("Es sollten mehrere Hintergründe konfiguriert sein", availableBackgrounds.isNotEmpty())

        for (item in availableBackgrounds) {
            val value = TypedValue()
            resources.getValue(item.drawableRes, value, true)
            val path = value.string?.toString().orEmpty()
            assertFalse(
                "${item.id}: painterResource würde dies fälschlich als VectorDrawable einstufen ($path)",
                path.endsWith(".xml")
            )

            val scaled = BitmapFactory.decodeResource(resources, item.drawableRes)
            assertNotNull("${item.id}: Bild ($path) konnte nicht dekodiert werden", scaled)
            val original = BitmapFactory.decodeResource(
                resources,
                item.drawableRes,
                BitmapFactory.Options().apply { inScaled = false }
            )
            assertEquals(
                "${item.id}: Das Bild wird dichteskaliert (OOM-Gefahr), es gehört nach drawable-nodpi ($path)",
                original.width,
                scaled.width
            )
            scaled.recycle()
            original.recycle()
        }
    }
}
