package com.example.puzzlecounter.ui

import androidx.annotation.PluralsRes
import com.example.puzzlecounter.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * What the scan means for the puzzle that was entered.
 *
 * - [COMPLETE]: exactly the entered number.
 * - [EXACT_VARIANT]: not the entered number, but what puzzles with this number on the box often really have (200 -> 204).
 * - [OTHER_SIZE]: neither, but the number of another known puzzle size (entered 200, counted 500): wrong size entered?
 * - [MISSING], [TOO_MANY]: fewer or more than entered, and nothing in [PuzzleSizes] explains it.
 */
internal enum class Outcome { COMPLETE, EXACT_VARIANT, OTHER_SIZE, MISSING, TOO_MANY }

/**
 * By how many pieces the count of [photos] photos may be off although everything was done right. Counting the same
 * photo turned and mirrored gives results that differ by up to 4 pieces (standard deviation 1.2), and where pieces
 * lie on each other their number is estimated. Two standard deviations, rounded up, make about 3 for one photo; the
 * errors of several photos partly cancel, so they add up like the square root of their number.
 */
internal fun countingTolerance(photos: Int): Int = ceil(2.5 * sqrt(photos.coerceAtLeast(1).toDouble())).toInt()

/**
 * How the scan of [counted] pieces in [photos] photos relates to the [target] that was entered, and to the sizes
 * puzzles really come in ([PuzzleSizes]). A count is only taken for a size if it lies within the counting accuracy
 * ([tolerance]) of its exact number.
 */
internal class ResultAnalysis(
    val outcome: Outcome,
    val tolerance: Int,
    /** The size the scan points to: [Outcome.EXACT_VARIANT] and [Outcome.OTHER_SIZE] only. */
    val matchingSize: PuzzleSize? = null,
    /** Sizes that fit the scan as well and are the entered number itself (1000 next to 1008), for telling them apart. */
    val alternatives: List<PuzzleSize> = emptyList(),
    /**
     * A size that puzzles with the entered number often have and that has more pieces than counted: if the puzzle is
     * one of them, pieces are missing although the count matches the entered number.
     */
    val largerVariant: PuzzleSize? = null,
)

internal fun analyseResult(target: Int, counted: Int, photos: Int): ResultAnalysis {
    val tolerance = countingTolerance(photos)
    val difference = target - counted
    fun fits(size: PuzzleSize) = abs(size.exactCount - counted) <= tolerance

    // 1. The count is what puzzles with the entered number often really have (and not just the entered number itself)
    val variants = PuzzleSizes.variantsOf(target)
    val fittingVariant = variants.filter(::fits).minByOrNull { abs(it.exactCount - counted) }
    if (fittingVariant != null && difference != 0) {
        val alternatives = listOfNotNull(PuzzleSizes.withExactCount(target)).filter(::fits)
        return ResultAnalysis(Outcome.EXACT_VARIANT, tolerance, fittingVariant, alternatives)
    }

    // 2. The count matches the entered number within the counting accuracy
    if (abs(difference) <= tolerance) {
        val outcome = when {
            difference == 0 -> Outcome.COMPLETE
            difference > 0 -> Outcome.MISSING
            else -> Outcome.TOO_MANY
        }
        val larger = if (difference >= 0) variants.filter { it.exactCount > counted }.minByOrNull { it.exactCount } else null
        return ResultAnalysis(outcome, tolerance, largerVariant = larger)
    }

    // 3. The count is far from the entered number: is it the number of another size?
    val other = PuzzleSizes.nearest(counted, tolerance)
    return when {
        other != null -> ResultAnalysis(Outcome.OTHER_SIZE, tolerance, other)
        difference > 0 -> ResultAnalysis(Outcome.MISSING, tolerance)
        else -> ResultAnalysis(Outcome.TOO_MANY, tolerance)
    }
}

/** What an earlier check ended with, for the history: the photos it took followed from the entered number. */
internal fun historyOutcome(target: Int, counted: Int): Outcome =
    analyseResult(target, counted, PuzzleViewModel.pilesFor(target)).outcome

/**
 * The texts of an earlier check, the same its result screen showed (the places where the number was estimated are
 * only known if the numbers of its photos were kept).
 */
internal fun resultTextsOf(entry: PuzzleHistoryEntry): ResultTexts =
    resultTexts(entry.target, entry.actual, entry.clusters, PuzzleViewModel.pilesFor(entry.target))

/** A number with its name, shown next to the others on the result screen: the entered, the scanned, the possible one. */
internal class ResultFigure(
    val label: UiText,
    val count: Int,
    val caption: UiText? = null,
    val highlighted: Boolean = false,
)

/**
 * The texts of the result screen: [scanned] is the real result of the scan and always comes first, [title] and
 * [message] say what it means, [figures] show the numbers next to each other and [notes] explain further. They are put
 * into words only on the screen, in the language of the app.
 */
internal class ResultTexts(
    val outcome: Outcome,
    val scanned: UiText,
    val title: UiText,
    val message: UiText,
    val notes: List<UiText>,
    val figures: List<ResultFigure>,
)

/**
 * The result texts for [counted] pieces where [target] were entered, counted in [photos] photos. [estimatedGroups]
 * is the number of places where pieces lay on each other or touched, so that their number was estimated from the area.
 */
