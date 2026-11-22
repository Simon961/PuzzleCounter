package com.example.puzzlecounter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.puzzlecounter.R
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/**
 * Everything a check of the history found out, in the order it is read: how many pieces were scanned, what each photo
 * counted, and at the end what the numbers mean for the puzzle that was entered (the same texts as the result screen
 * had, so that a puzzle with 200 on its box that has 204 pieces says so here as well). The photos themselves are not
 * kept, only their numbers.
 */
@Composable
fun HistoryDetailScreen(uiState: PuzzleUiState, entry: PuzzleHistoryEntry, onClose: () -> Unit) {
    val texts = resultTextsOf(entry)
    val look = texts.outcome.look()
    // Dates and numbers in the language of the app (which may differ from the language of the system).
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(entry.date, locale) { DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, locale).format(Date(entry.date)) }
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val widest = entry.photos.maxOfOrNull { it.pieces }?.coerceAtLeast(1) ?: 1

    AppBackground(uiState = uiState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            CloseBar(onClose)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = texts.scanned.asString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().semantics { heading() }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = pluralStringResource(R.plurals.history_entry_title, entry.target, entry.target),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }

                item { PhotosCard(entry, numbers, widest) }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(16.dp))
                        OutcomeCard(texts, look)
                        Spacer(Modifier.height(16.dp))
                        Figures(texts.figures)
                    }
                }
                items(texts.notes) { note ->
                    Spacer(Modifier.height(12.dp))
                    Note(note)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** What each photo of the check counted: one row for each, with a bar as long as its share of the largest count. */
@Composable
private fun PhotosCard(entry: PuzzleHistoryEntry, numbers: NumberFormat, widest: Int) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            val photoCount = entry.photos.size.takeIf { it > 0 } ?: PuzzleViewModel.pilesFor(entry.target)
            Text(
                text = pluralStringResource(R.plurals.history_detail_photos, photoCount, photoCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            if (entry.photos.isEmpty()) {
                // saved before the app kept the numbers of the photos
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.history_detail_photos_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                entry.photos.forEachIndexed { i, photo ->
                    Spacer(Modifier.height(if (i == 0) 16.dp else 12.dp))
                    PhotoRow(number = i + 1, photo = photo, share = photo.pieces / widest.toFloat(), numbers = numbers)
                }
            }
        }
    }
}

private val BadgeSize = 32.dp

@Composable
private fun PhotoRow(number: Int, photo: PhotoCount, share: Float, numbers: NumberFormat) {
    val colors = MaterialTheme.colorScheme
    val barColor = if (photo.warned) colors.tertiary else colors.primary

    val photoLabel = stringResource(R.string.history_detail_photo_number, number)
    val piecesText = pluralStringResource(R.plurals.pieces_count, photo.pieces, photo.pieces)
    val estimated = if (photo.clusters > 0) pluralStringResource(R.plurals.review_estimated_places, photo.clusters, photo.clusters) else null
    val warned = if (photo.warned) stringResource(R.string.history_detail_photo_warned) else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // read as one: "Photo 2, 198 pieces, ..."
            .clearAndSetSemantics { contentDescription = listOfNotNull(photoLabel, piecesText, estimated, warned).joinToString(", ") }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.widthIn(min = BadgeSize).height(BadgeSize).clip(CircleShape).background(colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(colors.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(share.coerceIn(0.03f, 1f))
                        .clip(RoundedCornerShape(5.dp))
                        .background(barColor)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = numbers.format(photo.pieces),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 48.dp)
            )
        }
        if (estimated != null || warned != null) {
            Column(modifier = Modifier.padding(start = BadgeSize + 12.dp, top = 2.dp)) {
                if (estimated != null) {
                    Text(text = estimated, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
                if (warned != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.tertiary)
                        Spacer(Modifier.width(4.dp))
                        Text(text = warned, style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
                    }
                }
            }
        }
    }
}
