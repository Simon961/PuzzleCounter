package com.example.puzzlecounter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Phones are locked to portrait, tablets are not - and the lock is not in the manifest, where it would apply to every
 * screen size.
 */
class OrientationLockTest {

    private val main = File("src/main")

    private fun lockPortrait(folder: String): Boolean {
        val file = File(main, "res/$folder/bools.xml")
        assertTrue("$file fehlt", file.isFile)
        val bools = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("bool")
        val values = (0 until bools.length).map { bools.item(it) }.filter { it.attributes.getNamedItem("name").nodeValue == "lock_portrait" }
        assertEquals("$file: lock_portrait genau einmal", 1, values.size)
        return values.single().textContent.trim().toBooleanStrict()
    }

    @Test
    fun phones_areLocked_andLargeScreensAreNot() {
        assertTrue(lockPortrait("values"))
        assertFalse(lockPortrait("values-sw600dp"))
    }

    @Test
    fun theManifest_doesNotLockAnyOrientation() {
        val manifest = File(main, "AndroidManifest.xml").readText()
        assertFalse("screenOrientation im Manifest sperrt auch Tablets", "screenOrientation" in manifest)
    }

    @Test
    fun theActivity_appliesTheSetting() {
        val activity = File(main, "java/com/example/puzzlecounter/MainActivity.kt").readText()
        assertTrue("R.bool.lock_portrait" in activity)
        assertTrue("SCREEN_ORIENTATION_PORTRAIT" in activity)
        assertTrue("SCREEN_ORIENTATION_UNSPECIFIED" in activity)
    }
}
