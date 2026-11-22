package com.example.puzzlecounter.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.puzzlecounter.R
import com.example.puzzlecounter.analyzer.PhotoIssue
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private const val TAG = "CaptureScreen"

/**
 * Takes the photo of one pile and lets the user check the analysis.
 *
 * The viewfinder is a **square** - exactly the image the model analyses (it takes a square input). A CameraX
 * [ViewPort] makes the preview and the photo show the very same area on every phone, so what the user sees in the
 * viewfinder is what gets counted: nothing is cut off or padded afterwards.
 */
@Composable
fun CaptureScreen(
    uiState: PuzzleUiState,
    onPhotoCaptured: (Bitmap) -> Unit,
    onPickPhoto: (Uri) -> Unit,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current

    var hasCameraPermission by remember { mutableStateOf(isCameraPermissionGranted(context)) }
    // Denied for good: Android shows no dialog any more, the permission can only be given in the settings of the app.
    var needsSettings by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasCameraPermission = isGranted
        val activity = context.findActivity()
        needsSettings = !isGranted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }

    // The permission may have been given in the settings while the app was away.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = isCameraPermissionGranted(context)
                if (hasCameraPermission) needsSettings = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Only the live view needs the camera. The question is asked once, when the screen is opened to scan; it is not
    // asked again whenever the review of a photo ends - a photo from the gallery is reviewed and confirmed without the
    // camera, and after the last photo the check is over. Without the permission the screen offers it with a button.
    val checkFinished = uiState.isFinished
    var askedForCamera by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (shouldAskForCamera(hasCameraPermission, uiState.isReviewingCurrentPhoto, checkFinished, askedForCamera)) {
            askedForCamera = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    when {
        // The last photo was confirmed and the result is on its way: neither the camera nor its permission is wanted.
        checkFinished -> Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        // Also while analyzing: the photo path is only known once the analysis has finished.
        uiState.isReviewingCurrentPhoto -> PhotoReview(uiState, onRetake, onConfirm, onPickPhoto)
        !hasCameraPermission -> CameraPermissionRequest(
            uiState,
            needsSettings = needsSettings,
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            onPickPhoto = onPickPhoto
        )
        else -> LiveCamera(uiState, onPhotoCaptured, onPickPhoto)
    }
}

/**
 * Whether the screen asks for the camera permission by itself: when it is opened to scan (no photo under review, the
 * check not over) and the permission is missing - and only the first time, so that a user who said no and went on with
 * photos from the gallery is not asked again.
 */
internal fun shouldAskForCamera(hasPermission: Boolean, isReviewing: Boolean, checkFinished: Boolean, alreadyAsked: Boolean) =
    !hasPermission && !isReviewing && !checkFinished && !alreadyAsked

private fun isCameraPermissionGranted(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun CameraPermissionRequest(
    uiState: PuzzleUiState,
    needsSettings: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickPhoto: (Uri) -> Unit,
) {
    val openPhotoPicker = rememberPhotoPicker(onPickPhoto)
    AppBackground(uiState = uiState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.NoPhotography, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(if (needsSettings) R.string.camera_permission_denied else R.string.camera_permission_needed),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = if (needsSettings) onOpenSettings else onRequest, shape = RoundedCornerShape(50)) {
                Text(stringResource(if (needsSettings) R.string.camera_permission_open_settings else R.string.camera_permission_grant))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = openPhotoPicker, shape = RoundedCornerShape(50)) {
                Icon(Icons.Rounded.FileUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.upload_photo_instead))
            }
        }
    }
}

/**
 * The analysed photo with the result of the quality check and the buttons to confirm it or to take (or, for a photo
 * from the gallery, pick) another one.
 */
