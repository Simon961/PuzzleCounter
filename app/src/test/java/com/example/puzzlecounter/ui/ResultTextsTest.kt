package com.example.puzzlecounter.ui

import com.example.puzzlecounter.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the result screen says, independent of the language: which texts are shown with which numbers. The words
 * themselves are in the res/values and res/values-xx folders (checked by [StringResourcesTest]).
 */
class ResultTextsTest {

    @Test
    fun completePuzzle() {
        val texts = resultTexts(target = 200, counted = 200, estimatedGroups = 0)
        assertEquals(Outcome.COMPLETE, texts.outcome)
        assertEquals(UiText.Res(R.string.result_title_complete), texts.title)
        assertEquals(UiText.Res(R.string.result_message_complete), texts.message)
        assertNull(texts.hint)
        assertNull(texts.estimate)
        assertNull("nothing to qualify when the count matches", texts.accuracy)
    }

    @Test
    fun missingPieces_areCountedInThePlural() {
        val texts = resultTexts(200, 190, 0)
        assertEquals(Outcome.MISSING, texts.outcome)
        assertEquals(UiText.Res(R.string.result_title_missing), texts.title)
        // the number picks the grammatical form (one / few / many / other) and is shown as well
        assertEquals(UiText.Plural(R.plurals.result_message_missing, 10), texts.message)
        assertEquals(UiText.Plural(R.plurals.result_message_missing, 1), resultTexts(200, 199, 0).message)
        assertEquals(UiText.Res(R.string.result_hint_missing), texts.hint)
    }

    @Test
    fun tooManyPieces_areCountedInThePlural() {
        val texts = resultTexts(200, 206, 0)
        assertEquals(Outcome.TOO_MANY, texts.outcome)
        assertEquals(UiText.Res(R.string.result_title_too_many), texts.title)
        assertEquals(UiText.Plural(R.plurals.result_message_too_many, 6), texts.message)
        assertEquals(UiText.Res(R.string.result_hint_too_many), texts.hint)
    }

    @Test
    fun estimatedGroups_areMentionedWheneverThereAreSome() {
        assertEquals(UiText.Plural(R.plurals.result_estimate, 1), resultTexts(200, 200, 1).estimate)
        assertEquals(UiText.Plural(R.plurals.result_estimate, 4), resultTexts(200, 190, 4).estimate)
        assertNull(resultTexts(200, 190, 0).estimate)
    }

    @Test
    fun smallDifferences_areMarkedAsWithinTheCountingAccuracy() {
        // one photo is counted to about +-3 pieces
        assertEquals(3, countingTolerance(1))
        val advice = UiText.Res(R.string.result_accuracy_advice)
        assertEquals(UiText.Joined(listOf(UiText.Plural(R.plurals.result_accuracy_missing, 3), advice)), resultTexts(200, 199, 0).accuracy)
        assertEquals(UiText.Joined(listOf(UiText.Plural(R.plurals.result_accuracy_too_many, 3), advice)), resultTexts(200, 203, 0).accuracy)
        // beyond the accuracy the difference is a real one
        assertNull(resultTexts(200, 196, 0).accuracy)
        assertNull(resultTexts(200, 204, 0).accuracy)
    }

    @Test
    fun theTolerance_growsWithTheNumberOfPhotosButSlowerThanTheirNumber() {
        assertEquals(4, countingTolerance(2))
        assertEquals(6, countingTolerance(5))
        assertEquals(8, countingTolerance(10))
        assertEquals(3, countingTolerance(0)) // never less than one photo
        // five photos of a 1000 piece puzzle: 4 missing pieces are within the accuracy, 7 are not
        val within = resultTexts(1000, 996, 0, photos = 5).accuracy as UiText.Joined
        assertEquals(UiText.Plural(R.plurals.result_accuracy_missing, 6), within.parts.first())
        assertNull(resultTexts(1000, 993, 0, photos = 5).accuracy)
    }

    @Test
    fun theAccuracyNote_doesNotRepeatTheAdviceOfTheEstimateNote() {
        val withEstimate = resultTexts(1000, 996, estimatedGroups = 3, photos = 5)
        assertEquals(UiText.Plural(R.plurals.result_estimate, 3), withEstimate.estimate)
        assertEquals("the estimate note already says to spread the pieces out", UiText.Plural(R.plurals.result_accuracy_missing, 6), withEstimate.accuracy)
        // without estimated groups the accuracy note is the only one that tells what to do
        assertTrue(resultTexts(1000, 996, estimatedGroups = 0, photos = 5).accuracy is UiText.Joined)
    }
}
