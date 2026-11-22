package com.example.puzzlecounter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the history is stored. The history of earlier versions (`name,date,target,actual`) must stay readable, the
 * numbers of the photos come on top of it, and nothing that cannot be read may take the other entries with it.
 */
class HistoryFormatTest {

    private fun entry(target: Int, vararg photos: PhotoCount, date: Long = 1_763_700_000_000) =
        PuzzleHistoryEntry("Puzzle $target", date, target, photos.sumOf { it.pieces }, photos.toList())

    // --- the history of earlier versions ------------------------------------------------------------------------------

    @Test
    fun entriesOfEarlierVersions_areStillRead_withoutPhotos() {
        val entries = decodeHistory("Puzzle 200,1763700000000,200,204;Puzzle 1000,1763600000000,1000,996")
        assertEquals(
            listOf(
                PuzzleHistoryEntry("Puzzle 200", 1_763_700_000_000, 200, 204),
                PuzzleHistoryEntry("Puzzle 1000", 1_763_600_000_000, 1000, 996),
            ),
            entries
        )
        assertTrue(entries.all { it.photos.isEmpty() && it.clusters == 0 })
    }

    @Test
    fun anEntryWithoutPhotos_isWrittenInTheOldFormat() {
        // so that the numbers of the first four fields are where every version reads them
        assertEquals("Puzzle 200,1763700000000,200,204", encodeHistory(listOf(PuzzleHistoryEntry("Puzzle 200", 1_763_700_000_000, 200, 204))))
    }

    // --- the numbers of the photos ------------------------------------------------------------------------------------

    @Test
    fun theNumbersOfThePhotos_surviveWriting() {
        val entries = listOf(
            entry(600, PhotoCount(201, 1), PhotoCount(198, 0, warned = true), PhotoCount(204, 3), date = 1_763_700_000_000),
            entry(200, PhotoCount(203), date = 1_763_600_000_000),
            PuzzleHistoryEntry("Puzzle 500", 1_763_500_000_000, 500, 480),
        )
        val text = encodeHistory(entries)
        assertEquals("Puzzle 600,1763700000000,600,603,201:1|198:0:w|204:3;Puzzle 200,1763600000000,200,203,203:0;Puzzle 500,1763500000000,500,480", text)
        assertEquals(entries, decodeHistory(text))
    }

    @Test
    fun theEstimatedPlaces_areTheSumOfThePhotos() {
        assertEquals(4, entry(600, PhotoCount(201, 1), PhotoCount(198, 0), PhotoCount(204, 3)).clusters)
        assertEquals(0, PuzzleHistoryEntry("Puzzle 600", 1, 600, 603).clusters)
    }

    @Test
    fun thePhotosOfTheBiggestPuzzle_fit() {
        // 60000 pieces: 300 photos of about 200 pieces
        val photos = List(300) { PhotoCount(200, it % 3) }
        val entries = listOf(entry(60_000, *photos.toTypedArray()))
        assertEquals(entries, decodeHistory(encodeHistory(entries)))
    }

    @Test
    fun photosThatDoNotAddUpToTheResult_areLeftOut_butTheEntryStays() {
        val entries = decodeHistory("Puzzle 600,1763700000000,600,603,201:1|198:0")
        assertEquals(listOf(PuzzleHistoryEntry("Puzzle 600", 1_763_700_000_000, 600, 603)), entries)
    }

    @Test
    fun malformedPhotos_areLeftOut_butTheEntryStays() {
        for (photos in listOf("x", "201", "201:a", "201:1:x", "201:1:w:w", "-5:0", "201:1|", "|")) {
            assertEquals(
                "photos \"$photos\"",
                listOf(PuzzleHistoryEntry("Puzzle 201", 1_763_700_000_000, 201, 201)),
                decodeHistory("Puzzle 201,1763700000000,201,201,$photos")
            )
        }
    }

    // --- what cannot be read ------------------------------------------------------------------------------------------

    @Test
    fun anEntryThatCannotBeRead_isSkippedAndReported_theOthersStay() {
        val skipped = mutableListOf<String>()
        val entries = decodeHistory(
            "Puzzle 200,1763700000000,200,204;kaputt;Puzzle 500,x,500,500;Puzzle 1000,1763600000000,1000,y;Puzzle 300,1763500000000,300,300,300:0,extra;Puzzle 750,1763400000000,750,750",
            skipped::add
        )
        assertEquals(listOf(200, 750), entries.map { it.target })
        assertEquals(listOf("kaputt", "Puzzle 500,x,500,500", "Puzzle 1000,1763600000000,1000,y", "Puzzle 300,1763500000000,300,300,300:0,extra"), skipped)
        assertEquals(emptyList<PuzzleHistoryEntry>(), decodeHistory(""))
    }

    // --- the numbers of one photo on their own (they are also kept for the check in progress) --------------------------------

    @Test
    fun photoCounts_roundTrip_andMalformedOnesAreRecognised() {
        val photos = listOf(PhotoCount(201, 1), PhotoCount(198, 0, warned = true))
        assertEquals("201:1|198:0:w", photos.encode())
        assertEquals(photos, decodePhotoCounts(photos.encode()))
        assertEquals(emptyList<PhotoCount>(), decodePhotoCounts(null))
        assertEquals(emptyList<PhotoCount>(), decodePhotoCounts(""))
        assertEquals("", emptyList<PhotoCount>().encode())
        assertNull(decodePhotoCounts("201:1|oops"))
    }
}
