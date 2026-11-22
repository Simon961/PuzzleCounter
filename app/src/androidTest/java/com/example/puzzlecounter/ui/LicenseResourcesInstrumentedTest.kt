package com.example.puzzlecounter.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.puzzlecounter.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/**
 * What the installed app really contains for the Info screen: the license texts as the official files (a build that
 * shrinks resources must not change them), the screen-size rule of the orientation lock, and the texts of the
 * license section in every language.
 */
@RunWith(AndroidJUnit4::class)
class LicenseResourcesInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun sha256(resource: Int): String =
        MessageDigest.getInstance("SHA-256")
            .digest(context.resources.openRawResource(resource).use { it.readBytes() })
            .joinToString("") { "%02x".format(it) }

    @Test
    fun theLicenseTexts_arePackagedUnchanged() {
        assertEquals("cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30", sha256(R.raw.license_apache_2_0))
        assertEquals("0d96a4ff68ad6d4b6f1f30f713b18d5184912ba8dd389f86aa7710db079abcb0", sha256(R.raw.license_agpl_3_0))
        assertEquals("2b2cc1180c7e6988328ad2033b04b80117419db9c4c584918bbb3cfec7e9364f", sha256(R.raw.license_libyuv_bsd_3_clause))
    }

    @Test
    fun theLicenseTexts_cutIntoParagraphsOnTheDevice() {
        val agpl = LicenseText.paragraphs(context.resources.openRawResource(R.raw.license_agpl_3_0).bufferedReader().use { it.readText() })
        assertEquals("GNU AFFERO GENERAL PUBLIC LICENSE\nVersion 3, 19 November 2007", agpl.first().text)
        assertTrue("sections 0 to 17 are headings", agpl.count { it.kind == LicenseText.Kind.HEADING } == 18)
    }

    /** Phones (below 600 dp) are locked to portrait, larger screens are not. */
    @Test
    fun theOrientationLock_followsTheScreenSize() {
        val smallestWidthDp = context.resources.configuration.smallestScreenWidthDp
        assertEquals("smallest width $smallestWidthDp dp", smallestWidthDp < 600, context.resources.getBoolean(R.bool.lock_portrait))
    }
}
