package com.example.puzzlecounter.ui

import android.content.Context
import android.util.Log
import androidx.core.content.edit

data class PuzzleHistoryEntry(
    /** Kept in the stored format only; the screen names an entry by its [target] in the language of the app. */
    val name: String,
    val date: Long,
    val target: Int,
    val actual: Int,
    /**
     * What each photo of the check counted, in the order they were taken; they add up to [actual]. Empty for a check
     * that was saved before the app kept them (and so for what a photo-by-photo view cannot show).
     */
    val photos: List<PhotoCount> = emptyList(),
) {
    /** The places in the photos where the number was estimated (0 if the photos are not known). */
    val clusters: Int get() = photos.sumOf { it.clusters }
}

/** Persists the results of the last [MAX_ENTRIES] checked puzzles (newest first). */
class HistoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("puzzle_history", Context.MODE_PRIVATE)

    fun saveHistory(entry: PuzzleHistoryEntry) {
        val history = (listOf(entry) + getHistory()).take(MAX_ENTRIES)
        prefs.edit { putString("history_data", encodeHistory(history)) }
    }

    fun getHistory(): List<PuzzleHistoryEntry> =
        decodeHistory(prefs.getString("history_data", "").orEmpty()) { Log.w(TAG, "Unlesbarer Verlaufseintrag wird übersprungen: \"$it\"") }

    private companion object {
        const val TAG = "HistoryManager"
        const val MAX_ENTRIES = 15
    }
}

/**
 * The entries as text: `name,date,target,actual[,photos]` (the photos as in [encode], left out if they are not
 * known), the entries joined by `;`. The first four fields are what the app stored from the start, so that the
 * history of earlier versions stays readable.
 */
internal fun encodeHistory(entries: List<PuzzleHistoryEntry>): String = entries.joinToString(";") { entry ->
    val fields = "${entry.name},${entry.date},${entry.target},${entry.actual}"
    if (entry.photos.isEmpty()) fields else "$fields,${entry.photos.encode()}"
}

/** The entries of [csv] (see [encodeHistory]); every entry that cannot be read is skipped and passed to [onUnreadable]. */
internal fun decodeHistory(csv: String, onUnreadable: (String) -> Unit = {}): List<PuzzleHistoryEntry> {
    if (csv.isEmpty()) return emptyList()
    return csv.split(";").mapNotNull { text -> parseEntry(text) ?: run { onUnreadable(text); null } }
}

private fun parseEntry(text: String): PuzzleHistoryEntry? {
    val parts = text.split(",")
    if (parts.size != 4 && parts.size != 5) return null
    val actual = parts[3].toIntOrNull() ?: return null
    // photos that do not add up to the result are not trusted: the entry is shown without them
    val photos = parts.getOrNull(4)?.let(::decodePhotoCounts)?.takeIf { photos -> photos.sumOf { it.pieces } == actual }.orEmpty()
    return PuzzleHistoryEntry(
        name = parts[0],
        date = parts[1].toLongOrNull() ?: return null,
        target = parts[2].toIntOrNull() ?: return null,
        actual = actual,
        photos = photos,
    )
}
