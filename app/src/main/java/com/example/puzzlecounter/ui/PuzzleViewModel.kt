package com.example.puzzlecounter.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.puzzlecounter.analyzer.DetectionResult
import com.example.puzzlecounter.analyzer.ImageQualityChecker
import com.example.puzzlecounter.analyzer.PuzzleModel
import com.example.puzzlecounter.analyzer.QualityResult
import com.example.puzzlecounter.analyzer.TFLitePieceDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ceil

class PuzzleViewModel(application: Application) : AndroidViewModel(application) {
    private val historyManager = HistoryManager(application)
    private val debugManager = DebugManager(application)
    private val settingsManager = SettingsManager(application)

    // The TFLite model is not thread-safe: it is created, used and closed on this one thread only.
    private val modelExecutor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "puzzle-model") }
    private val modelDispatcher = modelExecutor.asCoroutineDispatcher()
    private var model: PuzzleModel? = null // only touched on modelDispatcher

    private val _uiState = MutableStateFlow(newSessionState())
    val uiState: StateFlow<PuzzleUiState> = _uiState.asStateFlow()

    /** A state without any scan progress; settings, history and debug sessions are loaded from storage. */
    private fun newSessionState() = PuzzleUiState(
        selectedBackgroundId = settingsManager.getBackgroundId(),
        backgroundBrightness = settingsManager.getBackgroundBrightness(),
        history = historyManager.getHistory(),
        debugSessions = debugManager.getSessions()
    )

    fun updateBackgroundId(id: Int) {
        settingsManager.setBackgroundId(id)
        _uiState.update { it.copy(selectedBackgroundId = id) }
    }

    fun updateBackgroundBrightness(brightness: Float) {
        settingsManager.setBackgroundBrightness(brightness)
        _uiState.update { it.copy(backgroundBrightness = brightness) }
    }

    /** Starts a new puzzle check; progress of a previous one is discarded. */
    fun setTargetPieceCount(count: Int) {
        val piles = ceil(count / PIECES_PER_PILE.toDouble()).toInt().coerceAtLeast(1)
        _uiState.value = newSessionState().copy(targetPieceCount = count, calculatedPiles = piles)
    }

    /**
     * Analyses the photo of the current pile. [bitmap] is the square photo of the viewfinder (see `CaptureScreen`),
     * which is exactly the format the model takes; it is recycled here.
     */
    fun onPhotoCaptured(bitmap: Bitmap) = analyzePhoto(fromGallery = false) { bitmap }

    private var pickedPhotoJob: Job? = null

    /** Starts loading the photo the user picked from the gallery; [PuzzleUiState.pickedPhoto] shows how far it is. */
    fun loadPickedPhoto(uri: Uri) {
        pickedPhotoJob?.cancel()
        _uiState.update { it.copy(pickedPhoto = PickedPhoto.Loading) }
        pickedPhotoJob = viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodePickedPhoto(getApplication(), uri) }
            _uiState.update { it.copy(pickedPhoto = if (bitmap != null) PickedPhoto.Ready(bitmap) else PickedPhoto.Failed) }
        }
    }

    /** The picked photo is not needed any more (the frame screen is left). */
    fun discardPickedPhoto() {
        pickedPhotoJob?.cancel()
        _uiState.update { it.copy(pickedPhoto = PickedPhoto.None) }
    }

    /**
     * The user moved the picked photo into the square frame: analyses the part of it that [region] describes, in
     * the same way as a photo of the camera. Photos are rarely square, but the model takes a square.
     */
    fun onPickedPhotoFramed(region: PhotoRegion) {
        val photo = (_uiState.value.pickedPhoto as? PickedPhoto.Ready)?.bitmap ?: return
        analyzePhoto(fromGallery = true) { cropSquare(photo, region, MAX_PROCESSING_DIMENSION.toInt()) }
    }

    /** Shows the analysis of the square photo that [photo] delivers (called on the model thread); recycles it. */
    private fun analyzePhoto(fromGallery: Boolean, photo: () -> Bitmap) {
        _uiState.update {
            it.copy(isAnalyzing = true, isReviewingCurrentPhoto = true, currentQualityResult = null, currentPhotoFromGallery = fromGallery)
        }

        viewModelScope.launch(modelDispatcher) {
            var bitmap: Bitmap? = null
            var scaled: Bitmap? = null
            var detection: DetectionResult? = null
            try {
                bitmap = photo()
                scaled = scaleDown(bitmap)
                val result = analyze(scaled).also { detection = it }
                val quality = ImageQualityChecker().checkQuality(scaled, result.hasHugeStacks)
                val session = debugManager.saveSession(scaled, result)
                val sessions = debugManager.getSessions()

                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        currentQualityResult = quality,
                        currentPieces = result.pieceCount,
                        currentClusters = result.clusterCount,
                        // The annotated image doubles as preview of the photo.
                        currentPhotoPath = session.annotPath,
                        debugSessions = sessions
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Analyse fehlgeschlagen", e)
                _uiState.update { it.copy(isAnalyzing = false, currentQualityResult = ANALYSIS_FAILED) }
            } finally {
                // Critical memory release
                // recycle() is safe to call twice (scaled is the original if the photo was small enough)
                scaled?.recycle()
                bitmap?.recycle()
                detection?.maskImage?.recycle()
            }
        }
    }

    /** Shrinks the camera photo to at most [MAX_PROCESSING_DIMENSION] pixels per edge. */
    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val scale = minOf(MAX_PROCESSING_DIMENSION / bitmap.width, MAX_PROCESSING_DIMENSION / bitmap.height)
        if (scale >= 1f) return bitmap
        return bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
    }

    /** Runs the detector, loading the model on first use. Must be called on [modelDispatcher]. */
    private fun analyze(bitmap: Bitmap): DetectionResult {
        val loaded = model ?: PuzzleModel.create(getApplication()).also { model = it }
        return TFLitePieceDetector(loaded).detectPieces(bitmap)
    }

    fun retakePhoto() {
        _uiState.update {
            it.copy(
                isReviewingCurrentPhoto = false,
                currentPhotoPath = null,
                currentQualityResult = null,
                currentPieces = 0,
                currentClusters = 0,
                currentPhotoFromGallery = false
            )
        }
    }

    /** Adds the pieces of the current photo to the count; after the last pile the result is saved. */
    fun confirmPhoto() {
        val state = _uiState.value
        if (state.currentPhotoPath == null) return // nothing analysed yet

        val countedPieces = state.countedPieces + state.currentPieces
        val nextPileIndex = state.currentPileIndex + 1

        _uiState.update {
            it.copy(
                countedPieces = countedPieces,
                countedClusters = state.countedClusters + state.currentClusters,
                currentPileIndex = nextPileIndex,
                isReviewingCurrentPhoto = false,
                currentPhotoPath = null,
                currentQualityResult = null,
                currentPieces = 0,
                currentClusters = 0,
                currentPhotoFromGallery = false
            )
        }

        if (nextPileIndex > state.calculatedPiles) {
            saveResultToHistory(countedPieces, state.targetPieceCount)
        }
    }

    private fun saveResultToHistory(actual: Int, target: Int) {
        historyManager.saveHistory(
            PuzzleHistoryEntry(
                name = "Puzzle $target Teile",
                date = System.currentTimeMillis(),
                target = target,
                actual = actual
            )
        )
        val history = historyManager.getHistory()
        _uiState.update { it.copy(history = history) }
    }

    fun clearDebugSessions() {
        debugManager.clearAll()
        _uiState.update { it.copy(debugSessions = emptyList()) }
    }

    fun reset() {
        _uiState.value = newSessionState()
    }

    override fun onCleared() {
        super.onCleared()
        // Close the model on its owning thread, after any running analysis has finished.
        modelExecutor.execute {
            try {
                model?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Modell konnte nicht sauber geschlossen werden", e)
            }
            model = null
        }
        modelExecutor.shutdown()
    }

    companion object {
        /** Pieces per photo: bigger puzzles are split into several piles of about this size. */
        const val PIECES_PER_PILE = 200

        /** Photos are shrunk to this edge length before the analysis. */
        private const val MAX_PROCESSING_DIMENSION = 1080f

        private const val TAG = "PuzzleViewModel"

        private val ANALYSIS_FAILED = QualityResult("Die Analyse ist fehlgeschlagen. Bitte versuche es mit einem neuen Foto noch einmal.")
    }
}
