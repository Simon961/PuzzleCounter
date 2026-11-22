package com.example.puzzlecounter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The language files must match: every text in every language, with the same placeholders (a missing or different
 * %1$d crashes the screen or shows the wrong number), and every plural with the forms its language needs.
 * Also guards the code against new texts that are written into it instead of into the resources.
 */
class StringResourcesTest {

    private class Texts(val strings: Map<String, String>, val untranslatable: Set<String>, val plurals: Map<String, Map<String, String>>)

    private val res = File("src/main/res")
    private val languages = listOf("de", "es", "ru", "fr")

    /** The plural forms (CLDR) each language needs for whole numbers; English is the default file. */
    private val pluralForms = mapOf(
        "" to setOf("one", "other"),
        "de" to setOf("one", "other"),
        "es" to setOf("one", "many", "other"),
        "fr" to setOf("one", "many", "other"),
        "ru" to setOf("one", "few", "many", "other"),
    )

    private fun load(folder: String): Texts {
        val file = File(res, "$folder/strings.xml")
        assertTrue("$file fehlt", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = LinkedHashMap<String, String>()
        val untranslatable = HashSet<String>()
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            strings[e.getAttribute("name")] = e.textContent
            if (e.getAttribute("translatable") == "false") untranslatable += e.getAttribute("name")
        }
        val plurals = LinkedHashMap<String, Map<String, String>>()
        val pluralNodes = doc.getElementsByTagName("plurals")
        for (i in 0 until pluralNodes.length) {
            val e = pluralNodes.item(i) as Element
            val items = e.getElementsByTagName("item")
            plurals[e.getAttribute("name")] = (0 until items.length).associate { j ->
                val item = items.item(j) as Element
                item.getAttribute("quantity") to item.textContent
            }
        }
        return Texts(strings, untranslatable, plurals)
    }

    private fun placeholders(text: String): List<String> =
        Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z%]""").findAll(text).map { it.value }.sorted().toList()

    private val default by lazy { load("values") }

    @Test
    fun everyLanguage_hasEveryTextAndNoOtherOne() {
        val keys = default.strings.keys - default.untranslatable
        for (language in languages) {
            val texts = load("values-$language")
            assertEquals("values-$language: Texte", keys, texts.strings.keys)
            assertEquals("values-$language: Pluralformen", default.plurals.keys, texts.plurals.keys)
            assertTrue("values-$language: nur in values/ erlaubt: ${texts.untranslatable}", texts.untranslatable.isEmpty())
        }
    }

    @Test
    fun everyText_keepsThePlaceholdersOfTheEnglishOne() {
        for (language in languages) {
            val texts = load("values-$language")
            for ((key, english) in default.strings) {
                val text = texts.strings[key] ?: continue
                assertEquals("values-$language/$key", placeholders(english), placeholders(text))
                assertTrue("values-$language/$key ist leer", text.isNotBlank())
            }
        }
    }

    @Test
    fun everyPlural_hasTheFormsOfItsLanguageWithTheSamePlaceholders() {
        for (folder in listOf("") + languages) {
            val texts = if (folder.isEmpty()) default else load("values-$folder")
            val forms = pluralForms.getValue(folder)
            for ((key, items) in texts.plurals) {
                assertEquals("values${if (folder.isEmpty()) "" else "-$folder"}/$key: Formen", forms, items.keys)
                val expected = placeholders(default.plurals.getValue(key).getValue("other"))
                for ((quantity, text) in items) {
                    assertEquals("$folder/$key[$quantity]", expected, placeholders(text))
                }
            }
        }
    }

    @Test
    fun theDefaultLanguage_isEnglish() {
        for ((key, text) in default.strings) {
            if (key in default.untranslatable) continue
            assertTrue("values/$key sieht nicht englisch aus: $text", text.none { it in "äöüÄÖÜß" })
        }
    }

    @Test
    fun theLanguagesOfTheApp_areTheLanguageFolders() {
        val folders = res.listFiles()!!.map { it.name }.filter { it.matches(Regex("values-[a-z]{2}")) }.map { it.removePrefix("values-") }.toSet()
        assertEquals(languages.toSet(), folders)
        val offered = AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.map { it.tag }.toSet()
        assertEquals("die Sprachauswahl bietet genau die Sprachen der App an", folders + "en", offered)
        // each language is named in itself: the same in every language file, so it is not translated
        for (language in AppLanguage.entries.filter { it != AppLanguage.SYSTEM }) {
            val name = default.strings.entries.single { it.key == "language_" + language.name.lowercase() }
            assertTrue("${name.key} muss translatable=\"false\" sein", name.key in default.untranslatable)
        }
    }

    @Test
    fun theCode_writesNoTextIntoTheScreens() {
        val visible = Regex("""(\bText\(|contentDescription\s*=\s*|\btext\s*=\s*|\blabel\s*=\s*)"[^"]*\p{L}""")
        val offenders = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line -> if (visible.containsMatchIn(line)) "${file.name}:${i + 1}: ${line.trim()}" else null }
        }.toList()
        assertTrue("Text gehört in res/values*/strings.xml:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
