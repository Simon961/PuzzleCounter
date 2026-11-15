package com.example.puzzlecounter.ui

import androidx.annotation.PluralsRes
import com.example.puzzlecounter.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/** How the counted pieces compare to the number the puzzle should have. */
internal enum class Outcome { COMPLETE, MISSING, TOO_MANY }

/**
 * The texts of the result screen. [hint] explains what may be behind a difference, [estimate] that some pieces were
 * estimated, [accuracy] that a small difference is within what the count can tell. They are put into words only on
 * the screen, in the language of the app.
 */
internal class ResultTexts(
    val outcome: Outcome,
    val title: UiText,
    val message: UiText,
    val hint: UiText?,
    val estimate: UiText?,
    val accuracy: UiText? = null,
)

/**
 * By how many pieces the count of [photos] photos may be off although everything was done right. Counting the same
 * photo turned and mirrored gives results that differ by up to 4 pieces (standard deviation 1.2), and where pieces
 * lie on each other their number is estimated. Two standard deviations, rounded up, make about 3 for one photo; the
 * errors of several photos partly cancel, so they add up like the square root of their number.
 */
internal fun countingTolerance(photos: Int): Int = ceil(2.5 * sqrt(photos.coerceAtLeast(1).toDouble())).toInt()

/**
 * The result texts for [counted] pieces where [target] were expected, counted in [photos] photos. [estimatedGroups]
 * is the number of places where pieces lay on each other or touched, so that their number was estimated from the area.
 */
internal fun resultTexts(target: Int, counted: Int, estimatedGroups: Int, photos: Int = 1): ResultTexts {
    val estimate = if (estimatedGroups > 0) UiText.Plural(R.plurals.result_estimate, estimatedGroups) else null
    val difference = target - counted
    // A difference that small is what the counting itself can be off by: it may be no difference at all.
    val tolerance = countingTolerance(photos)
    fun accuracy(@PluralsRes whatMayBeRight: Int): UiText? {
        if (abs(difference) > tolerance) return null
        val note = UiText.Plural(whatMayBeRight, tolerance)
        // the note about estimated groups already advises to spread the pieces out
        return if (estimate == null) UiText.Joined(listOf(note, UiText.Res(R.string.result_accuracy_advice))) else note
    }
    return when {
        difference == 0 -> ResultTexts(
            outcome = Outcome.COMPLETE,
            title = UiText.Res(R.string.result_title_complete),
            message = UiText.Res(R.string.result_message_complete),
            hint = null,
            estimate = estimate,
        )
        difference > 0 -> ResultTexts(
            outcome = Outcome.MISSING,
            title = UiText.Res(R.string.result_title_missing),
            message = UiText.Plural(R.plurals.result_message_missing, difference),
            hint = UiText.Res(R.string.result_hint_missing),
            estimate = estimate,
            accuracy = accuracy(R.plurals.result_accuracy_missing),
        )
        else -> ResultTexts(
            outcome = Outcome.TOO_MANY,
            title = UiText.Res(R.string.result_title_too_many),
            message = UiText.Plural(R.plurals.result_message_too_many, -difference),
            hint = UiText.Res(R.string.result_hint_too_many),
            estimate = estimate,
            accuracy = accuracy(R.plurals.result_accuracy_too_many),
        )
    }
}
