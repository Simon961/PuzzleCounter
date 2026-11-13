package com.example.puzzlecounter.analyzer

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import com.google.android.gms.tflite.gpu.support.TfLiteGpu
import com.google.android.gms.tflite.java.TfLite
import org.tensorflow.lite.DataType
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.gpu.GpuDelegateFactory
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Raw model output of one inference. The arrays are owned by [PuzzleModel] and reused,
 * they are only valid until the next call of [PuzzleModel.run].
 */
class ModelOutput(
    /** Detection head, `[channel * numAnchors + anchor]`, see [PuzzleModel]. */
    val detections: FloatArray,
    /** Mask prototypes, `[k * protoHeight * protoWidth + y * protoWidth + x]`. */
    val protos: FloatArray,
)

/**
 * Thin wrapper around the Ultralytics YOLO26-seg TFLite model (`puzzle_model.tflite`).
 *
 * Model contract (checked against the .tflite file by `PuzzleModelInstrumentedTest`):
 * - Input: FLOAT32 `[1, 3, 640, 640]` (NCHW), RGB, values 0..1, letterboxed with gray 114.
 * - Output 0: FLOAT32 `[1, 37, 8400]`, stored channel-major (`det[channel * 8400 + anchor]`). Channels:
 *   [0..3] box (cx, cy, w, h; normalized 0..1), [4] class score (sigmoid already applied),
 *   [5..36] mask coefficients.
 * - Output 1: FLOAT32 `[1, 32, 160, 160]` mask prototypes (NCHW).
 *
 * The sizes are read from the interpreter, the layout is validated when the model is loaded:
 * a model that breaks the contract fails loudly instead of silently producing garbage.
 *
 * Not thread-safe: create, run and close it on the same single background thread.
 */
class PuzzleModel private constructor(private val interpreter: InterpreterApi) : AutoCloseable {

    val inputShape: IntArray = interpreter.getInputTensor(0).shape()
    val inputSize: Int

    private val detectionOutputIndex: Int
    private val protoOutputIndex: Int
    val detectionShape: IntArray
    val protoShape: IntArray

    /** Channels per anchor (4 box + 1 class + [maskDim] mask coefficients). */
    val numChannels: Int
    val numAnchors: Int
    val maskDim: Int
    val protoHeight: Int
    val protoWidth: Int

    // Preallocated buffers, reused for every inference.
    private val inputBuffer: ByteBuffer
    private val outputBuffers: Map<Int, ByteBuffer>
    private val output: ModelOutput

    init {
        val inputTensor = interpreter.getInputTensor(0)
        require(inputTensor.dataType() == DataType.FLOAT32) {
            "Unerwarteter Input-Datentyp ${inputTensor.dataType()} (erwartet FLOAT32)"
        }
        require(inputShape.size == 4 && inputShape[0] == 1 && inputShape[1] == 3 && inputShape[2] == inputShape[3]) {
            "Unerwartete Input-Shape ${inputShape.contentToString()} (erwartet [1, 3, N, N])"
        }
        inputSize = inputShape[2]

        var detIdx = -1
        var protoIdx = -1
        for (i in 0 until interpreter.outputTensorCount) {
            when (interpreter.getOutputTensor(i).shape().size) {
                3 -> if (detIdx == -1) detIdx = i
                4 -> if (protoIdx == -1) protoIdx = i
            }
        }
        require(detIdx != -1 && protoIdx != -1) {
            "Das Modell braucht einen Detektions-Output (Rang 3) und einen Masken-Output (Rang 4)"
        }
        detectionOutputIndex = detIdx
        protoOutputIndex = protoIdx

        val detectionTensor = interpreter.getOutputTensor(detIdx)
        val protoTensor = interpreter.getOutputTensor(protoIdx)
        require(detectionTensor.dataType() == DataType.FLOAT32 && protoTensor.dataType() == DataType.FLOAT32) {
            "Unerwartete Output-Datentypen ${detectionTensor.dataType()} / ${protoTensor.dataType()} (erwartet FLOAT32)"
        }
        detectionShape = detectionTensor.shape()
        protoShape = protoTensor.shape()
        require(detectionShape[0] == 1 && protoShape[0] == 1) { "Es wird nur Batch-Größe 1 unterstützt" }

        // Detection [1, channels, anchors], prototypes [1, maskDim, height, width].
        numChannels = detectionShape[1]
        numAnchors = detectionShape[2]
        maskDim = protoShape[1]
        protoHeight = protoShape[2]
        protoWidth = protoShape[3]
        require(numChannels == FIRST_MASK_CHANNEL + maskDim) {
            "Ungültiges Output-Layout: erwartet [1, ${FIRST_MASK_CHANNEL + maskDim}, Anker] und " +
                "Masken [1, $maskDim, H, W], erhalten ${detectionShape.contentToString()} und ${protoShape.contentToString()}"
        }

        inputBuffer = ByteBuffer.allocateDirect(inputTensor.numBytes()).order(ByteOrder.nativeOrder())
        outputBuffers = (0 until interpreter.outputTensorCount).associateWith { i ->
            ByteBuffer.allocateDirect(interpreter.getOutputTensor(i).numBytes()).order(ByteOrder.nativeOrder())
        }
        output = ModelOutput(
            detections = FloatArray(detectionTensor.numElements()),
            protos = FloatArray(protoTensor.numElements())
        )
    }