@Composable
private fun PhotoReview(uiState: PuzzleUiState, onRetake: () -> Unit, onConfirm: () -> Unit, onPickPhoto: (Uri) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val openPhotoPicker = rememberPhotoPicker(onPickPhoto)
    val fromGallery = uiState.currentPhotoFromGallery
    val another = { if (fromGallery) openPhotoPicker() else onRetake() }

    // Back leads to the live camera again (not out of the check), but not while the photo is still analysed.
    BackHandler { if (!uiState.isAnalyzing) onRetake() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val bitmap = remember(uiState.currentPhotoPath) {
            uiState.currentPhotoPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.review_photo_description),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Analysing State overlay
        AnimatedVisibility(
            visible = uiState.isAnalyzing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        if (uiState.calculatedPiles > 1) {
                            stringResource(R.string.analyzing_pile, uiState.currentPileIndex, uiState.calculatedPiles)
                        } else {
                            stringResource(R.string.analyzing_photo)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Results State overlay
        if (!uiState.isAnalyzing) {
            val quality = uiState.currentQualityResult
            if (quality != null) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    if (quality.isAcceptable) {
                        Surface(
                            modifier = Modifier.statusBarsPadding().padding(top = 16.dp, start = 16.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(20.dp),
                            tonalElevation = 8.dp
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = pluralStringResource(R.plurals.pieces_count, uiState.currentPieces, uiState.currentPieces),
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (uiState.currentClusters > 0) {
                                        Text(
                                            text = pluralStringResource(R.plurals.review_estimated_places, uiState.currentClusters, uiState.currentClusters),
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.statusBarsPadding().padding(top = 16.dp, start = 16.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(20.dp),
                            tonalElevation = 8.dp
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Error, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = stringResource((quality.issue ?: PhotoIssue.ANALYSIS_FAILED).messageRes),
                                    color = MaterialTheme.colorScheme.onError,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }
                }

                // Action Buttons Bottom
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier.navigationBarsPadding().padding(bottom = 24.dp, start = 24.dp, end = 24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (quality.isAcceptable) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onConfirm()
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_confirm_continue), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    another()
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                            ) {
                                Icon(if (fromGallery) Icons.Rounded.FileUpload else Icons.Rounded.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(if (fromGallery) R.string.action_choose_other_photo else R.string.action_retake))
                            }
                        } else {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    another()
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(if (fromGallery) Icons.Rounded.FileUpload else Icons.Rounded.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(if (fromGallery) R.string.action_choose_other_photo_recommended else R.string.action_retake_recommended),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onError
                                )
                            }
                            if (uiState.currentPhotoPath != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onConfirm()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.action_continue_anyway), color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCamera(uiState: PuzzleUiState, onPhotoCaptured: (Bitmap) -> Unit, onPickPhoto: (Uri) -> Unit) {
    val context = LocalContext.current
    val openPhotoPicker = rememberPhotoPicker(onPickPhoto)
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    val previewView = remember { newPreviewView(context) }
    val imageCapture = remember { newImageCapture() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraFailed by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isFlashing by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var captureFailed by remember { mutableStateOf(false) }
    LaunchedEffect(captureFailed) {
        if (captureFailed) {
            delay(4.seconds)
            captureFailed = false
        }
    }

    // The photo is cut out and rotated off the main thread.
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(captureExecutor) { onDispose { captureExecutor.shutdown() } }

    // The camera only runs while the live view is shown: leaving it releases the camera and switches the torch off.
    DisposableEffect(previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                provider = providerFuture.get()
                camera = bindSquareCamera(provider!!, lifecycleOwner, previewView, imageCapture)
            } catch (e: Exception) {
                Log.e(TAG, "Kamera konnte nicht gestartet werden", e)
                cameraFailed = true
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbindAll()
        }
    }

    val shutterEnabled = camera != null && !isCapturing && !uiState.isAnalyzing

    fun takePhoto() {
        isCapturing = true
        isFlashing = true
        imageCapture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val photo = try {
                        image.toSquareBitmap()
                    } catch (e: Exception) {
                        Log.e(TAG, "Foto konnte nicht verarbeitet werden", e)
                        null
                    } catch (e: OutOfMemoryError) {
                        Log.e(TAG, "Zu wenig Speicher für das Foto", e)
                        null
                    } finally {
                        image.close()
                    }
                    isFlashing = false
                    if (photo != null) onPhotoCaptured(photo) else captureFailed = true
                    isCapturing = false
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Aufnahme fehlgeschlagen", exception)
                    isFlashing = false
                    captureFailed = true
                    isCapturing = false
                }
            },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // The viewfinder: exactly the area that gets analysed.
        Box(modifier = Modifier.align(Alignment.Center).size(minOf(maxWidth, maxHeight)).clipToBounds()) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Vignette Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                radius = size.maxDimension * 1.4f,
                            ),
                        )
                    },
            )

            // Grid and Target
            val bracketColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cw = size.width
                val ch = size.height
                val color = Color.White.copy(alpha = 0.2f)
                drawLine(color, start = Offset(cw / 3, 0f), end = Offset(cw / 3, ch), strokeWidth = 2f)
                drawLine(color, start = Offset(cw * 2 / 3, 0f), end = Offset(cw * 2 / 3, ch), strokeWidth = 2f)
                drawLine(color, start = Offset(0f, ch / 3), end = Offset(cw, ch / 3), strokeWidth = 2f)
                drawLine(color, start = Offset(0f, ch * 2 / 3), end = Offset(cw, ch * 2 / 3), strokeWidth = 2f)

                // Corner Brackets
                val bSize = 60f
                val bThick = 8f
                val pad = 40f
                // Top Left
                drawLine(bracketColor, start = Offset(pad, pad), end = Offset(pad + bSize, pad), strokeWidth = bThick, cap = Stroke.DefaultCap)
                drawLine(bracketColor, start = Offset(pad, pad), end = Offset(pad, pad + bSize), strokeWidth = bThick, cap = Stroke.DefaultCap)
                // Top Right
                drawLine(bracketColor, start = Offset(cw - pad - bSize, pad), end = Offset(cw - pad, pad), strokeWidth = bThick)
                drawLine(bracketColor, start = Offset(cw - pad, pad), end = Offset(cw - pad, pad + bSize), strokeWidth = bThick)
                // Bottom Left
                drawLine(bracketColor, start = Offset(pad, ch - pad), end = Offset(pad + bSize, ch - pad), strokeWidth = bThick)
                drawLine(bracketColor, start = Offset(pad, ch - pad - bSize), end = Offset(pad, ch - pad), strokeWidth = bThick)
                // Bottom Right
                drawLine(bracketColor, start = Offset(cw - pad - bSize, ch - pad), end = Offset(cw - pad, ch - pad), strokeWidth = bThick)
                drawLine(bracketColor, start = Offset(cw - pad, ch - pad - bSize), end = Offset(cw - pad, ch - pad), strokeWidth = bThick)
            }

            if (cameraFailed) {
                Text(
                    text = stringResource(R.string.camera_failed),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            } else if (captureFailed) {
                Text(
                    text = stringResource(R.string.capture_failed),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(16.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = isFlashing,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(400)),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White))
        }

        // Top Info HUD
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(top = 16.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (uiState.calculatedPiles > 1) {
                        stringResource(R.string.capture_pile_progress, uiState.currentPileIndex, uiState.calculatedPiles)
                    } else {
                        stringResource(R.string.capture_single_photo)
                    },
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            // Not every device has a torch.
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                Spacer(Modifier.width(16.dp))
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isTorchOn = !isTorchOn
                        camera?.cameraControl?.enableTorch(isTorchOn)
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                        contentDescription = stringResource(if (isTorchOn) R.string.torch_off else R.string.torch_on),
                        tint = Color.White,
                    )
                }
            }
        }

        // Shutter Button (only once the camera is bound; one photo at a time)
        val takePhotoDescription = stringResource(R.string.take_photo)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp)
                .size(80.dp)
                .border(4.dp, Color.White, CircleShape)
                .padding(8.dp)
                .background(Color.White, CircleShape)
                .alpha(if (shutterEnabled) 1f else 0.4f)
                .semantics { contentDescription = takePhotoDescription }
                .clickable(enabled = shutterEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    takePhoto()
                },
        )

        // A photo from the gallery instead of the camera (for the next piles as well)
        UploadPhotoButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                openPhotoPicker()
            },
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 40.dp, bottom = 52.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
        )
    }
}

