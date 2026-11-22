package com.example.puzzlecounter.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app opens at its start screen: there is no screen of its own that makes the user wait (it used to show the
 * name for two seconds). Android 12 and newer show their own splash screen while the app starts, see
 * res/values-v31/themes.xml.
 */
class StartDestinationTest {

    private val ui = File("src/main/java/com/example/puzzlecounter/ui")
    private val res = File("src/main/res")

    @Test
    fun theNavigation_startsAtTheStartScreen() {
        val app = File(ui, "PuzzleApp.kt").readText()
        assertTrue("startDestination = PuzzleScreen.Start.name" in app)
        assertFalse("PuzzleScreen.Splash" in app)
        assertTrue("Splash" !in PuzzleScreen.entries.map { it.name })
    }

    @Test
    fun noScreenWaitsBeforeTheStartScreen() {
        val sources = ui.walkTopDown().filter { it.extension == "kt" }.toList()
        assertFalse("SplashScreen" in sources.joinToString("\n") { it.readText() })
        assertFalse("PuzzleScreens.kt hat ein delay(", "delay(" in File(ui, "PuzzleScreens.kt").readText())
    }

    @Test
    fun theTextsOfTheOldSplashScreen_areGone() {
        val files = res.listFiles()!!.filter { it.name.startsWith("values") }.map { File(it, "strings.xml") }.filter { it.isFile }
        assertTrue(files.size >= 5)
        for (file in files) assertFalse("$file: app_tagline gehörte nur zum Splash-Screen", "app_tagline" in file.readText())
    }

    @Test
    fun theSystemSplashScreen_isDark() {
        val theme = File(res, "values-v31/themes.xml").readText()
        assertTrue("android:windowSplashScreenBackground" in theme)
        assertTrue("@color/window_background" in theme)
    }
}
