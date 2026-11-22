package com.example.puzzlecounter.ui

import com.example.puzzlecounter.ui.LicenseText.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The license texts of the Info screen must be the official ones, and showing them must not change a word.
 */
class LicenseTextTest {

    private val raw = File("src/main/res/raw")

    private fun text(name: String) = File(raw, name).readText(Charsets.UTF_8)

    private fun sha256(name: String) =
        MessageDigest.getInstance("SHA-256").digest(File(raw, name).readBytes()).joinToString("") { "%02x".format(it) }

    private fun words(text: String) = text.split(Regex("""\s+""")).filter { it.isNotEmpty() }

    /** The files are byte for byte the texts as published; changing one on purpose means updating its hash here. */
    @Test
    fun theLicenseFiles_areTheOfficialTexts() {
        assertEquals(
            "https://www.apache.org/licenses/LICENSE-2.0.txt",
            "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30", sha256("license_apache_2_0.txt")
        )
        assertEquals(
            "https://www.gnu.org/licenses/agpl-3.0.txt",
            "0d96a4ff68ad6d4b6f1f30f713b18d5184912ba8dd389f86aa7710db079abcb0", sha256("license_agpl_3_0.txt")
        )
        assertEquals(
            "https://chromium.googlesource.com/libyuv/libyuv/+/refs/heads/main/LICENSE",
            "2b2cc1180c7e6988328ad2033b04b80117419db9c4c584918bbb3cfec7e9364f", sha256("license_libyuv_bsd_3_clause.txt")
        )
    }

    @Test
    fun theParagraphs_keepEveryWordInItsPlace() {
        for (name in listOf("license_apache_2_0.txt", "license_agpl_3_0.txt", "license_libyuv_bsd_3_clause.txt")) {
            val original = text(name)
            val shown = LicenseText.paragraphs(original).joinToString(" ") { it.text }
            assertEquals(name, words(original), words(shown))
        }
    }

    @Test
    fun theParagraphs_areJoinedLinesWithoutLeftoverIndentation() {
        for (name in listOf("license_apache_2_0.txt", "license_agpl_3_0.txt", "license_libyuv_bsd_3_clause.txt")) {
            for (paragraph in LicenseText.paragraphs(text(name))) {
                assertEquals("$name: $paragraph", paragraph.text.trim(), paragraph.text.lines().joinToString("\n") { it.trim() })
                assertTrue("$name: $paragraph", paragraph.text.isNotBlank())
                if (paragraph.kind != Kind.TITLE) assertFalse("$name: only a title has several lines: $paragraph", '\n' in paragraph.text)
            }
        }
    }

    @Test
    fun theTitlesAndHeadings_areRecognised() {
        val agpl = LicenseText.paragraphs(text("license_agpl_3_0.txt"))
        assertEquals(
            LicenseText.Paragraph("GNU AFFERO GENERAL PUBLIC LICENSE\nVersion 3, 19 November 2007", Kind.TITLE), agpl.first()
        )
        assertEquals(
            listOf("Preamble", "TERMS AND CONDITIONS", "END OF TERMS AND CONDITIONS", "How to Apply These Terms to Your New Programs"),
            agpl.drop(1).filter { it.kind == Kind.TITLE }.map { it.text }
        )
        // sections 0 to 17, each a heading of its own
        val sections = agpl.filter { it.kind == Kind.HEADING }.map { it.text }
        assertEquals((0..17).toList(), sections.map { it.substringBefore('.').toInt() })
        assertEquals("0. Definitions.", sections.first())
        // a sentence that is a paragraph of its own is not a heading
        assertEquals(Kind.BODY, agpl.single { it.text.startsWith("\"This License\" refers to") }.kind)

        val apache = LicenseText.paragraphs(text("license_apache_2_0.txt"))
        assertEquals(33, apache.size)
        assertEquals(
            LicenseText.Paragraph("Apache License\nVersion 2.0, January 2004\nhttp://www.apache.org/licenses/", Kind.TITLE), apache.first()
        )
        assertEquals(
            listOf(
                "TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION", "1. Definitions.", "END OF TERMS AND CONDITIONS",
                "APPENDIX: How to apply the Apache License to your work."
            ),
            apache.filter { it.kind == Kind.HEADING }.map { it.text }
        )
        assertEquals(Kind.BODY, apache.single { it.text == "Copyright [yyyy] [name of copyright owner]" }.kind)
        // the lines of a wrapped paragraph are one line now
        assertTrue(apache.any { it.text.startsWith("\"License\" shall mean the terms and conditions for use, reproduction, and distribution as defined by Sections 1 through 9 of this document.") })
    }
}
