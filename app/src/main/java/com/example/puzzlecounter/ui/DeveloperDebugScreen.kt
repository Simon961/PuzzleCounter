package com.example.puzzlecounter.ui

import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.puzzlecounter.R
import java.io.File

private enum class ImageMode(@StringRes val labelRes: Int) {
    CONTOURS(R.string.inspector_mode_contours),
    MASK(R.string.inspector_mode_mask),
    ORIGINAL(R.string.inspector_mode_original),
}

/** Developer inspector: browse the last scans as contour overlay, mask or original photo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperDebugScreen(viewModel: PuzzleViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val sessions = uiState.debugSessions

    var selectedId by remember { mutableStateOf<String?>(null) }
    var askToDelete by remember { mutableStateOf(false) }
    var imageMode by remember { mutableStateOf(ImageMode.CONTOURS) }
    val selected = sessions.firstOrNull { it.id == selectedId } ?: sessions.firstOrNull()

    if (askToDelete) {
        AlertDialog(
            onDismissRequest = { askToDelete = false },
            title = { Text(stringResource(R.string.inspector_delete_title)) },
            text = { Text(stringResource(R.string.inspector_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askToDelete = false
                    viewModel.clearDebugSessions()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { askToDelete = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.developer_inspector), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { askToDelete = true }, enabled = sessions.isNotEmpty()) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.inspector_delete_all), tint = MaterialTheme.colorScheme.error.copy(alpha = if (sessions.isNotEmpty()) 1f else 0.4f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (selected == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.inspector_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                SingleChoiceSegmentedButtonRow {
                    ImageMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = imageMode == mode,
                            onClick = { imageMode = mode },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ImageMode.entries.size)
                        ) {
                            Text(stringResource(mode.labelRes))
                        }
                    }
                }
            }

            ZoomableImage(
                path = selected.imagePath(imageMode),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.inspector_measurements), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.inspector_pieces, selected.pieceCount), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.inspector_overlap_places, selected.clusterCount), color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            SessionThumbnailsRow(
                sessions = sessions,
                selected = selected,
                onSessionSelect = { selectedId = it.id }
            )
        }
    }
}

/** The image of [mode], falling back to the contour overlay and then to the original if a file is missing. */
private fun DebugScanSession.imagePath(mode: ImageMode): String? {
    val preferred = when (mode) {
        ImageMode.CONTOURS -> annotPath
        ImageMode.MASK -> maskPath
        ImageMode.ORIGINAL -> rawPath
    }
    return listOf(preferred, annotPath, rawPath).firstOrNull { it.isNotEmpty() && File(it).exists() }
}

/** Shows the image at [path] with pinch-to-zoom and pan; zoom resets whenever another image is shown. */
@Composable
private fun ZoomableImage(path: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(path) { path?.let { BitmapFactory.decodeFile(it) }?.asImageBitmap() }
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .pointerInput(path) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 10f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.inspector_saved_scan),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.BrokenImage,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.inspector_image_failed), color = Color.White)
            }
        }
    }
}

@Composable
private fun SessionThumbnailsRow(
    sessions: List<DebugScanSession>,
    selected: DebugScanSession,
    onSessionSelect: (DebugScanSession) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(sessions, key = { it.id }) { session ->
            // Thumbnails are only 60dp: decode them at a quarter of the resolution.
            val thumb = remember(session.rawPath) {
                BitmapFactory.decodeFile(session.rawPath, BitmapFactory.Options().apply { inSampleSize = 4 })
            }
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        2.dp,
                        if (session.id == selected.id) MaterialTheme.colorScheme.primary else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSessionSelect(session) }
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = stringResource(R.string.inspector_select_scan),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}
