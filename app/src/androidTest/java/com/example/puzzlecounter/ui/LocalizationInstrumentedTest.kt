package com.example.puzzlecounter.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.puzzlecounter.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The texts of every language as Android really resolves them on the device: the grammatical forms of numbers
 * (Russian has three) and every placeholder filled without an error. The switch of the language itself needs a running
 * AppCompatActivity (see [AppLanguage]) and is checked with the real app.
 */
@RunWith(AndroidJUnit4::class)
class LocalizationInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun inLanguage(tag: String): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
    )

    private fun Context.pieces(count: Int) = resources.getQuantityString(R.plurals.pieces_count, count, count)

    @Test
    fun numbers_takeTheGrammaticalFormOfTheirLanguage() {
        val ru = inLanguage("ru")
        assertEquals("1 деталь", ru.pieces(1))
        assertEquals("2 детали", ru.pieces(2))
        assertEquals("5 деталей", ru.pieces(5))
        assertEquals("11 деталей", ru.pieces(11))
        assertEquals("21 деталь", ru.pieces(21))
        assertEquals("204 детали", ru.pieces(204))
        val de = inLanguage("de")
        assertEquals("1 Teil", de.pieces(1))
        assertEquals("207 Teile", de.pieces(207))
        val fr = inLanguage("fr")
        assertEquals("French uses the singular for 0 and 1", "0 pièce", fr.pieces(0))
        assertEquals("2 pièces", fr.pieces(2))
        assertEquals("1 pieza", inLanguage("es").pieces(1))
        assertEquals("200 piezas", inLanguage("es").pieces(200))
        assertEquals("1 piece", inLanguage("en").pieces(1))
        assertEquals("2 pieces", inLanguage("en").pieces(2))
    }

    /** Big piece counts (a puzzle goes up to 60000 pieces) are grouped the way the language writes numbers. */
    @Test
    fun bigNumbers_areGroupedLikeTheLanguage() {
        fun scanned(tag: String, count: Int) = inLanguage(tag).resources.getQuantityString(R.plurals.result_scanned, count, count)
        assertEquals("1,008 pieces scanned", scanned("en", 1008))
        assertEquals("1.008 Teile gescannt", scanned("de", 1008))
        assertEquals("60.000 piezas escaneadas", scanned("es", 60_000))
        // French and Russian separate the thousands with a (narrow) no-break space
        assertTrue(scanned("fr", 1008), Regex("1[\\s\u00a0\u202f]008 pièces scannées").matches(scanned("fr", 1008)))
        assertTrue(scanned("ru", 60_000), Regex("Отсканировано 60[\\s\u00a0\u202f]000 деталей").matches(scanned("ru", 60_000)))
        // small numbers stay as they are
        assertEquals("204 Teile gescannt", scanned("de", 204))
        assertEquals("Отсканировано 204 детали", scanned("ru", 204))
        assertEquals("Отсканирована 21 деталь", scanned("ru", 21))
    }

    @Test
    fun aLanguageTheAppDoesNotSpeak_showsEnglish() {
        assertEquals("Settings", inLanguage("it").getString(R.string.settings))
        assertEquals("Einstellungen", inLanguage("de").getString(R.string.settings))
        assertEquals("Настройки", inLanguage("ru").getString(R.string.settings))
    }

    /** Fills every placeholder of every text and plural with a value of its type, in every language. */
    @Test
    fun everyTextOfEveryLanguage_canBeFilledIn() {
        // %1$d, %d, %1$,d (with the grouping of the language), %2$s ...: the number gives the position
        val placeholder = Regex("""%(?:(\d+)\$)?[-#+ 0,(]*\d*([ds])""")
        fun argsFor(raw: String): Array<Any> {
            val found = placeholder.findAll(raw).toList()
            val count = found.maxOfOrNull { m -> m.groupValues[1].toIntOrNull() ?: (found.indexOf(m) + 1) } ?: 0
            val args = Array<Any>(count) { "x" }
            found.forEachIndexed { i, m ->
                val position = (m.groupValues[1].toIntOrNull() ?: (i + 1)) - 1
                args[position] = if (m.groupValues[2] == "d") 12_345 else "x"
            }
            return args
        }
        val strings = R.string::class.java.fields.map { it.getInt(null) to it.name }
        val plurals = R.plurals::class.java.fields.map { it.getInt(null) to it.name }
        for (tag in listOf("en", "de", "es", "ru", "fr")) {
            val res = inLanguage(tag).resources
            for ((id, name) in strings) {
                if (APP_TEXT_PREFIXES.none { name == it || name.startsWith(it + "_") }) continue // texts of the libraries
                val raw = res.getString(id)
                val text = res.getString(id, *argsFor(raw))
                assertTrue("$tag/$name ist leer", text.isNotBlank())
            }
            for ((id, name) in plurals) {
                if (APP_TEXT_PREFIXES.none { name.startsWith(it + "_") }) continue
                for (count in listOf(0, 1, 2, 3, 5, 11, 21, 101, 200, 1000)) {
                    val raw = res.getQuantityString(id, count)
                    val args = argsFor(raw).let { a -> if (a.isNotEmpty()) a.also { it[0] = count } else a }
                    assertTrue("$tag/$name($count)", res.getQuantityString(id, count, *args).isNotBlank())
                }
            }
        }
    }

    private companion object {
        /** The texts of the app itself (the R class also holds those of the libraries). */
        val APP_TEXT_PREFIXES = listOf(
            "app", "action", "settings", "upload_photo", "start", "history", "selection",
            "instruction", "result", "camera", "capture", "torch", "take_photo", "review", "analyzing", "issue", "frame",
            "background", "info", "language", "pieces", "maker",
        )
    }
}
