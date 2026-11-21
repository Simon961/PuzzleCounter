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

    /** The result of [counted] pieces for a puzzle entered as [target], counted in as many photos as the app takes for it. */
    private fun result(target: Int, counted: Int, estimatedGroups: Int = 0) =
        resultTexts(target, counted, estimatedGroups, PuzzleViewModel.pilesFor(target))

    private fun scannedPlural(counted: Int) = UiText.Plural(R.plurals.result_scanned, counted)

    private val advice = UiText.Res(R.string.result_accuracy_advice)

    // --- the scan itself always comes first ---------------------------------------------------------------------------

    @Test
    fun theScannedNumber_isAlwaysTheHeadline() {
        for ((target, counted) in listOf(200 to 200, 200 to 190, 200 to 210, 200 to 204, 200 to 300, 60_000 to 59_970, 1000 to 1004)) {
            val texts = result(target, counted)
            assertEquals("$target / $counted", scannedPlural(counted), texts.scanned)
            val figures = texts.figures
            assertEquals(UiText.Res(R.string.result_figure_box), figures[0].label)
            assertEquals(target, figures[0].count)
            assertEquals(UiText.Res(R.string.result_figure_scanned), figures[1].label)
            assertEquals(counted, figures[1].count)
            assertTrue("the scanned number is the highlighted one", figures[1].highlighted && !figures[0].highlighted)
        }
    }

    // --- complete, missing, too many (nothing in the reference explains the difference) -------------------------------------

    @Test
    fun completePuzzle() {
        val texts = result(500, 500)
        assertEquals(Outcome.COMPLETE, texts.outcome)
        assertEquals(UiText.Res(R.string.result_title_complete), texts.title)
        assertEquals(UiText.Res(R.string.result_message_complete), texts.message)
        assertEquals("nothing to add: no known puzzle with 500 on the box has another number of pieces", emptyList<UiText>(), texts.notes)
        assertEquals(2, texts.figures.size)
    }

    @Test
    fun missingPieces_areCountedInThePlural() {
        val texts = result(200, 190)
        assertEquals(Outcome.MISSING, texts.outcome)
        assertEquals(UiText.Res(R.string.result_title_missing), texts.title)
        // the number picks the grammatical form (one / few / many / other) and is shown as well
        assertEquals(UiText.Plural(R.plurals.result_message_missing, 10), texts.message)
        assertEquals(UiText.Res(R.string.result_hint_missing), texts.notes.single())
        assertEquals(UiText.Plural(R.plurals.result_message_missing, 1), result(300, 299).message)
    }

    @Test
    fun tooManyPieces_areCountedInThePlural() {
        val texts = result(200, 210)
        assertEquals(Outcome.TOO_MANY, texts.outcome)
        assertEquals(UiText.Res(R.string.result_title_too_many), texts.title)
        assertEquals(UiText.Plural(R.plurals.result_message_too_many, 10), texts.message)
        assertEquals(UiText.Res(R.string.result_hint_too_many), texts.notes.single())
    }

    @Test
    fun estimatedGroups_areMentionedWheneverThereAreSome() {
        assertEquals(UiText.Plural(R.plurals.result_estimate, 1), result(500, 500, 1).notes.last())
        assertEquals(UiText.Plural(R.plurals.result_estimate, 4), result(500, 480, 4).notes.last())
        assertTrue(result(500, 480, 0).notes.none { it is UiText.Plural && it.id == R.plurals.result_estimate })
    }

    // --- the count is what puzzles with this box number often really have: the case of the 204 piece puzzle ----------------

    @Test
    fun enteredTwoHundred_scannedTwoHundredFour_mayBeAPuzzleOfExactly204() {
        val texts = result(200, 204)
        assertEquals(Outcome.EXACT_VARIANT, texts.outcome)
        assertEquals(UiText.Plural(R.plurals.result_title_exact_variant, 204), texts.title)
        assertEquals(UiText.Plural(R.plurals.result_message_exact_variant, 204, listOf(200, 204)), texts.message)
        // 200 on the box, 204 scanned, 204 possible (a Ravensburger): the three numbers side by side
        assertEquals(listOf(200, 204, 204), texts.figures.map { it.count })
        assertEquals(UiText.Res(R.string.result_figure_possible), texts.figures[2].label)
        assertEquals(UiText.Res(PuzzleMaker.RAVENSBURGER.nameRes), texts.figures[2].caption)
        // how to check by hand: 17 x 12 pieces have 54 border pieces
        assertEquals(UiText.Res(R.string.result_tip_edge_pieces, listOf("17 × 12", 54)), texts.notes.single())
    }

    @Test
    fun theCountingAccuracy_decidesWhichScansFit204() {
        // one photo: +-3
        for (counted in 201..207) assertEquals("$counted", Outcome.EXACT_VARIANT, result(200, counted).outcome)
        assertEquals(Outcome.COMPLETE, result(200, 200).outcome)
        assertEquals(Outcome.TOO_MANY, result(200, 208).outcome)
        assertEquals(Outcome.MISSING, result(200, 199).outcome)
    }

    @Test
    fun enteredTheExactNumber_everythingFitsAlready() {
        val texts = result(204, 204)
        assertEquals(Outcome.COMPLETE, texts.outcome)
        assertEquals(emptyList<UiText>(), texts.notes)
        // and 204 entered, 207 scanned is what it always was: a little more than entered, within the accuracy
        assertEquals(Outcome.TOO_MANY, result(204, 207).outcome)
        assertEquals(Outcome.MISSING, result(204, 200).outcome)
    }

    @Test
    fun aCountThatMatchesTheBoxNumber_mentionsThatThePuzzleMayHaveMore() {
        // 200 entered and 200 scanned: if the puzzle is one of 204 pieces, 4 are missing
        val complete = result(200, 200)
        assertEquals(Outcome.COMPLETE, complete.outcome)
        assertEquals(UiText.Plural(R.plurals.result_note_larger_variant, 204, listOf(200, 204, 4)), complete.notes.single())
        // a little less than entered: the same, with the bigger gap to 204
        val missing = result(200, 198)
        assertEquals(Outcome.MISSING, missing.outcome)
        assertEquals(UiText.Plural(R.plurals.result_note_larger_variant, 204, listOf(200, 204, 6)), missing.notes.last())
        // far less than entered: the puzzle is obviously incomplete, no need to talk about 204
        assertTrue(result(200, 150).notes.none { it is UiText.Plural && it.id == R.plurals.result_note_larger_variant })
    }

    @Test
    fun thousand_canBeTheThousandOrTheThousandAndEight_andTheBorderTellsThem() {
        // 5 photos, +-6: 1004 fits 1000 and 1008
        val texts = result(1000, 1004)
        assertEquals(Outcome.EXACT_VARIANT, texts.outcome)
        assertEquals(UiText.Plural(R.plurals.result_title_exact_variant, 1008), texts.title)
        // 1000 = 40 x 25 has 126 border pieces, 1008 = 36 x 28 has 124
        assertEquals(UiText.Res(R.string.result_tip_edge_compare, listOf(126, "40 × 25", 1000, 124, "36 × 28", 1008)), texts.notes.single())
        assertEquals(listOf(1000, 1004, 1008), texts.figures.map { it.count })
        // 1010 fits only 1008
        val only1008 = result(1000, 1010)
        assertEquals(Outcome.EXACT_VARIANT, only1008.outcome)
        assertEquals(UiText.Res(R.string.result_tip_edge_pieces, listOf("36 × 28", 124)), only1008.notes.single())
        // exactly 1000: all there, but 1008 would mean 8 missing
        val complete = result(1000, 1000)
        assertEquals(Outcome.COMPLETE, complete.outcome)
        assertEquals(UiText.Plural(R.plurals.result_note_larger_variant, 1008, listOf(1000, 1008, 8)), complete.notes.single())
    }

    @Test
    fun sizesWithoutAKnownGrid_giveNoBorderTip() {
        val texts = result(3000, 3168)
        assertEquals(Outcome.EXACT_VARIANT, texts.outcome)
        assertEquals(emptyList<UiText>(), texts.notes)
        assertEquals(listOf(3000, 3168, 3168), texts.figures.map { it.count })
    }

    @Test
    fun everyKnownRounding_isRecognisedAndNeverMistakenForTheBoxNumber() {
        for (size in PuzzleSizes.all.filter { it.exactCount != it.boxCount }) {
            val photos = PuzzleViewModel.pilesFor(size.boxCount)
            val tolerance = countingTolerance(photos)
            assertTrue("${size.boxCount}: the counting accuracy $tolerance must be smaller than the rounding", Math.abs(size.exactCount - size.boxCount) > tolerance)
            val exact = resultTexts(size.boxCount, size.exactCount, 0, photos)
            assertEquals("${size.boxCount} -> ${size.exactCount}", Outcome.EXACT_VARIANT, exact.outcome)
            assertEquals(size.exactCount, exact.figures.last().count)
            assertEquals(Outcome.EXACT_VARIANT, resultTexts(size.boxCount, size.exactCount - tolerance, 0, photos).outcome)
            assertEquals(Outcome.EXACT_VARIANT, resultTexts(size.boxCount, size.exactCount + tolerance, 0, photos).outcome)
            // the box number itself is complete, and the rounding is pointed out
            val onTheBox = resultTexts(size.boxCount, size.boxCount, 0, photos)
            assertEquals(Outcome.COMPLETE, onTheBox.outcome)
            assertEquals(UiText.Plural(R.plurals.result_note_larger_variant, size.exactCount, listOf(size.boxCount, size.exactCount, size.exactCount - size.boxCount)), onTheBox.notes.single())
        }
    }

    // --- the count is the number of another size: wrong size entered? -------------------------------------------------------------

    @Test
    fun scannedThreeHundred_enteredTwoHundred_matchesAnotherSize() {
        val texts = result(200, 300)
        assertEquals(Outcome.OTHER_SIZE, texts.outcome)
        assertEquals(UiText.Plural(R.plurals.result_title_other_size, 300), texts.title)
        assertEquals(UiText.Res(R.string.result_message_other_size, listOf(200)), texts.message)
        assertEquals(listOf(200, 300, 300), texts.figures.map { it.count })
        // 20 x 15 pieces have 66 border pieces
        assertEquals(UiText.Res(R.string.result_tip_edge_pieces, listOf("20 × 15", 66)), texts.notes.single())
    }

    @Test
    fun aSmallPuzzleEnteredAsABigOne_isRecognised() {
        // 500 entered (3 photos, +-5), 204 scanned
        val texts = result(500, 204)
        assertEquals(Outcome.OTHER_SIZE, texts.outcome)
        assertEquals(204, texts.figures.last().count)
        // the exact count entered although the puzzle is the one with the round number: 1008 entered, 1000 scanned
        assertEquals(Outcome.OTHER_SIZE, result(1008, 1000).outcome)
        assertEquals(1000, result(1008, 1000).figures.last().count)
    }

    @Test
    fun aCountBelowTheEnteredNumber_thatMatchesAnotherSize_alsoSaysToLookForPiecesOutsideThePhoto() {
        val missingHint = UiText.Res(R.string.result_hint_missing)
        // 1000 entered, 500 scanned: another size - or half of the pieces were not in the photo
        val fewer = result(1000, 500)
        assertEquals(Outcome.OTHER_SIZE, fewer.outcome)
        assertTrue(missingHint in fewer.notes)
        // 200 entered, 102 scanned: nearest to the 104 piece size, but it is just as likely that half is missing
        assertEquals(Outcome.OTHER_SIZE, result(200, 102).outcome)
        assertTrue(missingHint in result(200, 102).notes)
        // more than entered: nothing can be missing from the photo
        assertTrue(missingHint !in result(200, 300).notes)
    }

    @Test
    fun aCountNearNoKnownSize_isJustMissingOrTooMany() {
        assertEquals(Outcome.MISSING, result(200, 150).outcome)
        assertEquals(Outcome.TOO_MANY, result(200, 250).outcome)
        assertEquals(Outcome.TOO_MANY, result(204, 260).outcome)
        assertEquals(2, result(200, 250).figures.size)
    }

    @Test
    fun theLargestPuzzles_workToo() {
        // 60000 pieces: 300 photos, +-44
        assertEquals(44, countingTolerance(PuzzleViewModel.pilesFor(60_000)))
        assertEquals(Outcome.COMPLETE, result(60_000, 60_000).outcome)
        val near = result(60_000, 59_970)
        assertEquals(Outcome.MISSING, near.outcome)
        assertTrue("within the accuracy of 44", near.notes.any { it is UiText.Joined })
        assertEquals(Outcome.TOO_MANY, result(60_000, 60_040).outcome)
        assertEquals(Outcome.MISSING, result(60_000, 59_950).outcome)
        // the sizes of the biggest puzzles are found, and 99999 (the largest number that can be entered) is nothing special
        assertEquals(Outcome.OTHER_SIZE, result(99_999, 60_000).outcome)
        assertEquals(Outcome.MISSING, result(99_999, 99_000).outcome)
        assertEquals(Outcome.EXACT_VARIANT, result(32_000, 32_256).outcome)
    }

    // --- the history says what the result screen said ------------------------------------------------------------------------------

    @Test
    fun theHistory_judgesLikeTheResultScreen() {
        assertEquals(Outcome.COMPLETE, historyOutcome(200, 200))
        assertEquals(Outcome.EXACT_VARIANT, historyOutcome(200, 204))
        assertEquals(Outcome.OTHER_SIZE, historyOutcome(200, 300))
        assertEquals(Outcome.MISSING, historyOutcome(1000, 996))
        assertEquals(Outcome.TOO_MANY, historyOutcome(500, 520))
        for ((target, counted) in listOf(200 to 204, 1000 to 1008, 1500 to 1530, 3000 to 3168, 60_000 to 60_000, 100 to 90)) {
            assertEquals("$target / $counted", result(target, counted).outcome, historyOutcome(target, counted))
        }
    }

    // --- the accuracy of the count ------------------------------------------------------------------------------------------------

    @Test
    fun smallDifferences_areMarkedAsWithinTheCountingAccuracy() {
        // a puzzle of 150 pieces is one photo, which is counted to about +-3 (no known size lies near 150)
        assertEquals(3, countingTolerance(PuzzleViewModel.pilesFor(150)))
        assertEquals(
            UiText.Joined(listOf(UiText.Plural(R.plurals.result_accuracy_missing, 3), advice)),
            result(150, 148).notes.first()
        )
        assertEquals(
            UiText.Joined(listOf(UiText.Plural(R.plurals.result_accuracy_too_many, 3), advice)),
            result(150, 153).notes.first()
        )
        // beyond the accuracy the difference is a real one
        assertTrue(result(150, 146).notes.none { it is UiText.Joined })
        assertTrue(result(150, 154).notes.none { it is UiText.Joined })
    }

    @Test
    fun theTolerance_growsWithTheNumberOfPhotosButSlowerThanTheirNumber() {
        assertEquals(4, countingTolerance(2))
        assertEquals(6, countingTolerance(5))
        assertEquals(8, countingTolerance(10))
        assertEquals(3, countingTolerance(0)) // never less than one photo
        // five photos of a 1000 piece puzzle (+-6): 4 missing pieces are within the accuracy, 7 are not
        assertEquals(UiText.Plural(R.plurals.result_accuracy_missing, 6), (resultTexts(1000, 996, 0, photos = 5).notes.first() as UiText.Joined).parts.first())
        assertTrue(resultTexts(1000, 993, 0, photos = 5).notes.none { it is UiText.Joined })
    }

    @Test
    fun theAccuracyNote_doesNotRepeatTheAdviceOfTheEstimateNote() {
        val withEstimate = result(150, 147, estimatedGroups = 3)
        assertEquals(UiText.Plural(R.plurals.result_estimate, 3), withEstimate.notes.last())
        assertEquals("the estimate note already says to spread the pieces out", UiText.Plural(R.plurals.result_accuracy_missing, 3), withEstimate.notes.first())
        // without estimated groups the accuracy note is the only one that tells what to do
        assertTrue(result(150, 147).notes.first() is UiText.Joined)
        assertNull(result(150, 147).notes.firstOrNull { it is UiText.Plural && it.id == R.plurals.result_estimate })
    }
}
