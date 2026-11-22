package com.example.puzzlecounter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.puzzlecounter.R
import java.text.NumberFormat

/** The colour and the symbol that tell at a glance what the scan means. */
internal class OutcomeLook(val accent: Color, val container: Color, val icon: ImageVector)

@Composable
internal fun Outcome.look(): OutcomeLook {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        Outcome.COMPLETE -> OutcomeLook(colors.secondary, colors.secondaryContainer, Icons.Rounded.CheckCircle)
        Outcome.EXACT_VARIANT -> OutcomeLook(colors.primary, colors.primary.copy(alpha = 0.2f), Icons.Rounded.Lightbulb)
        Outcome.OTHER_SIZE -> OutcomeLook(colors.tertiary, colors.tertiaryContainer, Icons.Rounded.Straighten)
        Outcome.TOO_MANY -> OutcomeLook(colors.tertiary, colors.tertiaryContainer, Icons.AutoMirrored.Rounded.HelpOutline)
        Outcome.MISSING -> OutcomeLook(colors.error, colors.errorContainer, Icons.Rounded.Warning)
    }
}

/**
 * The result of a check. The real result of the scan is always the first and biggest thing on it; below it stands what
 * it means for the puzzle that was entered (see [resultTexts]), the numbers side by side, and notes.
 *
 * The X in the corner (and going back, which does the same) ends the check: the app goes to the start screen, and the
 * photos of the check are deleted, only its counts are kept.
 */
@Composable
fun ResultScreen(uiState: PuzzleUiState, onClose: () -> Unit) {
    // Closing ends the check and clears its state at once, while this screen is still sliding away: it keeps showing
    // the result it was opened with (and does not, for a moment, show an empty check as a perfect one).
    val check = remember { uiState }
    val texts = resultTexts(check.targetPieceCount, check.countedPieces, check.countedClusters, check.calculatedPiles)
    val look = texts.outcome.look()
    val haptic = LocalHapticFeedback.current
    val isComplete = texts.outcome == Outcome.COMPLETE

    BackHandler(onBack = onClose)

    LaunchedEffect(isComplete) {
        if (isComplete) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    AppBackground(uiState = uiState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            CloseBar(onClose)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = texts.scanned.asString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() }
                )
                Spacer(Modifier.height(20.dp))

                OutcomeCard(texts, look)
                Spacer(Modifier.height(16.dp))
                Figures(texts.figures)

                for (note in texts.notes) {
                    Spacer(Modifier.height(12.dp))
                    Note(note)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** The top of a screen that is left by closing it: the X in the upper right corner. */
@Composable
internal fun CloseBar(onClose: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClose()
            },
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close), modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * What the scan means: the symbol with a title, and below them, over the whole width of the card, a short message (next
 * to the symbol it would be a narrow column that grows tall with long texts or large fonts).
 */
@Composable
internal fun OutcomeCard(texts: ResultTexts, look: OutcomeLook) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = look.container, modifier = Modifier.size(48.dp)) {
                    Icon(look.icon, contentDescription = null, modifier = Modifier.padding(10.dp).fillMaxSize(), tint = look.accent)
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = texts.title.asString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = look.accent,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = texts.message.asString(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private val FigureGap = 8.dp
private val FigurePadding = 8.dp

/**
 * The entered, the scanned and the possible number next to each other. Where something would not fit into its card
 * (a long number such as 12.096 or a word such as "Ravensburger" on a narrow screen or with a large font) they are put
 * one below the other instead, so that no number is cut off and no word is broken.
 */
@Composable
internal fun Figures(figures: List<ResultFigure>) {
    // Numbers with the grouping of the language of the app, which may differ from the one of the system.
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val numbers = figures.map { format.format(it.count) }
    val labels = figures.map { it.label.asString() }
    val captions = figures.map { it.caption?.asString() }

    val measurer = rememberTextMeasurer()
    val numberStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    val labelStyle = MaterialTheme.typography.labelMedium
    val captionStyle = MaterialTheme.typography.labelSmall

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardInnerWidth = with(LocalDensity.current) {
            ((maxWidth - FigureGap * (figures.size - 1)) / figures.size - FigurePadding * 2).roundToPx()
        }
        fun widthOf(text: String, style: TextStyle) = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
        fun widestWordOf(text: String, style: TextStyle) = text.split(' ').maxOf { widthOf(it, style) }
        val sideBySide = figures.indices.all { i ->
            widthOf(numbers[i], numberStyle) <= cardInnerWidth &&
                widestWordOf(labels[i], labelStyle) <= cardInnerWidth &&
                (captions[i]?.let { widestWordOf(it, captionStyle) } ?: 0) <= cardInnerWidth
        }

        if (sideBySide) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(FigureGap)
            ) {
                for (i in figures.indices) {
                    FigureCard(labels[i], numbers[i], captions[i], figures[i].highlighted, stacked = false, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(FigureGap)) {
                for (i in figures.indices) {
                    FigureCard(labels[i], numbers[i], captions[i], figures[i].highlighted, stacked = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun FigureCard(label: String, number: String, caption: String?, highlighted: Boolean, stacked: Boolean, modifier: Modifier) {
    Surface(
        // read as one: "Scanned 203"
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        if (stacked) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    caption?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.width(12.dp))
                Text(number, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = FigurePadding, vertical = 12.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
                Text(number, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                caption?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

/** A hint below the numbers. */
@Composable
internal fun Note(text: UiText) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            modifier = Modifier.padding(top = 2.dp).size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text.asString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        )
    }
}
