package com.example.puzzlecounter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URI

/**
 * The privacy policy of the app is a web page that the Info screen links to (Google Play wants the link in the app as
 * well as in the store entry).
 */
class PrivacyPolicyLinkTest {

    private val infoScreen = File("src/main/java/com/example/puzzlecounter/ui/InfoScreen.kt").readText()

    @Test
    fun thePolicy_isAnHttpsAddress() {
        val uri = URI(PRIVACY_POLICY_URL)
        assertEquals("https", uri.scheme)
        assertEquals("sites.google.com", uri.host)
        assertEquals("/view/piece-scanner/startseite", uri.path)
    }

    @Test
    fun theInfoScreen_showsItWithItsSentence() {
        assertTrue("LinkRow(PRIVACY_POLICY_URL)" in infoScreen)
        assertTrue("R.string.info_privacy_policy_text" in infoScreen)
    }
}
