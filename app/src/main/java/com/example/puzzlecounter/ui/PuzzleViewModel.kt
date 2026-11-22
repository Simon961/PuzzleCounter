package com.example.puzzlecounter.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.puzzlecounter.analyzer.DetectionResult
import com.example.puzzlecounter.analyzer.ImageQualityChecker
import com.example.puzzlecounter.analyzer.PhotoIssue
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The state of the app. What a check in progress consists of (the size of the puzzle, the piles counted so far) is
 * also kept in [savedState]: Android may end the process of an app that is in the background - while the user lays
 * out the pieces of the next pile, for example - and the check must then go on where it was, not start over or, worse,
 * finish with an empty state.
 *
 * The photos of a check are not kept: a photo is analysed in memory, its preview (see [ScanPhotoStore]) exists only
 * while the user looks at it, and what a check keeps are the numbers of its photos.
 *
 * The default view model factory creates the class with `(Application, SavedStateHandle)`, which is the overload
 * that [JvmOverloads] generates; tests hand in their own [ScanPhotoStore] as the third parameter.
 */
class PuzzleViewModel @JvmOverloads constructor(
    application: Application,
    private val savedState: SavedStateHandle,
    private val photoStore: ScanPhotoStore = ScanPhotoStore(application),
) : AndroidViewModel(application) {
    private val historyManager = HistoryManager(application)
    private val settingsManager = SettingsManager(application)

    // The TFLite model is not thread-safe: it is created, used and closed on this one thread only.
    private val modelExecutor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "puzzle-model") }
    private val modelDispatcher = modelExecutor.asCoroutineDispatcher()
    private var model: PuzzleModel? = null // only touched on modelDispatcher

    /**
     * Counts the checks that were discarded. An analysis that finishes after its check was discarded (see
     * [discardCheck]) must neither change the state of the next check nor leave its photo behind.
     */
    private val checkGeneration = AtomicInteger()

    private val _uiState = MutableStateFlow(restoredSessionState())
    val uiState: StateFlow<PuzzleUiState> = _uiState.asStateFlow()

    init {
        // What a process that was ended in the middle of a check left behind (no check is running yet, and the model
        // thread, on which every analysis runs, does this first).
        viewModelScope.launch(modelDispatcher) { photoStore.clear() }
    }

    /** A state without any scan progress; settings and history are loaded from storage. */
    private fun newSessionState() = PuzzleUiState(
        selectedBackgroundId = settingsManager.getBackgroundId(),
        backgroundBrightness = settingsManager.getBackgroundBrightness(),
        history = historyManager.getHistory(),
    )

    /** The state at the start: the check that was in progress when the system ended the process, if there was one. */
    private fun restoredSessionState(): PuzzleUiState {
        val target = savedState.get<Int>(KEY_TARGET)?.takeIf { it > 0 } ?: return newSessionState()
        return newSessionState().copy(
            targetPieceCount = target,
            calculatedPiles = savedState.get<Int>(KEY_PILES) ?: 1,
            currentPileIndex = savedState.get<Int>(KEY_PILE) ?: 1,
            photos = decodePhotoCounts(savedState.get<String>(KEY_PHOTOS)).orEmpty(),
        )
    }

    /** Keeps the check in progress where the system can give it back after it ended the process. */
    private fun keepCheck(state: PuzzleUiState) {
        savedState[KEY_TARGET] = state.targetPieceCount
        savedState[KEY_PILES] = state.calculatedPiles
        savedState[KEY_PILE] = state.currentPileIndex
        savedState[KEY_PHOTOS] = state.photos.encode()
    }

    fun updateBackgroundId(id: Int) {
        settingsManager.setBackgroundId(id)
        _uiState.update { it.copy(selectedBackgroundId = id) }
    }

    fun updateBackgroundBrightness(brightness: Float) {
        settingsManager.setBackgroundBrightness(brightness)
        _uiState.update { it.copy(backgroundBrightness = brightness) }
    }

    /**
     * Loads the model in the background, so that the first photo does not have to wait for it (loading takes seconds,
     * see [PuzzleModel.create]). A model that cannot be loaded is reported when a photo is analysed.
     */
    fun preloadModel() {
        viewModelScope.launch(modelDispatcher) {
            try {
                loadedModel()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Modell konnte nicht vorab geladen werden", e)
            }
        }
    }

    /** Starts a new puzzle check; progress of a previous one (and its photos) is discarded. */
    fun setTargetPieceCount(count: Int) {
        discardCheck()
        _uiState.value = newSessionState().copy(targetPieceCount = count, calculatedPiles = pilesFor(count)).also(::keepCheck)
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
        analyzePhoto(fromGallery = true) { modelSize -> cropSquare(photo, region, modelSize) }
    }

    /**
     * Shows the analysis of the square photo that [photo] delivers; recycles it. [photo] runs on the model thread and
     * is told the edge length of the model input, which is the size the photo is wanted in.
     */
    private fun analyzePhoto(fromGallery: Boolean, photo: (modelSize: Int) -> Bitmap) {
        val generation = checkGeneration.get()
        // A photo that is still under review is replaced by this one (a photo from the gallery is chosen again): its
        // preview is not needed any more, and its numbers must not be confirmed with the new photo.
        photoStore.delete(_uiState.value.currentPhotoPath)
        _uiState.update {
            it.copy(
                isAnalyzing = true,
                isReviewingCurrentPhoto = true,
                currentQualityResult = null,
                currentPhotoPath = null,
                currentPieces = 0,
                currentClusters = 0,
                currentPhotoFromGallery = fromGallery
            )
        }

        viewModelScope.launch(modelDispatcher) {
            var bitmap: Bitmap? = null
            var scaled: Bitmap? = null
            try {
                val loaded = loadedModel()
                bitmap = photo(loaded.inputSize)
                scaled = scaleDown(bitmap, loaded.inputSize)
                val result = detect(scaled)
                val quality = ImageQualityChecker().checkQuality(scaled, result.hasHugeStacks, result.typicalPieceEdgeShare)
                // The annotated photo doubles as preview of the photo.
                val previewPath = photoStore.save(scaled, result)

                val applied = applyAnalysis(generation) {
                    it.copy(
                        isAnalyzing = false,
                        currentQualityResult = quality,
                        currentPieces = result.pieceCount,
                        currentClusters = result.clusterCount,
                        currentPhotoPath = previewPath
                    )
                }
                if (!applied) photoStore.delete(previewPath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ModelUnavailable) {
                Log.e(TAG, "Das Modell konnte nicht geladen werden", e)
                applyAnalysis(generation) { it.copy(isAnalyzing = false, currentQualityResult = MODEL_UNAVAILABLE) }
            } catch (e: Exception) {
                Log.e(TAG, "Analyse fehlgeschlagen", e)
                applyAnalysis(generation) { it.copy(isAnalyzing = false, currentQualityResult = ANALYSIS_FAILED) }
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Zu wenig Speicher für die Analyse", e)
                applyAnalysis(generation) { it.copy(isAnalyzing = false, currentQualityResult = OUT_OF_MEMORY) }
            } finally {
                // Critical memory release
                // recycle() is safe to call twice (scaled is the original if the photo was small enough)
                scaled?.recycle()
                bitmap?.recycle()
            }
        }
    }

    /**
     * Changes the state to what the analysis of a photo came to - unless the check the photo belongs to was discarded
     * meanwhile, whose state is gone. Returns whether the state was changed.
     */
    private fun applyAnalysis(generation: Int, change: (PuzzleUiState) -> PuzzleUiState): Boolean {
        var applied = false
        _uiState.update {
            applied = generation == checkGeneration.get()
            if (applied) change(it) else it
        }
        return applied
    }

    /**
     * Shrinks the photo to the model input of [size] pixels per edge in one step, so that the detector does not have
     * to resample it again (a photo that is not larger is kept as it is).
     */
    private fun scaleDown(bitmap: Bitmap, size: Int): Bitmap {
        val scale = minOf(size.toFloat() / bitmap.width, size.toFloat() / bitmap.height)
        if (scale >= 1f) return bitmap
        return bitmap.scale((bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt())
    }

    /** The model, loaded on first use. Must be called on [modelDispatcher]. */
    private fun loadedModel(): PuzzleModel = model ?: createModel(allowGpu = true)

    private fun createModel(allowGpu: Boolean): PuzzleModel = try {
        PuzzleModel.create(getApplication(), allowGpu).also { model = it }
    } catch (e: Exception) {
        throw ModelUnavailable(e)
    }

    /**
     * Counts the pieces in [photo]. If the model fails while it runs - a GPU can get lost while the app rests in the
     * background - it is loaded again, this time for the CPU that cannot get lost, and the photo is analysed once more.
     */
    private fun detect(photo: Bitmap): DetectionResult {
        val current = loadedModel()
        return try {
            TFLitePieceDetector(current).detectPieces(photo)
        } catch (e: Exception) {
            Log.w(TAG, "Erkennung fehlgeschlagen, das Modell wird für die CPU neu geladen", e)
            model = null
            runCatching { current.close() }
            TFLitePieceDetector(createModel(allowGpu = false)).detectPieces(photo)
        }
    }

    /** The photo under review is dropped (to take or choose another one): its preview is deleted. */
    fun retakePhoto() {
        photoStore.delete(_uiState.value.currentPhotoPath)
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

    /**
     * Adds the pieces of the current photo to the count and deletes its preview (the numbers are all a check keeps of
     * a photo); after the last pile the result is saved.
     */
    fun confirmPhoto() {
        val state = _uiState.value
        val previewPath = state.currentPhotoPath ?: return // nothing analysed yet
        photoStore.delete(previewPath)

        val photos = state.photos + PhotoCount(
            pieces = state.currentPieces,
            clusters = state.currentClusters,
            // "continue anyway" on a photo that the analysis advised against
            warned = state.currentQualityResult?.isAcceptable == false
        )
        val nextPileIndex = state.currentPileIndex + 1

        _uiState.update {
            it.copy(
                photos = photos,
                currentPileIndex = nextPileIndex,
                isReviewingCurrentPhoto = false,
                currentPhotoPath = null,
                currentQualityResult = null,
                currentPieces = 0,
                currentClusters = 0,
                currentPhotoFromGallery = false
            )
        }
        keepCheck(_uiState.value)

        if (nextPileIndex > state.calculatedPiles) {
            saveResultToHistory(photos, state.targetPieceCount)
        }
    }

    private fun saveResultToHistory(photos: List<PhotoCount>, target: Int) {
        historyManager.saveHistory(
            PuzzleHistoryEntry(
                // Not shown: the title of an entry is made from its target in the language of the app.
                name = "Puzzle $target",
                date = System.currentTimeMillis(),
                target = target,
                actual = photos.sumOf { it.pieces },
                photos = photos
            )
        )
        val history = historyManager.getHistory()
        _uiState.update { it.copy(history = history) }
    }

    /**
     * Ends the check, which the user does by closing the result (or by going back from it): the progress is
     * discarded together with every photo of it, and the history keeps the counts.
     */
    fun reset() {
        discardCheck()
        _uiState.value = newSessionState()
        savedState.keys().toList().forEach { savedState.remove<Any>(it) }
    }

    /** Deletes every photo of the check and makes analyses that are still running leave the next check alone. */
    private fun discardCheck() {
        checkGeneration.incrementAndGet()
        photoStore.clear()
    }

    override fun onCleared() {
        // Close the model on its owning thread, after any running analysis has finished.
        modelExecutor.execute {
            photoStore.clear() // the app is closed: no check goes on
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

        /** The piles (photos) a puzzle of [pieces] pieces is counted in; the same for a check that is made now and one in the history. */
        fun pilesFor(pieces: Int): Int = ceil(pieces / PIECES_PER_PILE.toDouble()).toInt().coerceAtLeast(1)

        private const val TAG = "PuzzleViewModel"

        // Keys of the check in progress in the saved state.
        private const val KEY_TARGET = "target"
        private const val KEY_PILES = "piles"
        private const val KEY_PILE = "pile"
        private const val KEY_PHOTOS = "photos"

        private val ANALYSIS_FAILED = QualityResult(PhotoIssue.ANALYSIS_FAILED)

        /** The model itself cannot run: retaking the photo does not help, so the text names what might. */
        private val MODEL_UNAVAILABLE = QualityResult(PhotoIssue.MODEL_UNAVAILABLE)

        private val OUT_OF_MEMORY = QualityResult(PhotoIssue.OUT_OF_MEMORY)
    }

    /** The model could not be loaded (as opposed to a photo that could not be analysed). */
    private class ModelUnavailable(cause: Throwable) : Exception(cause)
}
