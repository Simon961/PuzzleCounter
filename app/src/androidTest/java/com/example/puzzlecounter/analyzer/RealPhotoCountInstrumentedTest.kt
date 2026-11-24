package com.example.puzzlecounter.analyzer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The whole detection (real model, real device) on four photos of a pile of about 200 puzzle pieces, taken with the
 * app (assets/real_piles, 1080 x 1080 px, shrunk to the 1024 px of the model): they pin the piece counting down to
 * numbers that were checked by eye.
 *
 * How the numbers were checked: every blob of the photos that the counting rules treat as a cluster was looked at
 * (each holds two or more pieces), and in two dense regions of pile 4 every piece was counted by hand. The model
 * finds 25 of the 26 pieces of region A and 10 of the 11 to 12 of region B, and the area of the cluster of four
 * pieces lies right on the border between three and four (see [PieceCounting]); the checks of these places
 * therefore allow one piece less than the hand count. The tolerance of one or two pieces for the totals allows for
 * the small differences between devices (GPU delegate).
 */
@RunWith(AndroidJUnit4::class)
class RealPhotoCountInstrumentedTest {

    private class Expected(val pieces: Int, val clusters: Int)

    private val expected = listOf(Expected(204, 0), Expected(205, 5), Expected(205, 4), Expected(207, 6))

    private fun load(index: Int): Bitmap =
        InstrumentationRegistry.getInstrumentation().context.assets.open("real_piles/pile_$index.jpg").use {
            BitmapFactory.decodeStream(it)!!
        }

    private inline fun <T> withDetection(index: Int, block: (Bitmap, DetectionResult) -> T): T {
        val model = PuzzleModel.create(InstrumentationRegistry.getInstrumentation().targetContext)
        val photo = load(index)
        try {
            return block(photo, TFLitePieceDetector(model).detectPieces(photo))
        } finally {
            photo.recycle()
            model.close()
        }
    }

    @Test
    fun fourPiles_areCountedAsChecked() {
        expected.forEachIndexed { i, want ->
            withDetection(i + 1) { photo, result ->
                val name = "Haufen ${i + 1}"
                assertTrue("$name: ${result.pieceCount} Teile statt ${want.pieces}", abs(result.pieceCount - want.pieces) <= 2)
                assertTrue("$name: ${result.clusterCount} Gruppen statt ${want.clusters}", abs(result.clusterCount - want.clusters) <= 1)
                assertFalse("$name: normale Haufen sind keine Stapel", result.hasHugeStacks)
                val checker = ImageQualityChecker()
                val quality = checker.checkQuality(photo, result.hasHugeStacks)
                assertTrue("$name: scharfes, gut belichtetes Foto muss akzeptiert werden: ${quality.issue}", quality.isAcceptable)

                // the way the app asks: with the pieces' boxes. These piles cover a quarter of the photo and more, so
                // they are measured as a whole, to the last digit as without the boxes, and accepted
                val boxes = result.pieces.map { it.rect }
                val asApp = checker.checkQuality(photo, result.hasHugeStacks, result.typicalPieceEdgeShare, boxes)
                assertTrue("$name: die App-Prüfung (mit Boxen) meldet ${asApp.issue}", asApp.isAcceptable)
                val withBoxes = checker.measure(photo, boxes)
                val whole = checker.measure(photo)
                assertFalse("$name: ein Haufen wird als ganzes Foto gemessen", withBoxes.measuredOnPieces)
                assertTrue("$name: Messwerte mit Boxen ${withBoxes.sharpness} statt ${whole.sharpness}", withBoxes.sharpness == whole.sharpness)
                assertTrue(withBoxes.averageLuminance == whole.averageLuminance)
            }
        }
    }

