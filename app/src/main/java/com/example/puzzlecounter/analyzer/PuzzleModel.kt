package com.example.puzzlecounter.analyzer

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.scale
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
import java.util.concurrent.TimeUnit

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

/** The model ran, but its results are not what this model gives on any working device (see [PuzzleModel]). */
class ImplausibleOutputException(message: String) : IllegalStateException(message)

/**
 * Thin wrapper around the Ultralytics YOLO26-seg TFLite model (`puzzle_model_20.tflite`, a YOLO26m-seg for one class).
 *
 * Model contract (checked against the .tflite file by `PuzzleModelInstrumentedTest`):
 * - Input: FLOAT32 `[1, 3, 1024, 1024]` (NCHW), RGB, values 0..1, letterboxed with gray 114.
 * - Output 0: FLOAT32 `[1, 37, 21504]` (anchors of the strides 8, 16 and 32), stored channel-major
 *   (`det[channel * 21504 + anchor]`). Channels: [0..3] box (cx, cy, w, h; normalized 0..1), [4] class score
 *   (sigmoid already applied), [5..36] mask coefficients. The model has no built-in NMS (`end2end` is off).
 * - Output 1: FLOAT32 `[1, 32, 256, 256]` mask prototypes (NCHW).
 *
 * The sizes are read from the interpreter, the layout is validated when the model is loaded:
 * a model that breaks the contract fails loudly instead of silently producing garbage.
 *
 * The app runs on many different phones, so loading is defensive. A self-test photo (`canary_pile.jpg`) is run
 * through the model once, and the GPU is only kept if its results are plausible (a GPU can return finite but wrong
 * numbers); otherwise the CPU takes over. A [GpuGuard] stops a GPU driver that crashes the process from doing so at
 * every start.
 *
 * Not thread-safe: create, run and close it on the same single background thread.
 */
