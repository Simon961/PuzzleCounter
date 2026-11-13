package com.example.puzzlecounter.ui

import android.content.Context
import android.util.Log
import androidx.core.content.edit

data class PuzzleHistoryEntry(
    val name: String,
    val date: Long,
    val target: Int,
    val actual: Int,
)

/** Persists the results of the last [MAX_ENTRIES] checked puzzles (newest first). */
class HistoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("puzzle_history", Context.MODE_PRIVATE)

    fun saveHistory(entry: PuzzleHistoryEntry) {
        val history = (listOf(entry) + getHistory()).take(MAX_ENTRIES)
        val csv = history.joinToString(";") { "${it.name},${it.date},${it.target},${it.actual}" }
        prefs.edit { putString("history_data", csv) }
    }

    fun getHistory(): List<PuzzleHistoryEntry> {
        val csv = prefs.getString("history_data", "").orEmpty()
        if (csv.isEmpty()) return emptyList()

        return csv.split(";").mapNotNull { entry ->
            parseEntry(entry) ?: run {
                Log.w(TAG, "Unlesbarer Verlaufseintrag wird übersprungen: \"$entry\"")
                null
            }
        }
    }

    /** One entry is `name,date,target,actual`; null if it is malformed. */
    private fun parseEntry(entry: String): PuzzleHistoryEntry? {
        val parts = entry.split(",")
        if (parts.size != 4) return null
        return PuzzleHistoryEntry(
            name = parts[0],
            date = parts[1].toLongOrNull() ?: return null,
            target = parts[2].toIntOrNull() ?: return null,
            actual = parts[3].toIntOrNull() ?: return null,
        )
    }

    private companion object {
        const val TAG = "HistoryManager"
        const val MAX_ENTRIES = 15
    }
}
