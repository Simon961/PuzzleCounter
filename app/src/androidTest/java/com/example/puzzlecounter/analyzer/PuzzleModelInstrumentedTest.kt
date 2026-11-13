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
 * End-to-end check that `puzzle_model.tflite` is loaded and run exactly the way
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
            // Verified against the actual puzzle_model.tflite via flatbuffer inspection:
            // args_0 [1,3,640,640] NCHW, output_0 [1,37,8400] channel-major, output_1 [1,32,160,160].
            assertEquals(listOf(1, 3, 640, 640), model.inputShape.toList())
            assertEquals(640, model.inputSize)

            assertEquals(listOf(1, 37, 8400), model.detectionShape.toList())
            assertEquals(37, model.numChannels)
            assertEquals(8400, model.numAnchors)

            assertEquals(listOf(1, 32, 160, 160), model.protoShape.toList())
            assertEquals(32, model.maskDim)
            assertEquals(160, model.protoHeight)
            assertEquals(160, model.protoWidth)

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
            try {
                // Whether the model recognizes the drawn placeholders is not the point: the pipeline
                // must finish and hand back a consistent, usable result.
                assertFalse(result.maskImage.isRecycled)
                assertEquals(photo.width, result.maskImage.width)
                assertEquals(photo.height, result.maskImage.height)
                assertEquals(result.pieces.sumOf { it.pieceCount }, result.pieceCount)
                assertEquals(result.pieces.count { it.pieceCount > 1 }, result.clusterCount)
                result.pieces.forEach { piece ->
                    assertTrue("Fläche muss positiv sein", piece.area > 0)
                    assertTrue("Konfidenz im Bereich 0..1", piece.score in 0f..1f)
                    assertTrue("Eine Erkennung steht für keines (Teil einer größeren) oder mehrere Teile", piece.pieceCount >= 0)
                    assertFalse("Box darf nicht leer sein", piece.rect.isEmpty)
                }
            } finally {
                result.maskImage.recycle()
            }
        } finally {
            photo.recycle()
            model.close()
        }
    }
}