/**
 * The viewfinder. COMPATIBLE (a TextureView) instead of the default SurfaceView on purpose: the camera image is
 * larger than the square viewport, and only the TextureView is clipped to the square - the SurfaceView would show
 * the rest of the image above and below it, which the photo does not contain.
 */
private fun newPreviewView(context: Context) = PreviewView(context).apply {
    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    scaleType = PreviewView.ScaleType.FILL_CENTER
}

/**
 * Photos only need to be as sharp as the analysis: the largest supported 4:3 size up to 2048 x 1536 (~3 MP) is plenty
 * for the 1024 px working image and keeps the memory use small on every phone (some sensors deliver 50-200 MP).
 * Only if a phone offers nothing that small, the next larger size is used.
 */
private fun newImageCapture() = ImageCapture.Builder()
    .setResolutionSelector(
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(2048, 1536), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
            .build(),
    )
    .build()

/**
 * Binds preview and photo to the lifecycle. The shared square [ViewPort] makes CameraX cut both to the same centered
 * square of the sensor image, whatever aspect ratios the phone's sensor and screen have.
 */
private fun bindSquareCamera(
    provider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    imageCapture: ImageCapture,
): Camera {
    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
    val viewPort = ViewPort.Builder(Rational(1, 1), previewView.display?.rotation ?: Surface.ROTATION_0).build()
    val useCases = UseCaseGroup.Builder()
        .setViewPort(viewPort)
        .addUseCase(preview)
        .addUseCase(imageCapture)
        .build()
    // Almost every phone has a back camera, but not every tablet or notebook: any camera is better than none.
    val selector = listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)
        .firstOrNull { provider.hasCamera(it) }
        ?: throw IllegalStateException("Dieses Gerät hat keine Kamera")
    provider.unbindAll()
    return provider.bindToLifecycle(lifecycleOwner, selector, useCases)
}
