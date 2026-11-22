package com.example.puzzlecounter.ui

import android.content.Context
import androidx.core.content.edit

/**
 * Persists the appearance settings (background image and brightness).
 *
 * The background is saved under the stable name of its [AppBackgroundItem], never as `R.drawable` number: resource
 * numbers are handed out alphabetically across the app and all its libraries, so they move whenever one of them
 * adds or drops a resource - after an update a saved number could point at a different resource or at none, and
 * the app would crash at its first screen.
 */
class SettingsManager(context: Context, prefsName: String = "puzzle_settings") {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /** The drawable of the chosen background; the default one if nothing valid is saved. */
    fun getBackgroundId(): Int {
        val saved = availableBackgrounds.firstOrNull { it.id == prefs.getString(KEY_BACKGROUND, null) }
        return (saved ?: DEFAULT_BACKGROUND).drawableRes
    }

    /** Remembers [drawableRes], which must be the drawable of one of the [availableBackgrounds]. */
    fun setBackgroundId(drawableRes: Int) {
        val item = availableBackgrounds.firstOrNull { it.drawableRes == drawableRes } ?: return
        prefs.edit { putString(KEY_BACKGROUND, item.id) }
    }

    fun getBackgroundBrightness(): Float = prefs.getFloat("bg_brightness", DEFAULT_BRIGHTNESS)

    fun setBackgroundBrightness(brightness: Float) = prefs.edit { putFloat("bg_brightness", brightness) }

    private companion object {
        const val DEFAULT_BRIGHTNESS = 0.65f
        const val KEY_BACKGROUND = "bg_name"
        val DEFAULT_BACKGROUND get() = availableBackgrounds.first()
    }
}
