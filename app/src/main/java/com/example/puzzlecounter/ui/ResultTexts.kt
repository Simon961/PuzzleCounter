package com.example.puzzlecounter.ui

/** How the counted pieces compare to the number the puzzle should have. */
internal enum class Outcome { COMPLETE, MISSING, TOO_MANY }

/** The texts of the result screen. [hint] explains what may be behind a difference, [estimate] that some pieces were estimated. */
internal class ResultTexts(
    val outcome: Outcome,
    val title: String,
    val message: String,
    val hint: String?,
    val estimate: String?,
)

/**
 * The result texts for [counted] pieces where [target] were expected. [estimatedGroups] is the number of places
 * where pieces lay on each other or touched, so that their number was estimated from the area.
 */
internal fun resultTexts(target: Int, counted: Int, estimatedGroups: Int): ResultTexts {
    val estimate = when {
        estimatedGroups <= 0 -> null
        else -> {
            val places = if (estimatedGroups == 1) "In 1 Gruppe lagen Teile" else "In $estimatedGroups Gruppen lagen Teile"
            "$places übereinander oder berührten sich – dort ist die Zahl geschätzt. " +
                "Für ein genaues Ergebnis die Teile mit Abstand auslegen."
        }
    }
    val difference = target - counted
    return when {
        difference == 0 -> ResultTexts(Outcome.COMPLETE, "Perfekt!", "Alle Teile sind vorhanden.", null, estimate)
        difference > 0 -> ResultTexts(
            outcome = Outcome.MISSING,
            title = "Achtung!",
            message = if (difference == 1) "Es fehlt 1 Teil." else "Es fehlen $difference Teile.",
            hint = "Prüfe, ob alle Teile auf dem Foto liegen: Teile unter anderen Teilen oder außerhalb des Fotos werden nicht erkannt.",
            estimate = estimate,
        )
        else -> {
            val extra = -difference
            ResultTexts(
                outcome = Outcome.TOO_MANY,
                title = "Mehr Teile als erwartet",
                message = if (extra == 1) "Es wurde 1 Teil mehr gezählt als angegeben." else "Es wurden $extra Teile mehr gezählt als angegeben.",
                hint = "Mögliche Gründe: Das Puzzle hat mehr Teile als angegeben, es liegen Teile eines anderen Puzzles dabei, " +
                    "oder Teile wurden doppelt gezählt.",
                estimate = estimate,
            )
        }
    }
}
