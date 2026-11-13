package com.example.puzzlecounter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultTextsTest {

    @Test
    fun completePuzzle() {
        val texts = resultTexts(target = 200, counted = 200, estimatedGroups = 0)
        assertEquals(Outcome.COMPLETE, texts.outcome)
        assertEquals("Alle Teile sind vorhanden.", texts.message)
        assertNull(texts.hint)
        assertNull(texts.estimate)
    }

    @Test
    fun missingPieces_useTheRightGrammar() {
        assertEquals("Es fehlt 1 Teil.", resultTexts(200, 199, 0).message)
        assertEquals("Es fehlen 2 Teile.", resultTexts(200, 198, 0).message)
        assertEquals(Outcome.MISSING, resultTexts(200, 150, 0).outcome)
        assertNotNull(resultTexts(200, 199, 0).hint)
    }

    @Test
    fun tooManyPieces_useTheRightGrammar() {
        assertEquals("Es wurde 1 Teil mehr gezählt als angegeben.", resultTexts(200, 201, 0).message)
        assertEquals("Es wurden 6 Teile mehr gezählt als angegeben.", resultTexts(200, 206, 0).message)
        assertEquals(Outcome.TOO_MANY, resultTexts(200, 206, 0).outcome)
        assertTrue(resultTexts(200, 206, 0).hint!!.contains("mehr Teile als angegeben"))
    }

    @Test
    fun estimatedGroups_areMentionedWheneverThereAreSome() {
        assertTrue(resultTexts(200, 200, 1).estimate!!.startsWith("In 1 Gruppe lagen Teile"))
        assertTrue(resultTexts(200, 190, 4).estimate!!.startsWith("In 4 Gruppen lagen Teile"))
        assertNull(resultTexts(200, 190, 0).estimate)
    }
}
