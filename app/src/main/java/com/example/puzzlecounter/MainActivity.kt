package com.example.puzzlecounter

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.puzzlecounter.ui.PuzzleApp
import com.example.puzzlecounter.ui.theme.PuzzleCounterTheme

/**
 * The only activity. An [AppCompatActivity] so that the language chosen in the app (see `AppLanguage`) applies at
 * once on every Android version: from Android 13 on the system does it, before that AppCompat.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockPortraitOnPhones()
        // Transparent system bars with light icons: the app is always dark.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            PuzzleCounterTheme {
                PuzzleApp()
            }
        }
    }

    /**
     * Phones keep the app upright, tablets and unfolded foldables are not locked (`R.bool.lock_portrait`, see
     * res/values-sw600dp). It is done here and not with `android:screenOrientation` in the manifest, which would
     * apply to every screen size: Android 16 and newer ignores such a lock on large screens anyway, and Google Play
     * expects apps to work in both orientations there. The activity is created again when the screen class changes
     * (a foldable that is opened), and this runs again then.
     */
    @SuppressLint("SourceLockedOrientationActivity")
    private fun lockPortraitOnPhones() {
        requestedOrientation = if (resources.getBoolean(R.bool.lock_portrait)) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