    /**
     * A few pieces on a plain table are the simplest photo there is. Pieces are cut out of pile 1, each along the
     * contour the model found, and put well apart on a white sheet and on a gray mat: the model has to count them
     * right, and the photo has to be accepted. The quality check once judged the empty table instead of the pieces and
     * called such photos overexposed or blurry, and the right count stayed hidden.
     */
    @Test
    fun fewPiecesOnAPlainTable_areCountedAndAccepted() {
        val model = PuzzleModel.create(InstrumentationRegistry.getInstrumentation().targetContext)
        val pile = load(1)
        val scenes = mutableListOf<Bitmap>()
        try {
            val detector = TFLitePieceDetector(model)
            val pieces = looselyLyingSinglePieces(detector.detectPieces(pile)).take(10)
            assertTrue("zu wenige einzelne Teile: ${pieces.size}", pieces.size == 10)
            val checker = ImageQualityChecker()
            for ((table, count) in listOf(Color.rgb(245, 245, 245) to 10, Color.rgb(120, 120, 120) to 5)) {
                val scene = piecesOnTable(pile, pieces.take(count), table).also { scenes += it }
                val result = detector.detectPieces(scene)
                val quality = checker.checkQuality(scene, result.hasHugeStacks, result.typicalPieceEdgeShare, result.pieces.map { it.rect })
                val name = "Tisch #${Integer.toHexString(table)}"
                assertTrue("$name: ${result.pieceCount} Teile statt $count", abs(result.pieceCount - count) <= 1)
                assertTrue("$name: ${result.pieceCount} Teile, aber die Prüfung meldet ${quality.issue}", quality.isAcceptable)
            }
        } finally {
            scenes.forEach { it.recycle() }
            pile.recycle()
            model.close()
        }
    }

    /** Single pieces of a typical size, the least overlapped by other blobs first (ties: top to bottom, left to right). */
    private fun looselyLyingSinglePieces(result: DetectionResult): List<DetectedPiece> {
        val typical = result.pieces.map { it.area }.sorted().let { it[it.size / 2] }
        fun overlap(piece: DetectedPiece) = result.pieces.filter { it !== piece }.sumOf { other ->
            val common = Rect(piece.rect)
            if (common.intersect(other.rect)) common.width() * common.height() else 0
        }
        return result.pieces
            .filter { it.pieceCount == 1 && it.area in (typical * 0.85f).toInt()..(typical * 1.15f).toInt() }
            .sortedWith(compareBy({ overlap(it) }, { it.rect.top }, { it.rect.left }))
    }

    /** [pieces] of [photo], cut out along their contours, in the cells of a wide grid on a table of one [table] colour. */
    private fun piecesOnTable(photo: Bitmap, pieces: List<DetectedPiece>, table: Int): Bitmap {
        val cell = 180
        assertTrue("Teile für die Zellen zu groß", pieces.all { it.rect.width() < cell - 20 && it.rect.height() < cell - 20 })
        val frame = androidx.core.graphics.createBitmap(photo.width, photo.height)
        val canvas = Canvas(frame)
        canvas.drawColor(table)
        val columns = photo.width / cell
        pieces.forEachIndexed { i, piece ->
            val dx = (i % columns) * cell + (cell - piece.rect.width()) / 2 - piece.rect.left
            val dy = (i / columns) * cell + (cell - piece.rect.height()) / 2 - piece.rect.top
            canvas.save()
            canvas.translate(dx.toFloat(), dy.toFloat())
            canvas.clipPath(piece.path)
            canvas.drawBitmap(photo, 0f, 0f, null)
            canvas.restore()
        }
        return frame
    }

    /** Counted by hand on the photo: region A holds 26 pieces (3 clusters of two among them), region B 11 to 12. */
    @Test
    fun denseRegionsOfPile4_matchTheHandCount() {
        withDetection(4) { _, result ->
            fun piecesIn(region: Rect) = result.pieces.filter { region.contains(it.rect.centerX(), it.rect.centerY()) }.sumOf { it.pieceCount }
            val regionA = piecesIn(Rect(100, 150, 520, 330))
            val regionB = piecesIn(Rect(190, 850, 420, 1040))
            assertTrue("Ausschnitt A: $regionA Teile statt 26", abs(regionA - 26) <= 1)
            assertTrue("Ausschnitt B: $regionB Teile statt 11 bis 12", regionB in 10..13)
        }
    }

