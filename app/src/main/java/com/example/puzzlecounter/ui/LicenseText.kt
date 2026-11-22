package com.example.puzzlecounter.ui

/**
 * A license text as paragraphs. The official texts (res/raw) are plain files that are wrapped at 80 columns, partly with
 * the headings centred by spaces; wrapped like that they would break at every other line on a phone. A paragraph is what
 * stands between two blank lines: its lines are joined to one, so the words stay exactly the same and only the line
 * breaks are the screen's. Lines that are centred in the file (the titles) are kept as they are.
 */
internal object LicenseText {

    enum class Kind { TITLE, HEADING, BODY }

    data class Paragraph(val text: String, val kind: Kind)

    fun paragraphs(raw: String): List<Paragraph> {
        val result = ArrayList<Paragraph>()
        val lines = ArrayList<String>()
        fun endParagraph() {
            if (lines.isNotEmpty()) result += paragraphOf(lines)
            lines.clear()
        }
        for (line in raw.lines()) {
            if (line.isBlank()) endParagraph() else lines += line
        }
        endParagraph()
        return result
    }

    private fun paragraphOf(lines: List<String>): Paragraph {
        if (lines.all { it.length - it.trimStart().length >= CENTRED_INDENT }) {
            return Paragraph(lines.joinToString("\n") { it.trim() }, Kind.TITLE)
        }
        val text = lines.joinToString(" ") { it.trim() }
        val isHeading = lines.size == 1 && (NUMBERED_HEADING.matches(text) || text.startsWith("APPENDIX:") || isAllCaps(text))
        return Paragraph(text, if (isHeading) Kind.HEADING else Kind.BODY)
    }

    private fun isAllCaps(text: String) = text.any { it.isLetter() } && text.none { it.isLowerCase() }

    /** Lines that start with at least this many spaces are centred titles. */
    private const val CENTRED_INDENT = 12

    /** "0. Definitions." - a section heading of the GNU and Apache licenses (a single line of its own). */
    private val NUMBERED_HEADING = Regex("""\d+\.\s+\S.*""")
}
