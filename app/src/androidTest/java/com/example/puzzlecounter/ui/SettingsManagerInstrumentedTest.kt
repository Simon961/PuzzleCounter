package com.example.puzzlecounter.ui

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The background setting must survive an app update. Resource numbers (`R.drawable.x`) change whenever the app or one
 * of its libraries gains or loses a resource, so a saved number may name another resource or none after an update -
 * which crashed the first screen. The setting is saved under the name of the background instead.
 *
 * Works on a settings file of its own, so the settings of the person using the phone are not touched.
 */
@RunWith(AndroidJUnit4::class)
class SettingsManagerInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefsName = "settings_manager_test"
    private fun manager() = SettingsManager(context, prefsName)
    private val default = availableBackgrounds.first().drawableRes

    // commit(): the file is empty before the first test starts, no earlier write may still be on its way
    @SuppressLint("ApplySharedPref")
    @Before
    fun clean() {
        context.getSharedPreferences(prefsName, 0).edit().clear().commit()
    }

    /** Nothing of the test stays on the phone, not even an empty file. */
    @After
    fun removeTheFile() {
        context.deleteSharedPreferences(prefsName)
    }

    @Test
    fun withoutASavedChoice_theDefaultBackgroundIsUsed() {
        assertEquals(default, manager().getBackgroundId())
    }

    @Test
    fun everyBackground_isRememberedByItsName() {
        for (item in availableBackgrounds) {
            manager().setBackgroundId(item.drawableRes)
            assertEquals(item.drawableRes, manager().getBackgroundId())
            assertEquals(item.id, context.getSharedPreferences(prefsName, 0).getString("bg_name", null))
        }
    }

    @Test
    fun anUnknownDrawable_isNotSaved() {
        manager().setBackgroundId(availableBackgrounds[1].drawableRes)
        manager().setBackgroundId(-1)
        assertEquals(availableBackgrounds[1].drawableRes, manager().getBackgroundId())
    }

    // commit(): the name is in the file when the manager reads it in the next line
    @SuppressLint("ApplySharedPref")
    @Test
    fun aSavedNameThatNoBackgroundHasAnyMore_fallsBackToTheDefault() {
        context.getSharedPreferences(prefsName, 0).edit().putString("bg_name", "bg_removed_in_a_later_version").commit()
        assertEquals(default, manager().getBackgroundId())
    }

    @Test
    fun brightness_isRemembered() {
        assertEquals(0.65f, manager().getBackgroundBrightness(), 0f)
        manager().setBackgroundBrightness(0.3f)
        assertEquals(0.3f, manager().getBackgroundBrightness(), 0f)
    }
}
