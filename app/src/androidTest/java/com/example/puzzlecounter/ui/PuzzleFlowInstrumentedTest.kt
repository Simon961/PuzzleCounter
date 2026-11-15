package com.example.puzzlecounter.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.puzzlecounter.TestImages
import com.example.puzzlecounter.analyzer.PuzzleModel
import com.example.puzzlecounter.analyzer.TFLitePieceDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * The whole app flow from input to output on a real device, driven through the [PuzzleViewModel]:
 * target size -> photo -> analysis (real model) -> confirm -> result in the history.
 *
 * The persisted data of the app (history, settings, debug scans) is restored afterwards,
 * so running this on a device that is used for real does not touch its data.
 */
@RunWith(AndroidJUnit4::class)
class PuzzleFlowInstrumentedTest {

    @Test
    fun photoToResult_runsThroughTheWholeFlow() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val snapshot = AppDataSnapshot(app)
        val store = ViewModelStore()
        try {
            val viewModel = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[PuzzleViewModel::class.java]
            viewModel.preloadModel() // what the app does at its start; the photo below waits for the model

            // Input: puzzle size. 10 pieces fit in a single pile.
            viewModel.setTargetPieceCount(10)
            assertEquals(1, viewModel.uiState.value.calculatedPiles)
            assertEquals(1, viewModel.uiState.value.currentPileIndex)

            // Input: photo -> analysis with the real model
            viewModel.onPhotoCaptured(TestImages.fakePuzzlePhoto())
            val analyzed = awaitState(viewModel) { !it.isAnalyzing }
            assertTrue(analyzed.isReviewingCurrentPhoto)
            assertNotNull("Die Analyse muss ein Qualitätsergebnis liefern", analyzed.currentQualityResult)
            val photoPath = analyzed.currentPhotoPath
            assertNotNull("Analyse fehlgeschlagen: ${analyzed.currentQualityResult?.issue}", photoPath)
            assertTrue("Das annotierte Vorschaubild muss existieren", File(photoPath!!).exists())
            assertEquals("Die Vorschau gehört zur neuesten Debug-Session", photoPath, analyzed.debugSessions.first().annotPath)

            // Confirm the only pile: the puzzle is complete and the result is saved.
            viewModel.confirmPhoto()
            val result = viewModel.uiState.value
            assertFalse(result.isReviewingCurrentPhoto)
            assertEquals(2, result.currentPileIndex)
            assertEquals(analyzed.currentPieces, result.countedPieces)
            val saved = result.history.first()
            assertEquals(10, saved.target)
            assertEquals(result.countedPieces, saved.actual)
            assertEquals("Der Eintrag muss dauerhaft gespeichert sein", saved, HistoryManager(app).getHistory().first())
            assertEquals("Genau ein neuer Eintrag pro Prüfung (die Historie hält maximal 15)", minOf(snapshot.historyEntries + 1, 15), result.history.size)

            // Output -> new puzzle
            viewModel.reset()
            assertEquals(0, viewModel.uiState.value.targetPieceCount)
            assertEquals(result.history, viewModel.uiState.value.history)
        } finally {
            store.clear()
            snapshot.restore()
        }
    }

    @Test
    fun puzzleOfTwoPiles_addsUpBothPhotosAndSavesOnlyAtTheEnd() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val snapshot = AppDataSnapshot(app)
        val store = ViewModelStore()
        try {
            val viewModel = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[PuzzleViewModel::class.java]
            val historyBefore = HistoryManager(app).getHistory()

            viewModel.setTargetPieceCount(300)
            assertEquals(2, viewModel.uiState.value.calculatedPiles)

            viewModel.onPhotoCaptured(TestImages.fakePuzzlePhoto(pieceCount = 8))
            val first = awaitState(viewModel) { !it.isAnalyzing }
            assertNotNull("Analyse fehlgeschlagen: ${first.currentQualityResult?.issue}", first.currentPhotoPath)
            viewModel.confirmPhoto()
            val afterFirst = viewModel.uiState.value
            assertEquals(2, afterFirst.currentPileIndex)
            assertEquals(first.currentPieces, afterFirst.countedPieces)
            assertEquals("Vor dem letzten Haufen wird nichts gespeichert", historyBefore, HistoryManager(app).getHistory())

            viewModel.onPhotoCaptured(TestImages.fakePuzzlePhoto(pieceCount = 16))
            val second = awaitState(viewModel) { !it.isAnalyzing }
            assertNotNull("Analyse fehlgeschlagen: ${second.currentQualityResult?.issue}", second.currentPhotoPath)
            viewModel.confirmPhoto()
            val result = viewModel.uiState.value
            assertEquals(3, result.currentPileIndex)
            assertEquals(first.currentPieces + second.currentPieces, result.countedPieces)
            assertEquals(300, result.history.first().target)
            assertEquals(result.countedPieces, result.history.first().actual)
            assertEquals(minOf(historyBefore.size + 1, 15), result.history.size)
        } finally {
            store.clear()
            snapshot.restore()
        }
    }

    /**
     * A wide photo from the gallery: moved into the square frame, it must be counted exactly like the same pixels
     * as a square photo of the camera (the model takes a square, so the user places the photo in a frame first).
     * The pile has exactly the size of the model input, so no resampling can make the two differ.
     */
    @Test
    fun photoFromTheGallery_isPlacedInTheFrameAndCountedLikeACameraPhoto() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val snapshot = AppDataSnapshot(app)
        val store = ViewModelStore()
        val widePhoto = File(app.cacheDir, "gallery_wide.png")
        try {
            val model = PuzzleModel.create(app)
            val size = model.inputSize
            val pile = pile4().scale(size, size)
            val expected = try {
                piecesIn(model, pile)
            } finally {
                model.close()
            }

            // The pile (square) in the middle of a wide photo whose sides show nothing; the sides are an even
            // number of pixels wide, so that the centered square starts on a whole pixel
            val side = size / 3 / 2 * 2
            val wide = createBitmap(size + side, size).also {
                Canvas(it).apply { drawColor(Color.rgb(60, 50, 40)); drawBitmap(pile, side / 2f, 0f, null) }
            }
            val viewModel = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[PuzzleViewModel::class.java]
            viewModel.setTargetPieceCount(200)

            val photo = pickFromGallery(viewModel, widePhoto, wide)
            assertEquals(size + side, photo.width)
            assertEquals(size, photo.height)

            // Nothing moved: the frame shows the centered square, which is exactly the pile
            val region = PhotoFrame(photo.width, photo.height).regionAt(PhotoPlacement())
            assertEquals(PhotoRegion(left = side / 2f, top = 0f, side = size.toFloat()), region)
            viewModel.onPickedPhotoFramed(region)
            val analyzed = awaitState(viewModel) { it.isReviewingCurrentPhoto && !it.isAnalyzing }
            assertNotNull("Analyse fehlgeschlagen: ${analyzed.currentQualityResult?.issue}", analyzed.currentPhotoPath)
            assertEquals("Hochgeladenes Foto zählt wie das gleiche Quadrat der Kamera", expected, analyzed.currentPieces)

            // The photo is released when the frame screen is left; the analysis keeps going without it
            viewModel.discardPickedPhoto()
            assertEquals(PickedPhoto.None, viewModel.uiState.value.pickedPhoto)
            viewModel.confirmPhoto()
            assertEquals(expected, viewModel.uiState.value.countedPieces)
        } finally {
            widePhoto.delete()
            store.clear()
            snapshot.restore()
        }
    }

    /**
     * A gallery photo whose framed part is larger than the model input (the usual case: photos are much bigger):
     * the part is shrunk to the model input, and the count stays what it is for the pile itself.
     */
    @Test
    fun largePhotoFromTheGallery_isShrunkToTheModelInputAndCountedAsBefore() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val snapshot = AppDataSnapshot(app)
        val store = ViewModelStore()
        val bigPhoto = File(app.cacheDir, "gallery_big.png")
        try {
            val pile = pile4() // 1080 x 1080 px, the model input is smaller
            val expected = PuzzleModel.create(app).let { model ->
                try {
                    piecesIn(model, pile)
                } finally {
                    model.close()
                }
            }

            val big = createBitmap(pile.width * 4 / 3, pile.height).also {
                Canvas(it).apply { drawColor(Color.rgb(60, 50, 40)); drawBitmap(pile, (it.width - pile.width) / 2f, 0f, null) }
            }
            val viewModel = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[PuzzleViewModel::class.java]
            viewModel.setTargetPieceCount(200)

            val photo = pickFromGallery(viewModel, bigPhoto, big)
            val region = PhotoFrame(photo.width, photo.height).regionAt(PhotoPlacement())
            assertEquals(PhotoRegion(left = (big.width - pile.width) / 2f, top = 0f, side = pile.height.toFloat()), region)
            viewModel.onPickedPhotoFramed(region)
            val analyzed = awaitState(viewModel) { it.isReviewingCurrentPhoto && !it.isAnalyzing }
            assertNotNull("Analyse fehlgeschlagen: ${analyzed.currentQualityResult?.issue}", analyzed.currentPhotoPath)
            // Shrunk by the app or by the detector: the pixels differ a little, the count by one or two pieces at most
            assertTrue("${analyzed.currentPieces} Teile statt $expected", abs(analyzed.currentPieces - expected) <= 2)
        } finally {
            bigPhoto.delete()
            store.clear()
            snapshot.restore()
        }
    }

    /**
     * Android may end the process of an app that rests in the background - while the user lays out the next pile.
     * The check must then go on where it was (the saved state is what the system gives back to the new process),
     * not start over and not finish with an empty state ("Soll: 0").
     */
    @Test
    fun aCheckInProgress_goesOnAfterTheSystemEndedTheProcess() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val snapshot = AppDataSnapshot(app)
        val saved = SavedStateHandle()
        val firstProcess = ViewModelStore()
        val secondProcess = ViewModelStore()
        val thirdProcess = ViewModelStore()
        try {
            val first = viewModelWith(saved, firstProcess, app)
            assertEquals("ohne Prüfung beginnt die App leer", 0, first.uiState.value.targetPieceCount)
            first.setTargetPieceCount(600)
            first.onPhotoCaptured(TestImages.fakePuzzlePhoto(pieceCount = 8))
            val analyzed = awaitState(first) { !it.isAnalyzing }
            assertNotNull("Analyse fehlgeschlagen: ${analyzed.currentQualityResult?.issue}", analyzed.currentPhotoPath)
            first.confirmPhoto()
            val before = first.uiState.value
            assertEquals(2, before.currentPileIndex)
            firstProcess.clear() // the system ends the process ...

            // ... and the new process continues with the second of three piles
            val second = viewModelWith(saved, secondProcess, app)
            val restored = second.uiState.value
            assertEquals(600, restored.targetPieceCount)
            assertEquals(3, restored.calculatedPiles)
            assertEquals(2, restored.currentPileIndex)
            assertEquals(before.countedPieces, restored.countedPieces)
            assertEquals(before.countedClusters, restored.countedClusters)
            assertFalse("das Foto, das gerade ausgewertet wurde, ist weg: die Kamera beginnt neu", restored.isReviewingCurrentPhoto)

            // a new check starts empty again; what was left over is not brought back later
            second.reset()
            secondProcess.clear()
            assertEquals(0, viewModelWith(saved, thirdProcess, app).uiState.value.targetPieceCount)
        } finally {
            firstProcess.clear()
            secondProcess.clear()
            thirdProcess.clear()
            snapshot.restore()
        }
    }

    private fun viewModelWith(saved: SavedStateHandle, store: ViewModelStore, app: Application): PuzzleViewModel =
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PuzzleViewModel(app, saved) as T
        })[PuzzleViewModel::class.java]

    @Test
    fun galleryFileThatIsNoPhoto_isReportedAsFailed() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = ViewModelStore()
        val text = File(app.cacheDir, "gallery_text.txt").also { it.writeText("kein Foto") }
        try {
            val viewModel = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[PuzzleViewModel::class.java]
            viewModel.loadPickedPhoto(Uri.fromFile(text))
            assertEquals(PickedPhoto.Failed, awaitState(viewModel) { it.pickedPhoto !is PickedPhoto.Loading }.pickedPhoto)
            viewModel.discardPickedPhoto()
            assertEquals(PickedPhoto.None, viewModel.uiState.value.pickedPhoto)
        } finally {
            text.delete()
            store.clear()
        }
    }

    /** Pile 4 of the real photos (1080 x 1080 px). */
    private fun pile4(): Bitmap =
        InstrumentationRegistry.getInstrumentation().context.assets.open("real_piles/pile_4.jpg").use { BitmapFactory.decodeStream(it)!! }

    /** The pieces the detector counts in [photo], without anything of the app around it. */
    private fun piecesIn(model: PuzzleModel, photo: Bitmap): Int =
        TFLitePieceDetector(model).detectPieces(photo).let { it.pieceCount.also { _ -> it.maskImage.recycle() } }

    /** Saves [photo] as a file, picks it like a photo from the gallery and returns the bitmap the app got. */
    private fun pickFromGallery(viewModel: PuzzleViewModel, file: File, photo: Bitmap): Bitmap {
        file.outputStream().use { photo.compress(Bitmap.CompressFormat.PNG, 100, it) }
        viewModel.loadPickedPhoto(Uri.fromFile(file))
        val loaded = awaitState(viewModel) { it.pickedPhoto !is PickedPhoto.Loading }.pickedPhoto
        assertTrue("Foto muss geladen sein: $loaded", loaded is PickedPhoto.Ready)
        return (loaded as PickedPhoto.Ready).bitmap
    }

    private fun awaitState(viewModel: PuzzleViewModel, timeoutMs: Long = 120_000, predicate: (PuzzleUiState) -> Boolean): PuzzleUiState {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val state = viewModel.uiState.value
            if (predicate(state)) return state
            Thread.sleep(50)
        }
        throw AssertionError("Zeitüberschreitung beim Warten auf die Analyse")
    }

    /** Remembers the persisted app data and puts it back after the test. */
    private class AppDataSnapshot(private val context: Context) {
        private val prefs = PREF_NAMES.associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        private val debugDir = File(context.filesDir, "debug_scans")
        private val debugFiles = debugDir.list()?.toSet().orEmpty()

        val historyEntries: Int = HistoryManager(context).getHistory().size

        fun restore() {
            for ((name, values) in prefs) {
                val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                for ((key, value) in values) {
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                    }
                }
                editor.commit()
            }
            debugDir.listFiles()?.filter { it.name !in debugFiles }?.forEach { it.delete() }
        }

        private companion object {
            val PREF_NAMES = listOf("puzzle_history", "debug_manager_prefs", "puzzle_settings")
        }
    }
}