internal fun resultTexts(target: Int, counted: Int, estimatedGroups: Int, photos: Int = 1): ResultTexts {
    val analysis = analyseResult(target, counted, photos)
    val difference = target - counted
    val estimate = if (estimatedGroups > 0) UiText.Plural(R.plurals.result_estimate, estimatedGroups) else null

    val entered = ResultFigure(UiText.Res(R.string.result_figure_box), target)
    val scanned = ResultFigure(UiText.Res(R.string.result_figure_scanned), counted, highlighted = true)
    fun possible(size: PuzzleSize) =
        ResultFigure(UiText.Res(R.string.result_figure_possible), size.exactCount, caption = UiText.Res(size.maker.nameRes))

    // A difference this small is what the counting itself can be off by: it may be no difference at all.
    fun accuracy(@PluralsRes whatMayBeRight: Int): UiText? {
        if (abs(difference) > analysis.tolerance) return null
        val note = UiText.Plural(whatMayBeRight, analysis.tolerance)
        // the note about estimated groups already advises to spread the pieces out
        return if (estimate == null) UiText.Joined(listOf(note, UiText.Res(R.string.result_accuracy_advice))) else note
    }

    val larger = analysis.largerVariant?.let {
        UiText.Plural(R.plurals.result_note_larger_variant, it.exactCount, listOf(target, it.exactCount, it.exactCount - counted))
    }
    val size = analysis.matchingSize

    return when (analysis.outcome) {
        Outcome.COMPLETE -> ResultTexts(
            outcome = Outcome.COMPLETE,
            scanned = UiText.Plural(R.plurals.result_scanned, counted),
            title = UiText.Res(R.string.result_title_complete),
            message = UiText.Res(R.string.result_message_complete),
            notes = listOfNotNull(larger, estimate),
            figures = listOf(entered, scanned),
        )
        Outcome.MISSING -> ResultTexts(
            outcome = Outcome.MISSING,
            scanned = UiText.Plural(R.plurals.result_scanned, counted),
            title = UiText.Res(R.string.result_title_missing),
            message = UiText.Plural(R.plurals.result_message_missing, difference),
            notes = listOfNotNull(accuracy(R.plurals.result_accuracy_missing), UiText.Res(R.string.result_hint_missing), larger, estimate),
            figures = listOf(entered, scanned),
        )
        Outcome.TOO_MANY -> ResultTexts(
            outcome = Outcome.TOO_MANY,
            scanned = UiText.Plural(R.plurals.result_scanned, counted),
            title = UiText.Res(R.string.result_title_too_many),
            message = UiText.Plural(R.plurals.result_message_too_many, -difference),
            notes = listOfNotNull(accuracy(R.plurals.result_accuracy_too_many), UiText.Res(R.string.result_hint_too_many), estimate),
            figures = listOf(entered, scanned),
        )
        Outcome.EXACT_VARIANT -> ResultTexts(
            outcome = Outcome.EXACT_VARIANT,
            scanned = UiText.Plural(R.plurals.result_scanned, counted),
            title = UiText.Plural(R.plurals.result_title_exact_variant, size!!.exactCount),
            message = UiText.Plural(R.plurals.result_message_exact_variant, size.exactCount, listOf(target, size.exactCount)),
            notes = listOfNotNull(edgeTip(listOf(size) + analysis.alternatives), estimate),
            figures = listOf(entered, scanned, possible(size)),
        )
        Outcome.OTHER_SIZE -> ResultTexts(
            outcome = Outcome.OTHER_SIZE,
            scanned = UiText.Plural(R.plurals.result_scanned, counted),
            title = UiText.Plural(R.plurals.result_title_other_size, size!!.exactCount),
            message = UiText.Res(R.string.result_message_other_size, listOf(target)),
            // fewer than entered may also be pieces that were not in the photo, whatever size another puzzle has
            notes = listOfNotNull(edgeTip(listOf(size)), UiText.Res(R.string.result_hint_missing).takeIf { difference > 0 }, estimate),
            figures = listOf(entered, scanned, possible(size)),
        )
    }
}

/**
 * How to tell by hand which of the [candidates] the puzzle is: count its border pieces (with the 4 corners). Only if
 * the grid of every candidate is known, and for several candidates only if they differ in their border.
 */
private fun edgeTip(candidates: List<PuzzleSize>): UiText? {
    val grids = candidates.sortedBy { it.exactCount }.map { it to it.grid }
    if (grids.any { it.second == null }) return null
    return when (grids.size) {
        1 -> {
            val grid = grids[0].second!!
            UiText.Res(R.string.result_tip_edge_pieces, listOf(grid.toString(), grid.edgePieces))
        }
        2 -> {
            val (first, second) = grids
            val (firstGrid, secondGrid) = first.second!! to second.second!!
            if (firstGrid.edgePieces == secondGrid.edgePieces) {
                null
            } else {
                UiText.Res(
                    R.string.result_tip_edge_compare,
                    listOf(firstGrid.edgePieces, firstGrid.toString(), first.first.exactCount, secondGrid.edgePieces, secondGrid.toString(), second.first.exactCount),
                )
            }
        }
        else -> null
    }
}
