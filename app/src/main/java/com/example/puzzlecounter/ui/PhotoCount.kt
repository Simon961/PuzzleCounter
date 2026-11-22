package com.example.puzzlecounter.ui

/** What one confirmed photo added to a check: the numbers the check keeps of it (the photo itself is deleted). */
data class PhotoCount(
    val pieces: Int,
    /** Blobs of the photo that hold several touching pieces, whose number is estimated. */
    val clusters: Int = 0,
    /** The photo was kept although the analysis advised against it (too dark, blurry, pieces on top of each other ...). */
    val warned: Boolean = false,
)

/** The photos as text, `pieces:clusters` (with `:w` after a warned photo) for each, joined by `|`. */
internal fun List<PhotoCount>.encode(): String =
    joinToString("|") { "${it.pieces}:${it.clusters}" + if (it.warned) ":w" else "" }

/** The photos of [text] (see [encode]); an empty list for no text, null if the text is malformed. */
internal fun decodePhotoCounts(text: String?): List<PhotoCount>? {
    if (text.isNullOrEmpty()) return emptyList()
    return text.split("|").map { token ->
        val parts = token.split(":")
        if (parts.size !in 2..3 || (parts.size == 3 && parts[2] != "w")) return null
        val pieces = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val clusters = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        PhotoCount(pieces, clusters, warned = parts.size == 3)
    }
}