    /** Runs the model on letterboxed ARGB pixels of size [inputSize] x [inputSize]. */
    fun run(argbPixels: IntArray): ModelOutput {
        require(argbPixels.size == inputSize * inputSize) {
            "Erwartet ${inputSize * inputSize} Pixel, erhalten ${argbPixels.size}"
        }
        inputBuffer.rewind()
        // NCHW: all red values, then all green values, then all blue values.
        for (shift in intArrayOf(16, 8, 0)) {
            for (p in argbPixels) inputBuffer.putFloat(((p shr shift) and 0xFF) / 255f)
        }
        inputBuffer.rewind()
        outputBuffers.values.forEach { it.rewind() }

        interpreter.runForMultipleInputsOutputs(arrayOf<Any>(inputBuffer), outputBuffers)

        outputBuffers.getValue(detectionOutputIndex).let {
            it.rewind()
            it.asFloatBuffer().get(output.detections)
        }
        outputBuffers.getValue(protoOutputIndex).let {
            it.rewind()
            it.asFloatBuffer().get(output.protos)
        }
        return output
    }

    /** Runs a gray frame through the model and checks that the outputs are finite. */
    private fun warmUpAndValidate() {
        val out = run(IntArray(inputSize * inputSize) { LETTERBOX_COLOR })
        require(out.detections.all { it.isFinite() }) { "Modell liefert ungültige Werte (NaN/Inf)" }
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        private const val TAG = "PuzzleModel"
        private const val MODEL_ASSET = "puzzle_model.tflite"
        private const val NUM_THREADS = 4

        /** Detection channel of the (single) class score; channels 0..3 hold the box. */
        const val SCORE_CHANNEL = 4

        /** First of the mask coefficient channels. */
        const val FIRST_MASK_CHANNEL = 5

        /** Ultralytics letterbox padding color (114, 114, 114). */
        const val LETTERBOX_COLOR: Int = (0xFF shl 24) or (114 shl 16) or (114 shl 8) or 114

        /**
         * Initializes the Play-services TFLite runtime and loads the model.
         * Tries the GPU delegate first and falls back to the CPU (XNNPACK).
         *
         * Blocking call: must be called on a background thread.
         */
        fun create(context: Context): PuzzleModel {
            val appContext = context.applicationContext

            val gpuAvailable = try {
                Tasks.await(TfLiteGpu.isGpuDelegateAvailable(appContext))
            } catch (e: Exception) {
                Log.w(TAG, "GPU-Verfügbarkeit konnte nicht geprüft werden", e)
                false
            }

            Tasks.await(
                TfLite.initialize(
                    appContext,
                    TfLiteInitializationOptions.builder()
                        .setEnableGpuDelegateSupport(gpuAvailable)
                        .build()
                )
            )

            val modelBuffer = loadModelFile(appContext)

            if (gpuAvailable) {
                try {
                    return build(modelBuffer, useGpu = true).also { Log.i(TAG, "Modell mit GPU-Delegate geladen") }
                } catch (e: Exception) {
                    Log.w(TAG, "GPU-Delegate fehlgeschlagen, wechsle auf CPU", e)
                }
            }
            return build(modelBuffer, useGpu = false).also { Log.i(TAG, "Modell auf CPU ($NUM_THREADS Threads) geladen") }
        }

        private fun build(modelBuffer: MappedByteBuffer, useGpu: Boolean): PuzzleModel {
            val options = InterpreterApi.Options()
                // Required: the app only ships the Play-services runtime, not the bundled one.
                .setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY)
                .setNumThreads(NUM_THREADS)
            if (useGpu) options.addDelegateFactory(GpuDelegateFactory())

            val interpreter = InterpreterApi.create(modelBuffer, options)
            val model = try {
                PuzzleModel(interpreter)
            } catch (e: Exception) {
                interpreter.close()
                throw e
            }
            try {
                model.warmUpAndValidate()
            } catch (e: Exception) {
                model.close()
                throw e
            }
            return model
        }

        private fun loadModelFile(context: Context): MappedByteBuffer =
            context.assets.openFd(MODEL_ASSET).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
    }
}
