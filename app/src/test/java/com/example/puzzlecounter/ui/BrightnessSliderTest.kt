package com.example.puzzlecounter.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The brightness slider uses the Material3 slider that is controlled from outside: it only reports a drag
 * (`onValueChange`) and shows what its state holds. Without the state following the value of the app the thumb stands
 * still while the percentage changes (found on the phone, 2026-11-22). The behaviour itself needs a device (a drag on
 * the real screen); this guards the line that makes it work.
 */
class BrightnessSliderTest {

    private val source = File("src/main/java/com/example/puzzlecounter/ui/SettingsScreen.kt").readText()

    @Test
    fun theSliderState_followsTheBrightnessOfTheApp() {
        assertTrue("the state of the slider is made from the brightness of the app", "rememberSliderState(value = uiState.backgroundBrightness" in source)
        assertTrue("and is kept in step with it", "brightnessState.value = uiState.backgroundBrightness" in source)
        assertTrue("the drag is passed on to the app", "onValueChange = { viewModel.updateBackgroundBrightness(it) }" in source)
    }
}
