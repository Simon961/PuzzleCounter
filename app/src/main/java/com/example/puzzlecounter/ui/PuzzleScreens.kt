package com.example.puzzlecounter.ui

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.puzzlecounter.R
import kotlin.math.ceil
import java.text.DateFormat
import java.util.Date

@Composable
fun AppBackground(uiState: PuzzleUiState, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = uiState.selectedBackgroundId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (1.0f - uiState.backgroundBrightness) * 0.94f))
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(uiState: PuzzleUiState, onStartClicked: () -> Unit, onEntryClicked: (PuzzleHistoryEntry) -> Unit, onSettingsClicked: () -> Unit) {
    val haptic = LocalHapticFeedback.current

    AppBackground(uiState = uiState) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSettingsClicked()
                        }) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartClicked()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.start_new_check), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = stringResource(R.string.start_recent_checks),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(16.dp))

                if (uiState.history.isNotEmpty()) {
                    // Dates in the language of the app (which may differ from the language of the system).
                    val locale = LocalConfiguration.current.locales[0]
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.history) { entry ->
                            // the same judgement as on the result screen, so that the history does not contradict it
                            val outcome = remember(entry.target, entry.actual) { historyOutcome(entry.target, entry.actual) }
                            // A tap opens everything the check found out (see HistoryDetailScreen).
                            ElevatedCard(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onEntryClicked(entry)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // The texts take what is left: long words (Russian, French) must not squeeze the symbol.
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            pluralStringResource(R.plurals.history_entry_title, entry.target, entry.target),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.history_target_counted, entry.target, entry.actual),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = remember(entry.date, locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(entry.date)) },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = when (outcome) {
                                            Outcome.COMPLETE -> MaterialTheme.colorScheme.secondaryContainer
                                            Outcome.EXACT_VARIANT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else -> MaterialTheme.colorScheme.errorContainer
                                        },
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (outcome) {
                                                Outcome.COMPLETE -> Icons.Rounded.Check
                                                Outcome.EXACT_VARIANT -> Icons.Rounded.Info
                                                else -> Icons.Rounded.Warning
                                            },
                                            contentDescription = null,
                                            tint = when (outcome) {
                                                Outcome.COMPLETE -> MaterialTheme.colorScheme.secondary
                                                Outcome.EXACT_VARIANT -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.error
                                            },
                                            modifier = Modifier.padding(12.dp).size(24.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.padding(start = 4.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.start_no_checks), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** The piece counts that puzzles are usually sold with. */
private val standardSizes = listOf(200, 500, 1000, 1500, 2000, 5000)

/** Five digits are more than any puzzle has, and keep the number far away from overflowing. */
private const val MAX_PIECE_COUNT_DIGITS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionScreen(uiState: PuzzleUiState, onNextClicked: (Int) -> Unit) {
    var customCount by rememberSaveable { mutableStateOf("") }
    var selectedSize by rememberSaveable { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val count = selectedSize ?: customCount.toIntOrNull()?.takeIf { it > 0 }

    fun next() {
        focusManager.clearFocus()
        keyboardController?.hide()
        if (count != null) onNextClicked(count)
    }

    AppBackground(uiState = uiState) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.selection_title), fontWeight = FontWeight.Bold) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)) },
            containerColor = Color.Transparent,
            modifier = Modifier.imePadding().clickable { focusManager.clearFocus() }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().height(296.dp),
                    userScrollEnabled = false
                ) {
                    items(standardSizes) { size ->
                        val isSelected = selectedSize == size
                        OutlinedCard(
                            modifier = Modifier
                                .height(88.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedSize = size
                                    customCount = ""
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            ),
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = size.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = pluralStringResource(R.plurals.pieces_word, size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = customCount,
                    onValueChange = { input ->
                        if (input.length <= MAX_PIECE_COUNT_DIGITS && input.all { it.isDigit() }) {
                            customCount = input
                            selectedSize = null
                        }
                    },
                    label = { Text(stringResource(R.string.selection_custom_label)) },
                    supportingText = { Text(stringResource(R.string.selection_custom_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { next() }),
                    singleLine = true,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        next()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(bottom = 8.dp),
                    shape = RoundedCornerShape(50),
                    enabled = count != null
                ) {
                    Text(stringResource(R.string.action_next), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionScreen(uiState: PuzzleUiState, onStartScan: () -> Unit, onPickPhoto: (Uri) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val openPhotoPicker = rememberPhotoPicker(onPickPhoto)

    AppBackground(uiState = uiState) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.instruction_title), fontWeight = FontWeight.Bold) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)) },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // The instructions are taller than a small screen, a landscape screen or a large font allow:
                // they scroll, while the buttons below stay where they can be reached.
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.instruction_preparation), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(16.dp))

                            if (uiState.calculatedPiles <= 1) {
                                Text(
                                    text = stringResource(R.string.instruction_single_pile),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                val perPile = ceil(uiState.targetPieceCount / uiState.calculatedPiles.toDouble()).toInt()
                                Text(
                                    text = pluralStringResource(R.plurals.instruction_several_piles, uiState.calculatedPiles, uiState.calculatedPiles, perPile),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))

                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.instruction_important),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.instruction_square_frame),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartScan()
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.instruction_start_scan), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    UploadPhotoButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            openPhotoPicker()
                        }
                    )
                }
            }
        }
    }
}
