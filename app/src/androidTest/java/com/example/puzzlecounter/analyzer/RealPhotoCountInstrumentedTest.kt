package com.example.puzzlecounter.analyzer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
 * app (assets/real_piles, 1080 x 1080 px): they pin the piece counting down to numbers that were checked by eye.
 *
 * How the numbers were checked: every blob of the photos that the counting rules treat as a cluster was looked at
 * (each holds two or more pieces), and in two dense regions of pile 4 every piece was counted by hand.
 * The tolerance of one or two pieces allows for the small differences between devices (GPU delegate).
 */
@RunWith(AndroidJUnit4::class)
class RealPhotoCountInstrumentedTest {

    private class Expected(val pieces: Int, val clusters: Int)

    private val expected = listOf(Expected(206, 5), Expected(206, 6), Expected(208, 7), Expected(205, 4))

    private fun load(index: Int): Bitmap =
        InstrumentationRegistry.getInstrumentation().context.assets.open("real_piles/pile_$index.jpg").use {
            BitmapFactory.decodeStream(it)!!
        }

    private inline fun <T> withDetection(index: Int, block: (Bitmap, DetectionResult) -> T): T {
        val model = PuzzleModel.create(InstrumentationRegistry.getInstrumentation().targetContext)
        val photo = load(index)
        try {
            val result = TFLitePieceDetector(model).detectPieces(photo)
            try {
                return block(photo, result)
            } finally {
                result.maskImage.recycle()
            }
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
                val quality = ImageQualityChecker().checkQuality(photo, result.hasHugeStacks)
                assertTrue("$name: scharfes, gut belichtetes Foto muss akzeptiert werden: ${quality.warningMessage}", quality.isAcceptable)
            }
        }
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
}
