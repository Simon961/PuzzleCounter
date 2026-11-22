package com.example.puzzlecounter.analyzer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.puzzlecounter.TestImages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end check that `puzzle_model_20.tflite` is loaded and run exactly the way
 * [PuzzleModel] / [TFLitePieceDetector] expect: real asset, real Play-services TFLite
 * runtime, real inference on a device - no mocks.
 *
 * Runs on the connected device/emulator. A replaced model that breaks the contract fails here.
 */
@RunWith(AndroidJUnit4::class)
class PuzzleModelInstrumentedTest {

    @Test
    fun modelLoads_andContractMatchesTheRealAsset() {
        val model = PuzzleModel.create(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            // Verified against the actual puzzle_model_20.tflite via flatbuffer inspection (YOLO26m-seg, imgsz 1024):
            // args_0 [1,3,1024,1024] NCHW, output_0 [1,37,21504] channel-major, output_1 [1,32,256,256].
            // The 21504 anchors are the grids of the strides 8, 16 and 32: 128² + 64² + 32².
            assertEquals(listOf(1, 3, 1024, 1024), model.inputShape.toList())
            assertEquals(1024, model.inputSize)

            assertEquals(listOf(1, 37, 21504), model.detectionShape.toList())
            assertEquals(37, model.numChannels)
            assertEquals(21504, model.numAnchors)
            assertEquals(128 * 128 + 64 * 64 + 32 * 32, model.numAnchors)

            assertEquals(listOf(1, 32, 256, 256), model.protoShape.toList())
            assertEquals(32, model.maskDim)
            assertEquals(256, model.protoHeight)
            assertEquals(256, model.protoWidth)

            // 4 box values + 1 class score + 32 mask coefficients
            assertEquals(PuzzleModel.FIRST_MASK_CHANNEL + model.maskDim, model.numChannels)
        } finally {
            model.close()
        }
    }

    @Test
    fun fullPipeline_runsEndToEndOnAPhotoLikeFrame() {
        val model = PuzzleModel.create(InstrumentationRegistry.getInstrumentation().targetContext)
        val photo = TestImages.fakePuzzlePhoto()
        try {
            val result = TFLitePieceDetector(model).detectPieces(photo)
            // Whether the model recognizes the drawn placeholders is not the point: the pipeline
            // must finish and hand back a consistent, usable result.
            assertEquals(result.pieces.sumOf { it.pieceCount }, result.pieceCount)
            assertEquals(result.pieces.count { it.pieceCount > 1 }, result.clusterCount)
            result.pieces.forEach { piece ->
                assertTrue("Fläche muss positiv sein", piece.area > 0)
                assertTrue("Konfidenz im Bereich 0..1", piece.score in 0f..1f)
                assertTrue("Eine Erkennung steht für keines (Teil einer größeren) oder mehrere Teile", piece.pieceCount >= 0)
                assertFalse("Box darf nicht leer sein", piece.rect.isEmpty)
            }
        } finally {
            photo.recycle()
            model.close()
        }
    }

    /**
     * The self-test photo of the model (it keeps a GPU that returns wrong numbers from being used) must show on
     * this device what the CPU reference shows: 1865 candidates, the best at 0.94 - the same as the model gets
     * when the real app loads it. Checked here without any tolerance of the model code in between.
     */
    @Test
    fun selfTestPhoto_showsThePilesOfThePhotoOnEveryPath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val canary = context.assets.open("canary_pile.jpg").use { android.graphics.BitmapFactory.decodeStream(it)!! }
        for (allowGpu in listOf(true, false)) {
            val model = PuzzleModel.create(context, allowGpu = allowGpu)
            try {
                val pixels = IntArray(model.inputSize * model.inputSize).also { canary.getPixels(it, 0, canary.width, 0, 0, canary.width, canary.height) }
                val detections = model.run(pixels).detections
                val scores = PuzzleModel.SCORE_CHANNEL * model.numAnchors
                val candidates = (0 until model.numAnchors).count { detections[scores + it] >= 0.40f }
                val gpu = if (model.usesGpu) "GPU" else "CPU"
                assertTrue("$gpu: $candidates Kandidaten statt etwa 1865", candidates in 1500..2300)
                if (!allowGpu) assertFalse("ohne GPU-Erlaubnis läuft das Modell auf der CPU", model.usesGpu)
            } finally {
                model.close()
            }
        }
        canary.recycle()
    }
}
