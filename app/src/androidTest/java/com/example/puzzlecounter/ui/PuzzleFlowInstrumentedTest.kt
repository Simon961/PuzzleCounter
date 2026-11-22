package com.example.puzzlecounter.ui

import android.annotation.SuppressLint
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
import com.example.puzzlecounter.analyzer.DetectionResult
import com.example.puzzlecounter.analyzer.PuzzleModel
import com.example.puzzlecounter.analyzer.TFLitePieceDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * The whole app flow from input to output on a real device, driven through the [PuzzleViewModel]:
 * target size -> photo -> analysis (real model) -> confirm -> result in the history.
 *
 * The persisted data of the app (history, settings) is restored afterwards, so running this on a device that is
 * used for real does not touch its data. The photos of a check exist only while it runs: what is on the device
 * of them is looked at as well.
 */
@RunWith(AndroidJUnit4::class)
class PuzzleFlowInstrumentedTest {

    @Test
    fun photoToResult_runsThroughTheWholeFlow() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val snapshot = AppDataSnapshot(app)
        val store = ViewModelStore()
        try {
            val viewModel = viewModelWith(SavedStateHandle(), store, app)
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
            assertEquals("Die Vorschau ist das einzige Foto auf dem Gerät", listOf(File(photoPath).name), previewFiles(app))

            // Confirm the only pile: the puzzle is complete and the result is saved.
            viewModel.confirmPhoto()
            val result = viewModel.uiState.value
            assertFalse(result.isReviewingCurrentPhoto)
            assertEquals(2, result.currentPileIndex)
            assertEquals(analyzed.currentPieces, result.countedPieces)
            val saved = result.history.first()
            assertEquals(10, saved.target)
            assertEquals(result.countedPieces, saved.actual)
            assertEquals("Der Eintrag behält die Zahlen des Fotos", listOf(analyzed.asConfirmed()), saved.photos)
            assertEquals("Mit dem Bestätigen ist das Foto vom Gerät gelöscht", emptyList<String>(), previewFiles(app))
            assertEquals("Der Eintrag muss dauerhaft gespeichert sein", saved, HistoryManager(app).getHistory().first())
            assertEquals("Genau ein neuer Eintrag pro Prüfung (die Historie hält maximal 15)", minOf(snapshot.historyEntries + 1, 15), result.history.size)

            // Output -> new puzzle
            viewModel.reset()
            assertEquals(0, viewModel.uiState.value.targetPieceCount)
            assertEquals("Die Zahlen bleiben, wenn die Prüfung beendet ist", result.history, viewModel.uiState.value.history)
            assertEquals(emptyList<String>(), previewFiles(app))
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
            val viewModel = viewModelWith(SavedStateHandle(), store, app)
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
            assertEquals("Der Verlauf kennt die Zahl jedes Fotos", listOf(first.currentPieces, second.currentPieces), result.history.first().photos.map { it.pieces })
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
            val viewModel = viewModelWith(SavedStateHandle(), store, app)
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
            val viewModel = viewModelWith(SavedStateHandle(), store, app)
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
            assertEquals("auch die Zahlen der einzelnen Fotos kommen zurück", before.photos, restored.photos)
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

    private fun viewModelWith(
        saved: SavedStateHandle,
        store: ViewModelStore,
        app: Application,
        photos: ScanPhotoStore = ScanPhotoStore(app),
    ): PuzzleViewModel =
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PuzzleViewModel(app, saved, photos) as T
        })[PuzzleViewModel::class.java]

    /**
     * What confirming the photo under review adds to the check. The photo that the tests draw is not a real pile, the
     * analysis advises against it, and confirming it anyway is "continue anyway": the photo is marked as warned.
     */
    private fun PuzzleUiState.asConfirmed() =
        PhotoCount(currentPieces, currentClusters, warned = currentQualityResult?.isAcceptable == false)

    /** The photos of checks that are on the device: the previews, by file name. */
    private fun previewFiles(app: Application): List<String> =
        File(app.cacheDir, ScanPhotoStore.DIR_NAME).list().orEmpty().sorted()

    /**
     * A photo is on the device only while the user looks at its analysis: it is gone when it is retaken, replaced by
     * another one (a photo from the gallery that is chosen again) or confirmed - its numbers are what the check keeps.
     */
    @Test
    fun aPhoto_existsOnlyWhileItIsUnderReview() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val snapshot = AppDataSnapshot(app)
        val store = ViewModelStore()
        try {
            val viewModel = viewModelWith(SavedStateHandle(), store, app)
            viewModel.setTargetPieceCount(300)

            viewModel.onPhotoCaptured(TestImages.fakePuzzlePhoto(pieceCount = 8))
            val first = awaitState(viewModel) { !it.isAnalyzing }
            assertNotNull("Analyse fehlgeschlagen: ${first.currentQualityResult?.issue}", first.currentPhotoPath)
            assertEquals(1, previewFiles(app).size)

            // retaken: the photo is gone, and nothing of it can be confirmed
            viewModel.retakePhoto()
            assertEquals(emptyList<String>(), previewFiles(app))
            assertNull(viewModel.uiState.value.currentPhotoPath)

            // replaced by another one before it was confirmed: the first is gone at once, the second is the only one left
            viewModel.onPhotoCaptured(TestImages.fakePuzzlePhoto(pieceCount = 8))
            val second = awaitState(viewModel) { !it.isAnalyzing }
            val secondPath = second.currentPhotoPath
            assertNotNull("Analyse fehlgeschlagen: ${second.currentQualityResult?.issue}", secondPath)
            viewModel.onPhotoCaptured(TestImages.fakePuzzlePhoto(pieceCount = 12))
            val replacing = viewModel.uiState.value
            assertTrue(replacing.isAnalyzing)
            assertNull("Die Zahlen des alten Fotos gehören nicht zum neuen", replacing.currentPhotoPath)
            assertEquals(0, replacing.currentPieces)
            val third = awaitState(viewModel) { !it.isAnalyzing }
            assertNotNull("Analyse fehlgeschlagen: ${third.currentQualityResult?.issue}", third.currentPhotoPath)
            assertFalse("Das ersetzte Foto ist gelöscht", File(secondPath!!).exists())
            assertEquals(listOf(File(third.currentPhotoPath!!).name), previewFiles(app))

            // confirmed: the preview is gone, the numbers stay
            viewModel.confirmPhoto()
            assertEquals(emptyList<String>(), previewFiles(app))
            assertEquals(listOf(third.asConfirmed()), viewModel.uiState.value.photos)
        } finally {
            store.clear()
            snapshot.restore()
        }
    }

    /** A check that is left - by starting another one, or by closing its result - takes every photo of it with it. */
    @Test
    fun endingACheck_deletesItsPhotos_butNotItsCounts() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val snapshot = AppDataSnapshot(app)
        val store = ViewModelStore()
        val dir = File(app.cacheDir, ScanPhotoStore.DIR_NAME)
        try {
            val viewModel = viewModelWith(SavedStateHandle(), store, app)

            // a new check while a photo of the old one is still there
            viewModel.setTargetPieceCount(300)
            viewModel.onPhotoCaptured(TestImages.fakePuzzlePhoto(pieceCount = 8))
            awaitState(viewModel) { !it.isAnalyzing && it.currentPhotoPath != null }
            assertEquals(1, previewFiles(app).size)
            viewModel.setTargetPieceCount(10)
            assertEquals(emptyList<String>(), previewFiles(app))
            assertEquals(emptyList<PhotoCount>(), viewModel.uiState.value.photos)
            assertEquals(1, viewModel.uiState.value.currentPileIndex)

            // a finished check, and whatever is left on the device when its result is closed
            viewModel.onPhotoCaptured(TestImages.fakePuzzlePhoto(pieceCount = 8))
            val analyzed = awaitState(viewModel) { !it.isAnalyzing && it.currentPhotoPath != null }
            viewModel.confirmPhoto()
            val entry = viewModel.uiState.value.history.first()
            val leftover = File(dir, "preview_leftover.jpg").also { it.writeText("photo") }
            viewModel.reset()
            assertFalse(leftover.exists())
            assertEquals(emptyList<String>(), previewFiles(app))
            assertEquals("Der Eintrag bleibt", entry, viewModel.uiState.value.history.first())
            assertEquals(listOf(analyzed.asConfirmed()), entry.photos)
        } finally {
            store.clear()
            snapshot.restore()
        }
    }

    /**
     * A process that was ended in the middle of a check leaves its photo behind: the next start deletes it.
     */
    @Test
    fun whatAnEndedProcessLeftBehind_isDeletedAtTheNextStart() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val snapshot = AppDataSnapshot(app)
        val store = ViewModelStore()
        val dir = File(app.cacheDir, ScanPhotoStore.DIR_NAME).apply { mkdirs() }
        val orphan = File(dir, "preview_orphan.jpg").also { it.writeText("photo") }
        try {
            assertTrue(orphan.exists())
            viewModelWith(SavedStateHandle(), store, app)
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (SystemClock.elapsedRealtime() < deadline && orphan.exists()) Thread.sleep(50)
            assertFalse("Das Foto des beendeten Prozesses", orphan.exists())
        } finally {
            orphan.delete()
            store.clear()
            snapshot.restore()
        }
    }

    /**
     * The analysis of a photo runs on its own thread. If the check is discarded meanwhile, what it finds must neither
     * show up in the state of the next check nor stay on the device as a photo of nobody. The analysis is held up
     * right before its photo is saved, so that the order is certain.
     */
    @Test
    fun anAnalysisThatFinishesAfterItsCheckWasDiscarded_changesNothingAndLeavesNoPhoto() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val snapshot = AppDataSnapshot(app)
        val store = ViewModelStore()
        val reached = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val deleted = CountDownLatch(1)
        val savedPath = AtomicReference<String?>(null)
        val photos = object : ScanPhotoStore(app) {
            override fun save(photo: Bitmap, result: DetectionResult): String {
                reached.countDown()
                proceed.await(60, TimeUnit.SECONDS)
                return super.save(photo, result).also { savedPath.set(it) }
            }

            override fun delete(path: String?) {
                super.delete(path)
                if (path != null && path == savedPath.get()) deleted.countDown()
            }
        }
        try {
            val viewModel = viewModelWith(SavedStateHandle(), store, app, photos)
            viewModel.setTargetPieceCount(10)
            viewModel.onPhotoCaptured(TestImages.fakePuzzlePhoto())
            assertTrue("Die Analyse muss bis zum Speichern kommen", reached.await(120, TimeUnit.SECONDS))

            viewModel.reset() // the check is over before its photo is saved
            val afterReset = viewModel.uiState.value
            proceed.countDown()

            assertTrue("Das Foto der beendeten Prüfung muss gelöscht werden", deleted.await(60, TimeUnit.SECONDS))
            assertFalse(File(savedPath.get()!!).exists())
            assertEquals("Der Zustand der nächsten Prüfung bleibt unberührt", afterReset, viewModel.uiState.value)
            assertEquals(emptyList<String>(), previewFiles(app))
        } finally {
            proceed.countDown()
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
            val viewModel = viewModelWith(SavedStateHandle(), store, app)
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
        TFLitePieceDetector(model).detectPieces(photo).pieceCount

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
        // null for a file that does not exist (yet): it is deleted again, not left behind empty
        private val prefs = PREF_NAMES.associateWith { name ->
            if (prefsFile(name).exists()) context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toMap() else null
        }

        val historyEntries: Int = HistoryManager(context).getHistory().size

        private fun prefsFile(name: String) = File(context.dataDir, "shared_prefs/$name.xml")

        // commit() on purpose, see the comments below: the data must be back (or deleted) when the test ends
        @SuppressLint("ApplySharedPref")
        fun restore() {
            ScanPhotoStore(context).clear()
            for ((name, values) in prefs) {
                if (values == null) {
                    // commit() comes after every write that is still on its way (apply() writes later, on another
                    // thread): only then is the file deleted for good, and no entry of the test is written back
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                    context.deleteSharedPreferences(name)
                    continue
                }
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
        }

        private companion object {
            val PREF_NAMES = listOf("puzzle_history", "puzzle_settings")
        }
    }
}
