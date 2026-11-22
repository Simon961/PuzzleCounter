package com.example.puzzlecounter.ui

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.puzzlecounter.R

/**
 * The languages the app can be shown in. Each is named in its own language, so that everybody finds theirs, whatever
 * language the app is in at the moment; only [SYSTEM] is named in the current language.
 *
 * [current] and [choose] go through AppCompatDelegate, which from Android 13 on only reaches the system while an
 * AppCompatActivity is alive (it takes the system service from one) - without one both do nothing. In the app they
 * are only called from the screens of MainActivity.
 */
internal enum class AppLanguage(val tag: String, @param:StringRes val nameRes: Int) {
    /** The language of the phone; English if the app does not speak it. */
    SYSTEM("", R.string.language_system_default),
    ENGLISH("en", R.string.language_english),
    GERMAN("de", R.string.language_german),
    SPANISH("es", R.string.language_spanish),
    RUSSIAN("ru", R.string.language_russian),
    FRENCH("fr", R.string.language_french);

    companion object {
        /**
         * The language chosen for the app. Android keeps the choice itself (from Android 13 on, also in its own
         * settings); before that AppCompat stores it (`autoStoreLocales` in the manifest).
         */
        fun current(): AppLanguage {
            val chosen = AppCompatDelegate.getApplicationLocales()
            if (chosen.isEmpty) return SYSTEM
            val language = chosen[0]?.language ?: return SYSTEM
            return entries.firstOrNull { it != SYSTEM && it.tag == language } ?: SYSTEM
        }

        /** Shows the app in [language] at once: the screens are built again, the state of the app stays. */
        fun choose(language: AppLanguage) {
            val locales = if (language == SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language.tag)
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
