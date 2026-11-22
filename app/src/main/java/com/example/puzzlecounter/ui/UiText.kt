package com.example.puzzlecounter.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * A text of the app that is only turned into words where it is shown, so that it follows the language of the app.
 * Lets texts be put together outside of Compose (and checked in plain unit tests) without a language.
 *
 * The arguments of a text are numbers (shown with the grouping of the language if the text says `%,d`), strings
 * that need no translation, or other [UiText]s such as the name of a maker.
 */
internal sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /**
     * A plural whose form is chosen by [count]. Without [args] the text gets [count] as its only argument; a text with
     * several numbers names them in [args], and only the number that [count] is has a noun after it (the other
     * numbers must not, as the noun would have to agree with them in some languages).
     */
    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    /** Sentences shown as one paragraph. */
    data class Joined(val parts: List<UiText>) : UiText
}

@Composable
private fun List<Any>.resolved(): Array<Any> = map { if (it is UiText) it.asString() else it }.toTypedArray()

@Composable
internal fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.resolved())
    is UiText.Plural -> pluralStringResource(id, count, *args.resolved())
    is UiText.Joined -> parts.map { it.asString() }.joinToString(" ")
}
