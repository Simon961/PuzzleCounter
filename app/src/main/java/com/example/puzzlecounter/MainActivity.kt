package com.example.puzzlecounter

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
}
