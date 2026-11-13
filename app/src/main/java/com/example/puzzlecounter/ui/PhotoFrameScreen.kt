package com.example.puzzlecounter.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.puzzlecounter.analyzer.PuzzleModel
import kotlin.math.roundToInt

/**
 * Lets the user place a photo from the gallery in a square frame, because the model needs a square image and
 * photos rarely are one: the photo is moved with one finger and zoomed with two until all pieces lie in the
 * square, which is exactly the part that gets analysed ([onConfirm] gets it as a [PhotoRegion]). Zoomed out, the
 * whole photo fits and the rest of the square stays empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoFrameScreen(
    uiState: PuzzleUiState,
    onConfirm: (PhotoRegion) -> Unit,
    onBack: () -> Unit,
    onDiscard: () -> Unit,
) {
    // However the screen is left, the picked photo is not needed afterwards.
    DisposableEffect(Unit) { onDispose(onDiscard) }

    AppBackground(uiState = uiState) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Foto ausrichten", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            val content = Modifier.fillMaxSize().padding(padding).padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
            when (val photo = uiState.pickedPhoto) {
                is PickedPhoto.Ready -> FrameEditor(photo.bitmap, onConfirm, content)
                PickedPhoto.Failed -> Column(
                    modifier = content,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Das Foto konnte nicht geöffnet werden.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onBack, shape = RoundedCornerShape(50)) { Text("Zurück") }
                }
                PickedPhoto.Loading -> Column(
                    modifier = content,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
                PickedPhoto.None -> Unit
            }
        }
    }
}

/** The placement survives a rotation of the phone: it is measured in frame sides, not in pixels. */
private val PlacementSaver = listSaver<PhotoPlacement, Float>(
    save = { listOf(it.zoom, it.offsetX, it.offsetY) },
    restore = { PhotoPlacement(it[0], it[1], it[2]) }
)

@Composable
private fun FrameEditor(photo: Bitmap, onConfirm: (PhotoRegion) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val frame = remember(photo) { PhotoFrame(photo.width, photo.height) }
    val image = remember(photo) { photo.asImageBitmap() }
    var placement by rememberSaveable(stateSaver = PlacementSaver) { mutableStateOf(PhotoPlacement()) }
    var confirmed by remember { mutableStateOf(false) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Verschiebe das Foto und zoome mit zwei Fingern, bis alle Puzzleteile im Quadrat liegen. " +
                    "Gezählt wird nur, was im Quadrat zu sehen ist.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        Spacer(Modifier.height(16.dp))

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            Canvas(
                modifier = Modifier
                    .size(side)
                    .clipToBounds()
                    .semantics { contentDescription = "Quadrat: Foto verschieben und zoomen" }
                    .pointerInput(frame) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val sidePx = size.width.toFloat()
                            placement = frame.moved(
                                from = placement,
                                panX = pan.x / sidePx,
                                panY = pan.y / sidePx,
                                zoomChange = zoom,
                                pivotX = (centroid.x - sidePx / 2f) / sidePx,
                                pivotY = (centroid.y - sidePx / 2f) / sidePx,
                            )
                        }
                    }
            ) {
                // Draws what is cut out afterwards: the region of the photo that the frame shows.
                val region = frame.regionAt(placement)
                val pixelsPerPhotoPixel = size.width / region.side
                drawRect(Color(PuzzleModel.LETTERBOX_COLOR))
                drawImage(
                    image = image,
                    dstOffset = IntOffset((-region.left * pixelsPerPhotoPixel).roundToInt(), (-region.top * pixelsPerPhotoPixel).roundToInt()),
                    dstSize = IntSize((image.width * pixelsPerPhotoPixel).roundToInt(), (image.height * pixelsPerPhotoPixel).roundToInt()),
                    filterQuality = FilterQuality.Medium
                )

                // The 1:1 grid
                val gridColor = Color.White.copy(alpha = 0.45f)
                val lineWidth = 1.dp.toPx()
                for (i in 1..2) {
                    val at = size.width * i / 3f
                    drawLine(gridColor, Offset(at, 0f), Offset(at, size.height), strokeWidth = lineWidth)
                    drawLine(gridColor, Offset(0f, at), Offset(size.width, at), strokeWidth = lineWidth)
                }
                val border = 2.dp.toPx()
                drawRect(
                    Color.White.copy(alpha = 0.9f),
                    topLeft = Offset(border / 2f, border / 2f),
                    size = Size(size.width - border, size.height - border),
                    style = Stroke(width = border)
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                confirmed = true
                onConfirm(frame.regionAt(placement))
            },
            enabled = !confirmed,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(50)
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Bestätigen", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