    @Test
    fun clustersAreFoundWhereSeveralPiecesLieOnEachOther() {
        withDetection(4) { _, result ->
            // four overlapping pieces at the bottom left of pile 4 form one blob: counted as three or four, not as one
            val cluster = result.pieces.single { it.rect.contains(Rect(250, 920, 300, 980)) }
            assertTrue("Klumpen aus 4 Teilen zählt ${cluster.pieceCount}", cluster.pieceCount in 3..4)
        }
    }

    /**
     * The GPU (half precision) and the CPU (full precision) must count the same pile: the app runs on phones with all
     * kinds of GPUs, and the count must not depend on which one the phone has. Skipped where the phone has no usable GPU.
     */
    @Test
    fun gpuAndCpu_countTheSamePile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gpu = PuzzleModel.create(context, allowGpu = true)
        try {
            org.junit.Assume.assumeTrue("Dieses Gerät führt das Modell nicht auf der GPU aus", gpu.usesGpu)
            val cpu = PuzzleModel.create(context, allowGpu = false)
            try {
                for (index in listOf(1, 4)) {
                    val photo = load(index)
                    val onGpu = TFLitePieceDetector(gpu).detectPieces(photo).pieceCount
                    val onCpu = TFLitePieceDetector(cpu).detectPieces(photo).pieceCount
                    photo.recycle()
                    assertTrue("Haufen $index: GPU zählt $onGpu, CPU $onCpu", abs(onGpu - onCpu) <= 2)
                }
            } finally {
                cpu.close()
            }
        } finally {
            gpu.close()
        }
    }

    /** What a pile of about 200 pieces that fills the photo shows: pieces with an edge of about 4 % of the model input. */
    @Test
    fun typicalPieceSize_isMeasuredInTheModelsPixels() {
        for (index in 1..4) {
            withDetection(index) { _, result ->
                val share = result.typicalPieceEdgeShare!!
                assertTrue("Haufen $index: Teilekante ${share * 100} % der Eingabe", share in 0.035f..0.045f)
            }
        }
    }

    /**
     * The same pile photographed from farther away (shrunk on the empty table): at 45 % the pieces are too small for
     * the model and the count falls apart (off by 7 to 13 pieces in three of the four photos of the experiment), and
     * the app must say so instead of showing the number as if it was one. Shrunk to 60 % (the pieces are then 24 px)
     * it is still counted well and no warning is shown.
     */
    @Test
    fun aPileFromFarAway_isReportedAsTooSmall() {
        val model = PuzzleModel.create(InstrumentationRegistry.getInstrumentation().targetContext)
        val pile = load(4)
        try {
            val checker = ImageQualityChecker()
            val reference = TFLitePieceDetector(model).detectPieces(pile).pieceCount
            for ((scale, tooSmall) in listOf(0.6f to false, 0.45f to true)) {
                val distant = shrunkOnTable(pile, scale)
                val result = TFLitePieceDetector(model).detectPieces(distant)
                val quality = checker.checkQuality(distant, result.hasHugeStacks, result.typicalPieceEdgeShare)
                val small = quality.issue == PhotoIssue.PIECES_TOO_SMALL
                assertTrue("Maßstab $scale: Teilekante ${result.typicalPieceEdgeShare}, Meldung ${quality.issue}", small == tooSmall)
                if (!tooSmall) assertTrue("Maßstab $scale: ${result.pieceCount} statt $reference", abs(result.pieceCount - reference) <= 3)
                distant.recycle()
            }
        } finally {
            pile.recycle()
            model.close()
        }
    }

    /** [photo] shrunk to [scale] in the middle of a frame of its own size that shows the empty table (the colour of its border). */
    private fun shrunkOnTable(photo: Bitmap, scale: Float): Bitmap {
        val size = photo.width
        val border = IntArray(size).also { photo.getPixels(it, 0, size, 0, 0, size, 1) }
        val table = border.sortedBy { android.graphics.Color.green(it) }[size / 2] // a typical pixel of the table
        val frame = androidx.core.graphics.createBitmap(size, size)
        val canvas = android.graphics.Canvas(frame)
        canvas.drawColor(table)
        val shown = (size * scale).toInt()
        val offset = (size - shown) / 2f
        canvas.drawBitmap(photo, null, android.graphics.RectF(offset, offset, offset + shown, offset + shown), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        return frame
    }
}
