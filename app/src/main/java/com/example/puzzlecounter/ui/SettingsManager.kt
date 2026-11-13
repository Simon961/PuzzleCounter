package com.example.puzzlecounter.ui

import android.content.Context
import androidx.core.content.edit
import com.example.puzzlecounter.R

/** Persists the appearance settings (background image and brightness). */
class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("puzzle_settings", Context.MODE_PRIVATE)

    fun getBackgroundId(): Int = prefs.getInt("bg_id", R.drawable.app_background)

    fun setBackgroundId(id: Int) = prefs.edit { putInt("bg_id", id) }

    fun getBackgroundBrightness(): Float = prefs.getFloat("bg_brightness", DEFAULT_BRIGHTNESS)

    fun setBackgroundBrightness(brightness: Float) = prefs.edit { putFloat("bg_brightness", brightness) }

    private companion object {
        const val DEFAULT_BRIGHTNESS = 0.65f
    }
}
