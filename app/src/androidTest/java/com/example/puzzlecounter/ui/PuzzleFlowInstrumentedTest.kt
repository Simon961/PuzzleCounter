package com.example.puzzlecounter.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.core.graphics.createBitmap
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
            assertNotNull("Analyse fehlgeschlagen: ${analyzed.currentQualityResult?.warningMessage}", photoPath)
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
            assertNotNull("Analyse fehlgeschlagen: ${first.currentQualityResult?.warningMessage}", first.currentPhotoPath)
            viewModel.confirmPhoto()
            val afterFirst = viewModel.uiState.value
            assertEquals(2, afterFirst.currentPileIndex)
            assertEquals(first.currentPieces, afterFirst.countedPieces)
            assertEquals("Vor dem letzten Haufen wird nichts gespeichert", historyBefore, HistoryManager(app).getHistory())

            viewModel.onPhotoCaptured(TestImages.fakePuzzlePhoto(pieceCount = 16))
            val second = awaitState(viewModel) { !it.isAnalyzing }
            assertNotNull("Analyse fehlgeschlagen: ${second.currentQualityResult?.warningMessage}", second.currentPhotoPath)
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
     */
    @Test
    fun photoFromTheGallery_isPlacedInTheFrameAndCountedLikeACameraPhoto() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val snapshot = AppDataSnapshot(app)
        val store = ViewModelStore()
        val widePhoto = File(app.cacheDir, "gallery_wide.png")
        try {
            val pile = InstrumentationRegistry.getInstrumentation().context.assets.open("real_piles/pile_4.jpg").use { BitmapFactory.decodeStream(it)!! }
            val model = PuzzleModel.create(app)
            val expected = try {
                TFLitePieceDetector(model).detectPieces(pile).let { it.pieceCount.also { _ -> it.maskImage.recycle() } }
            } finally {
                model.close()
            }

            // The pile (square) in the middle of a 4:3 photo whose sides show nothing
            val wide = createBitmap(1440, 1080).also {
                Canvas(it).apply { drawColor(Color.rgb(60, 50, 40)); drawBitmap(pile, 180f, 0f, null) }
            }
            widePhoto.outputStream().use { wide.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val viewModel = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[PuzzleViewModel::class.java]
            viewModel.setTargetPieceCount(200)

            viewModel.loadPickedPhoto(Uri.fromFile(widePhoto))
            val loaded = awaitState(viewModel) { it.pickedPhoto !is PickedPhoto.Loading }.pickedPhoto
            assertTrue("Foto muss geladen sein: $loaded", loaded is PickedPhoto.Ready)
            val photo = (loaded as PickedPhoto.Ready).bitmap
            assertEquals(1440, photo.width)
            assertEquals(1080, photo.height)

            // Nothing moved: the frame shows the centered square, which is exactly the pile
            val region = PhotoFrame(photo.width, photo.height).regionAt(PhotoPlacement())
            assertEquals(PhotoRegion(left = 180f, top = 0f, side = 1080f), region)
            viewModel.onPickedPhotoFramed(region)
            val analyzed = awaitState(viewModel) { it.isReviewingCurrentPhoto && !it.isAnalyzing }
            assertNotNull("Analyse fehlgeschlagen: ${analyzed.currentQualityResult?.warningMessage}", analyzed.currentPhotoPath)
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