class PuzzleModel private constructor(
    private val interpreter: InterpreterApi,
    /** Whether the GPU delegate runs the model (otherwise the CPU does). */
    val usesGpu: Boolean,
) : AutoCloseable {

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

    /**
     * Runs a frame through the model and checks that its outputs are usable: finite and, with the [canary] frame (a
     * photo of a pile of pieces), about as many confident candidates as the model reports for that photo on every
     * working device. Without a canary a gray frame only proves that the outputs are finite. With [strict] the
     * results that are not plausible throw an [ImplausibleOutputException], otherwise they are only logged.
     */
    private fun warmUpAndValidate(canary: IntArray?, strict: Boolean) {
        val out = run(canary ?: IntArray(inputSize * inputSize) { LETTERBOX_COLOR })
        require(out.detections.all { it.isFinite() }) { "Modell liefert ungültige Werte (NaN/Inf)" }
        if (canary == null) return

        val scores = SCORE_CHANNEL * numAnchors
        var candidates = 0
        var best = 0f
        for (anchor in 0 until numAnchors) {
            val score = out.detections[scores + anchor]
            if (score >= CANARY_SCORE) candidates++
            if (score > best) best = score
        }
        if (candidates !in CANARY_CANDIDATES || best < CANARY_BEST_SCORE) {
            val message = "Modell liefert unplausible Ergebnisse: $candidates Kandidaten, bester Wert $best"
            if (strict) throw ImplausibleOutputException(message) else Log.w(TAG, message)
        }
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        private const val TAG = "PuzzleModel"
        private const val MODEL_ASSET = "puzzle_model_20.tflite"
        private const val NUM_THREADS = 4

        // The Google services may hang (an update in progress, no network for a module that is still to be fetched):
        // the app must end up with an error message then, not wait for ever.
        private const val GPU_CHECK_TIMEOUT_SECONDS = 30L
        private const val RUNTIME_TIMEOUT_MINUTES = 3L

        /** The self-test photo: a pile of 205 pieces, 1024 x 1024 px. */
        private const val CANARY_ASSET = "canary_pile.jpg"

        /** Confidence from which a candidate counts in the self test (the same as the detector's threshold). */
        private const val CANARY_SCORE = 0.40f

        /**
         * The CPU finds 1865 candidates in the self-test photo, best score 0.94. Anything between half and double
         * that, and a confident best candidate, is the model working; a broken GPU gives nothing, or everything,
         * or something else entirely.
         */
        private val CANARY_CANDIDATES = 900..3700
        private const val CANARY_BEST_SCORE = 0.7f

        /** Detection channel of the (single) class score; channels 0..3 hold the box. */
        const val SCORE_CHANNEL = 4

        /** First of the mask coefficient channels. */
        const val FIRST_MASK_CHANNEL = 5

        /** Ultralytics letterbox padding color (114, 114, 114). */
        const val LETTERBOX_COLOR: Int = (0xFF shl 24) or (114 shl 16) or (114 shl 8) or 114

        /**
         * Initializes the Play-services TFLite runtime and loads the model.
         * Tries the GPU delegate first (unless [allowGpu] is false or the [GpuGuard] has closed it) and falls back to
         * the CPU (XNNPACK).
         *
         * Blocking call: must be called on a background thread. It takes a while: the GPU has to compile its kernels
         * for this model, 3 to 29 s on a current flagship phone (the speed of the phone varies a lot). The compiled
         * kernels are kept in the code cache of the app (see [gpuOptions]); with them loading takes 1 to 8 s, and the
         * compiling happens once after an installation or an update.
         */
        fun create(context: Context, allowGpu: Boolean = true): PuzzleModel {
            val appContext = context.applicationContext
            val start = SystemClock.elapsedRealtime()

            val gpuSupported = initializeRuntime(appContext, gpuDelegateAvailable(appContext))
            val modelBuffer = loadModelFile(appContext)
            val canary = { size: Int -> loadCanary(appContext, size) }

            val guard = GpuGuard(appContext.getSharedPreferences("gpu_guard", Context.MODE_PRIVATE), appVersion(appContext))
            if (allowGpu && gpuSupported && guard.mayTryGpu()) {
                guard.attemptStarted()
                try {
                    return build(modelBuffer, gpuOptions(appContext, modelBuffer), canary, strict = true).also {
                        guard.attemptSucceeded()
                        Log.i(TAG, "Modell mit GPU-Delegate geladen (${SystemClock.elapsedRealtime() - start} ms)")
                    }
                } catch (e: Exception) {
                    guard.attemptFailed(definitive = e is ImplausibleOutputException)
                    Log.w(TAG, "GPU-Delegate fehlgeschlagen, wechsle auf CPU", e)
                }
            }
            return build(modelBuffer, gpu = null, canary, strict = false)
                .also { Log.i(TAG, "Modell auf CPU ($NUM_THREADS Threads) geladen (${SystemClock.elapsedRealtime() - start} ms)") }
        }

        /** Whether this device can run the GPU delegate at all (the Google services know which GPUs can). */
        private fun gpuDelegateAvailable(context: Context): Boolean = try {
            Tasks.await(TfLiteGpu.isGpuDelegateAvailable(context), GPU_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "GPU-Verfügbarkeit konnte nicht geprüft werden", e)
            false
        }

        /**
         * Starts the Play-services runtime and returns whether the GPU delegate can be used: if the runtime cannot
         * be started with its GPU module (not loaded yet, no network), it is started without, for the CPU.
         */
        private fun initializeRuntime(context: Context, gpuAvailable: Boolean): Boolean {
            fun start(gpuSupport: Boolean) = Tasks.await(
                TfLite.initialize(context, TfLiteInitializationOptions.builder().setEnableGpuDelegateSupport(gpuSupport).build()),
                RUNTIME_TIMEOUT_MINUTES,
                TimeUnit.MINUTES,
            )
            if (gpuAvailable) {
                try {
                    start(gpuSupport = true)
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Laufzeit mit GPU-Unterstützung nicht verfügbar, starte sie ohne", e)
                }
            }
            start(gpuSupport = false)
            return false
        }

        /**
         * The GPU delegate keeps the kernels it compiled for the model in the code cache of the app (Android clears
         * that on every update of the app). The token names the model by its file and size, so the kernels of another
         * model are never taken for these.
         */
        private fun gpuOptions(context: Context, modelBuffer: MappedByteBuffer) = GpuDelegateFactory.Options()
            .setSerializationParams(context.codeCacheDir.path, "${MODEL_ASSET}_${modelBuffer.capacity()}")

        private fun build(
            modelBuffer: MappedByteBuffer,
            gpu: GpuDelegateFactory.Options?,
            canary: (size: Int) -> IntArray?,
            strict: Boolean,
        ): PuzzleModel {
            val options = InterpreterApi.Options()
                // Required: the app only ships the Play-services runtime, not the bundled one.
                .setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY)
                .setNumThreads(NUM_THREADS)
            if (gpu != null) options.addDelegateFactory(GpuDelegateFactory(gpu))

            val interpreter = InterpreterApi.create(modelBuffer, options)
            val model = try {
                PuzzleModel(interpreter, usesGpu = gpu != null)
            } catch (e: Exception) {
                interpreter.close()
                throw e
            }
            try {
                model.warmUpAndValidate(canary(model.inputSize), strict)
            } catch (e: Exception) {
                model.close()
                throw e
            }
            return model
        }

        /** The self-test photo as pixels of a [size] x [size] frame, or null if it cannot be read (the check is then skipped). */
        private fun loadCanary(context: Context, size: Int): IntArray? = try {
            context.assets.open(CANARY_ASSET).use { stream ->
                val decoded = BitmapFactory.decodeStream(stream)
                if (decoded == null) {
                    null
                } else {
                    val frame = if (decoded.width == size && decoded.height == size) decoded else decoded.scale(size, size)
                    IntArray(size * size).also {
                        frame.getPixels(it, 0, size, 0, 0, size, size)
                        if (frame !== decoded) frame.recycle()
                        decoded.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Das Testfoto für das Modell konnte nicht gelesen werden", e)
            null
        }

        /** The version code of the app, 0 if it cannot be read. */
        private fun appVersion(context: Context): Long = try {
            PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
        } catch (e: Exception) {
            0L
        }

        private fun loadModelFile(context: Context): MappedByteBuffer =
            context.assets.openFd(MODEL_ASSET).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
    }
}
