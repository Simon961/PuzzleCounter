package com.example.puzzlecounter.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.puzzlecounter.R
import kotlin.math.roundToInt

/** A background image of the app; [id] is the stable name under which the choice is saved. */
data class AppBackgroundItem(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val drawableRes: Int,
)

val availableBackgrounds = listOf(
    AppBackgroundItem("bg_default", R.string.background_cyber_indigo, R.drawable.app_background),
    AppBackgroundItem("bg_geom", R.string.background_tech_symmetry, R.drawable.app_background_1),
    AppBackgroundItem("bg_poly", R.string.background_obsidian_edges, R.drawable.app_background_2),
    AppBackgroundItem("bg_board", R.string.background_dice_board, R.drawable.app_background_3)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: PuzzleUiState,
    onBack: () -> Unit,
    onNavigateToBackground: () -> Unit,
    onNavigateToInfo: () -> Unit,
) {
    var chooseLanguage by rememberSaveable { mutableStateOf(false) }
    val currentLanguage = remember { AppLanguage.current() }
    if (chooseLanguage) {
        LanguageDialog(
            current = currentLanguage,
            onChoose = { language ->
                chooseLanguage = false
                AppLanguage.choose(language)
            },
            onDismiss = { chooseLanguage = false }
        )
    }

    AppBackground(uiState = uiState) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                Spacer(Modifier.height(16.dp))

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column {
                        ListItem(
                            onClick = onNavigateToBackground,
                            supportingContent = { Text(stringResource(R.string.settings_background_summary)) },
                            leadingContent = {
                                Icon(Icons.Rounded.Wallpaper, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        ) {
                            Text(stringResource(R.string.settings_background_title), fontWeight = FontWeight.Bold)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        ListItem(
                            onClick = { chooseLanguage = true },
                            supportingContent = { Text(stringResource(currentLanguage.nameRes)) },
                            leadingContent = {
                                Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        ) {
                            Text(stringResource(R.string.settings_language_title), fontWeight = FontWeight.Bold)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        ListItem(
                            onClick = onNavigateToInfo,
                            supportingContent = { Text(stringResource(R.string.settings_info_summary)) },
                            leadingContent = {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        ) {
                            Text(stringResource(R.string.settings_info_title), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundSettingsScreen(uiState: PuzzleUiState, viewModel: PuzzleViewModel, onBack: () -> Unit) {
    AppBackground(uiState = uiState) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.background_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(16.dp))

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            stringResource(R.string.background_appearance),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.height(24.dp))

                        val brightnessPercent = (uiState.backgroundBrightness * 100).roundToInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.background_brightness), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.background_brightness_value, brightnessPercent), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // The slider with a callback is controlled: it only reports the drag (onValueChange) and shows what
                        // its state says, so the state has to follow the value of the app, or the thumb would not move.
                        val brightnessState = rememberSliderState(value = uiState.backgroundBrightness, trackRange = 0.1f..1.0f)
                        brightnessState.value = uiState.backgroundBrightness
                        Slider(
                            state = brightnessState,
                            onValueChange = { viewModel.updateBackgroundBrightness(it) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(32.dp))

                        Text(stringResource(R.string.background_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(16.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(availableBackgrounds) { item ->
                                val isSelected = uiState.selectedBackgroundId == item.drawableRes
                                Box(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { viewModel.updateBackgroundId(item.drawableRes) }
                                ) {
                                    Image(
                                        painter = painterResource(id = item.drawableRes),
                                        contentDescription = stringResource(item.titleRes),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .border(3.dp, Color.White, RoundedCornerShape(16.dp))
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = stringResource(R.string.background_selected),
                                            tint = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(24.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The choice of the language. Every language is named in itself; choosing one shows the app in it at once
 * (see [AppLanguage.choose]).
 */
@Composable
private fun LanguageDialog(current: AppLanguage, onChoose: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                for (language in AppLanguage.entries) {
                    val selected = language == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(selected = selected, onClick = { onChoose(language) }, role = Role.RadioButton)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(language.nameRes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
