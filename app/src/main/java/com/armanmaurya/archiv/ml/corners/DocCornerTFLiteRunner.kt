package com.armanmaurya.archiv.ml.corners

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import android.util.Size
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The DocCornerTFLiteRunner class provides functionality for running inference on a TFLite model.
 * Model expects NHWC format input: [1, 256, 256, 3]
 * Model outputs a single probability mask: [1, 256, 256, 1]
 */
class DocCornerTFLiteRunner private constructor(context: Context, modelAssetPath: String) : AutoCloseable {

    private val interpreter: Interpreter
    private val inputHeight: Int
    private val inputWidth: Int
    private val inputDataType: DataType

    init {
        val modelFile = copyAssetToCache(context, modelAssetPath, context.cacheDir)
        interpreter = Interpreter(modelFile, Interpreter.Options().apply {
            setNumThreads(maxOf(1, Runtime.getRuntime().availableProcessors() / 2))
        })

        val inputTensor = interpreter.getInputTensor(0)
        val inShape = inputTensor.shape()
        // expected shape like [1, H, W, C]
        if (inShape.size >= 3) {
            inputHeight = inShape[1]
            inputWidth = inShape[2]
        } else {
            inputHeight = 224
            inputWidth = 224
        }
        inputDataType = inputTensor.dataType()

        Log.d(TAG, "Model loaded from ${modelFile.absolutePath} inputShape=${inShape.contentToString()} outputs=${interpreter.outputTensorCount}")
    }

    data class Outputs(val probmap: FloatArray, val width: Int, val height: Int)

    data class SegmentationResult(
        val segmentation: Segmentation,
        val inferenceTime: Long
    )

    data class HeatmapsResult(
        val heatmaps: Array<FloatArray>, // [channel][w*h]
        val width: Int,
        val height: Int,
        val presence: Float,
        val inferenceTime: Long
    )

    data class Segmentation(
        private val probmap: FloatArray,
        val width: Int,
        val height: Int
    ) {
        fun get(x: Int, y: Int): Float = probmap[y * width + x]

        fun toBinaryMask(): Bitmap {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            for (i in probmap.indices) {
                val v = (probmap[i].coerceIn(0f, 1f) * 255f).toInt()
                pixels[i] = Color.rgb(v, v, v)
            }
            bmp.setPixels(pixels, 0, width, 0, 0, width, height)
            return bmp
        }

        fun toMat(): org.opencv.core.Mat {
            val threshold = 0.5f
            val mask = org.opencv.core.Mat(height, width, org.opencv.core.CvType.CV_8UC1)
            val data = ByteArray(width * height)

            for (i in probmap.indices) {
                data[i] = if (probmap[i] >= threshold) 255.toByte() else 0.toByte()
            }

            mask.put(0, 0, data)
            return mask
        }

        fun maskSize() = Size(width, height)
    }

    fun run(inputNhwc: FloatArray): Outputs {
        // Generic runner for single-input models; will select best spatial output as probmap
        // Fill input buffer into a ByteBuffer
        val inputBuf = ByteBuffer.allocateDirect(inputNhwc.size * 4).apply { order(ByteOrder.nativeOrder()) }
        inputBuf.asFloatBuffer().put(inputNhwc)
        inputBuf.rewind()

        // Prepare output buffers for all outputs
        val outputs = mutableMapOf<Int, Any>()
        val candidateInfo = mutableListOf<Triple<Int, Int, Int>>() // index, w, h

        for (i in 0 until interpreter.outputTensorCount) {
            val t = interpreter.getOutputTensor(i)
            val shape = t.shape()
            val dtype = t.dataType()
            // consider shapes like [1, H, W, 1] or [1, H, W]
            if (shape.size >= 3) {
                val h = shape[shape.size - 3]
                val w = shape[shape.size - 2]
                val count = h * w
                val buf = when (dtype) {
                    DataType.FLOAT32 -> ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
                    DataType.UINT8, DataType.INT8 -> ByteBuffer.allocateDirect(count)
                    else -> ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
                }
                outputs[i] = buf
                candidateInfo += Triple(i, w, h)
            } else if (shape.size == 2) {
                val w = shape[1]
                val count = w
                val buf = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
                outputs[i] = buf
                candidateInfo += Triple(i, w, 1)
            } else {
                // fallback: allocate small buffer
                val buf = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
                outputs[i] = buf
            }
        }

        // Run model
        try {
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuf), outputs)
        } catch (t: Throwable) {
            Log.e(TAG, "Model run failed", t)
            throw t
        }

        // Pick best candidate output (largest spatial area)
        val best = candidateInfo.maxByOrNull { it.second * it.third } ?: return Outputs(FloatArray(0), 0, 0)
        val outIdx = best.first
        val outW = best.second
        val outH = best.third
        val outTensor = interpreter.getOutputTensor(outIdx)
        val dtype = outTensor.dataType()
        val buf = outputs[outIdx] as ByteBuffer
        buf.rewind()

        val probmap = FloatArray(outW * outH)
        when (dtype) {
            DataType.FLOAT32 -> buf.asFloatBuffer().get(probmap)
            DataType.UINT8 -> {
                val raw = ByteArray(probmap.size)
                buf.get(raw)
                for (i in raw.indices) probmap[i] = (raw[i].toInt() and 0xFF) / 255f
            }
            DataType.INT8 -> {
                val raw = ByteArray(probmap.size)
                buf.get(raw)
                for (i in raw.indices) probmap[i] = (raw[i].toInt() + 128).coerceIn(0, 255) / 255f
            }
            else -> buf.asFloatBuffer().get(probmap)
        }

        for (i in probmap.indices) probmap[i] = probmap[i].coerceIn(0f, 1f)
        return Outputs(probmap, outW, outH)
    }

    @Synchronized
    fun runSegmentationAndReturn(bitmap: Bitmap): SegmentationResult? {
        return try {
            val startTime = SystemClock.uptimeMillis()

            val inputTensor = interpreter.getInputTensor(0)
            val (_, inH, inW, _) = if (inputTensor.shape().size >= 4) inputTensor.shape() else intArrayOf(1, inputHeight, inputWidth, 3)

            val imageProcessor =
                ImageProcessor.Builder()
                    .add(ResizeOp(inH, inW, ResizeOp.ResizeMethod.BILINEAR))
                    .add(NormalizeOp(127.5f, 127.5f))
                    .build()

            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bitmap)
            val processedImage = imageProcessor.process(tensorImage)

            val segmentation = segmentMultiOutput(processedImage)
            val inferenceTime = SystemClock.uptimeMillis() - startTime
            segmentation?.let { SegmentationResult(it, inferenceTime) }
        } catch (t: Throwable) {
            Log.e(TAG, "Segmentation failed", t)
            null
        }
    }

    private fun segmentMultiOutput(tensorImage: TensorImage): Segmentation? {
        val outputs = mutableMapOf<Int, Any>()
        val candidateInfo = mutableListOf<Triple<Int, Int, Int>>()

        for (i in 0 until interpreter.outputTensorCount) {
            val t = interpreter.getOutputTensor(i)
            val shape = t.shape()
            val dtype = t.dataType()
            if (shape.size >= 3) {
                val h = shape[shape.size - 3]
                val w = shape[shape.size - 2]
                val count = h * w
                val buf = when (dtype) {
                    DataType.FLOAT32 -> ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
                    DataType.UINT8, DataType.INT8 -> ByteBuffer.allocateDirect(count)
                    else -> ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
                }
                outputs[i] = buf
                candidateInfo += Triple(i, w, h)
            }
        }

        if (outputs.isEmpty()) return null

        interpreter.runForMultipleInputsOutputs(arrayOf(tensorImage.tensorBuffer.buffer), outputs)

        val best = candidateInfo.maxByOrNull { it.second * it.third } ?: return null
        val outIdx = best.first
        val outW = best.second
        val outH = best.third
        val outTensor = interpreter.getOutputTensor(outIdx)
        val dtype = outTensor.dataType()
        val buf = outputs[outIdx] as ByteBuffer
        buf.rewind()

        val floats = FloatArray(outW * outH)
        when (dtype) {
            DataType.FLOAT32 -> buf.asFloatBuffer().get(floats)
            DataType.UINT8 -> {
                val raw = ByteArray(floats.size)
                buf.get(raw)
                for (i in raw.indices) floats[i] = (raw[i].toInt() and 0xFF) / 255f
            }
            DataType.INT8 -> {
                val raw = ByteArray(floats.size)
                buf.get(raw)
                for (i in raw.indices) floats[i] = (raw[i].toInt() + 128).coerceIn(0, 255) / 255f
            }
            else -> buf.asFloatBuffer().get(floats)
        }

        for (i in floats.indices) floats[i] = floats[i].coerceIn(0f, 1f)
        return Segmentation(floats, outW, outH)
    }

    /**
     * Run model and try to extract multi-channel heatmaps and an optional presence score.
     * Looks for an output tensor shaped [..., H, W, C] where C >= 1 (prefer C==4).
     */
    fun runHeatmapsAndPresence(tensorImage: TensorImage): HeatmapsResult? {
        val startTime = SystemClock.uptimeMillis()

        // Prepare outputs buffers
        val outputs = mutableMapOf<Int, Any>()
        var heatmapCandidate: Triple<Int, Int, Int>? = null // idx, w, h
        var heatmapChannels = 1
        var presenceIdx: Int? = null

        for (i in 0 until interpreter.outputTensorCount) {
            val t = interpreter.getOutputTensor(i)
            val shape = t.shape()
            val dtype = t.dataType()

            if (shape.size >= 3) {
                val h = shape[shape.size - 3]
                val w = shape[shape.size - 2]
                val c = shape[shape.size - 1]
                // Prefer tensor with channel dim > 1 (likely heatmaps)
                val count = h * w * c
                val buf = when (dtype) {
                    DataType.FLOAT32 -> ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
                    DataType.UINT8, DataType.INT8 -> ByteBuffer.allocateDirect(count)
                    else -> ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
                }
                outputs[i] = buf
                // Choose candidate: prefer higher channel count (e.g., 4)
                if (heatmapCandidate == null || c > heatmapChannels || (c == heatmapChannels && h * w > heatmapCandidate.second * heatmapCandidate.third)) {
                    heatmapCandidate = Triple(i, w, h)
                    heatmapChannels = c
                }
            } else if (shape.size == 2 && shape[1] <= 4) {
                // small vector, could be presence/logit
                val count = shape[1]
                val buf = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
                outputs[i] = buf
                presenceIdx = i
            } else if (shape.size == 1) {
                val count = shape[0]
                val buf = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
                outputs[i] = buf
                presenceIdx = i
            } else {
                // fallback allocate small
                outputs[i] = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            }
        }

        if (outputs.isEmpty() || heatmapCandidate == null) return null

        // Run the model
        try {
            interpreter.runForMultipleInputsOutputs(arrayOf(tensorImage.tensorBuffer.buffer), outputs)
        } catch (t: Throwable) {
            Log.e(TAG, "runHeatmapsAndPresence failed", t)
            return null
        }

        val (idx, w, h) = heatmapCandidate
        val t = interpreter.getOutputTensor(idx)
        val shape = t.shape()
        val c = shape[shape.size - 1]
        val buf = outputs[idx] as ByteBuffer
        buf.rewind()

        val floats = FloatArray(w * h * c)
        when (t.dataType()) {
            DataType.FLOAT32 -> buf.asFloatBuffer().get(floats)
            DataType.UINT8 -> {
                val raw = ByteArray(floats.size)
                buf.get(raw)
                for (i in raw.indices) floats[i] = (raw[i].toInt() and 0xFF) / 255f
            }
            DataType.INT8 -> {
                val raw = ByteArray(floats.size)
                buf.get(raw)
                for (i in raw.indices) floats[i] = (raw[i].toInt() + 128).coerceIn(0, 255) / 255f
            }
            else -> buf.asFloatBuffer().get(floats)
        }

        // split channels
        val heatmaps = Array(c) { ch ->
            val arr = FloatArray(w * h)
            var k = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    arr[k++] = floats[(y * w + x) * c + ch].coerceIn(0f, 1f)
                }
            }
            arr
        }

        // presence score
        var presence = 0f
        presenceIdx?.let { pidx ->
            val pbuf = outputs[pidx] as ByteBuffer
            pbuf.rewind()
            val pTensor = interpreter.getOutputTensor(pidx)
            val pCount = pTensor.shape().fold(1) { acc, v -> acc * v }
            val pf = FloatArray(pCount)
            when (pTensor.dataType()) {
                DataType.FLOAT32 -> pbuf.asFloatBuffer().get(pf)
                DataType.UINT8 -> {
                    val raw = ByteArray(pf.size)
                    pbuf.get(raw)
                    for (i in raw.indices) pf[i] = (raw[i].toInt() and 0xFF) / 255f
                }
                DataType.INT8 -> {
                    val raw = ByteArray(pf.size)
                    pbuf.get(raw)
                    for (i in raw.indices) pf[i] = (raw[i].toInt() + 128).coerceIn(0, 255) / 255f
                }
                else -> pbuf.asFloatBuffer().get(pf)
            }
            if (pf.isNotEmpty()) presence = pf[0]
        }

        val inferenceTime = SystemClock.uptimeMillis() - startTime
        return HeatmapsResult(heatmaps, w, h, presence, inferenceTime)
    }

    @Synchronized
    fun runHeatmapsAndPresence(bitmap: Bitmap): HeatmapsResult? {
        return try {
            val inputTensor = interpreter.getInputTensor(0)
            val inShape = if (inputTensor.shape().size >= 4) inputTensor.shape() else intArrayOf(1, inputHeight, inputWidth, 3)
            val inH = inShape[1]
            val inW = inShape[2]

            val imageProcessor =
                ImageProcessor.Builder()
                    .add(ResizeOp(inH, inW, ResizeOp.ResizeMethod.BILINEAR))
                    .add(NormalizeOp(127.5f, 127.5f))
                    .build()

            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bitmap)
            val processed = imageProcessor.process(tensorImage)
            runHeatmapsAndPresence(processed)
        } catch (t: Throwable) {
            Log.e(TAG, "runHeatmapsAndPresence(bitmap) failed", t)
            null
        }
    }

    private fun outputToArray(outputBuffer: ByteBuffer, width: Int, height: Int): FloatArray {
        outputBuffer.rewind()
        val maskFloats = FloatArray(width * height)
        outputBuffer.asFloatBuffer().get(maskFloats)
        for (i in maskFloats.indices) {
            maskFloats[i] = maskFloats[i].coerceIn(0f, 1f)
        }
        return maskFloats
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        private const val TAG = "DocCornerTFLiteRunner"
        const val IN_H = 256
        const val IN_W = 256

        @Volatile
        private var instance: DocCornerTFLiteRunner? = null
        private val LOCK = Any()
        private val DEFAULT_EXECUTOR: Executor = Executors.newSingleThreadExecutor()

        fun getInstance(context: Context, modelAssetPath: String): DocCornerTFLiteRunner {
            return instance ?: synchronized(LOCK) {
                instance ?: DocCornerTFLiteRunner(context, modelAssetPath).also { instance = it }
            }
        }

        fun getInstanceAsync(context: Context, modelAssetPath: String, executor: Executor = DEFAULT_EXECUTOR): CompletableFuture<DocCornerTFLiteRunner> {
            return CompletableFuture.supplyAsync({
                getInstance(context, modelAssetPath)
            }, executor)
        }

        fun isInstanceLoaded(): Boolean = instance != null

        fun releaseInstance() {
            synchronized(LOCK) {
                instance?.let {
                    try {
                        it.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error closing instance: ${e.message}")
                    }
                    instance = null
                }
            }
        }

        private fun copyAssetToCache(context: Context, assetPath: String, cacheDir: File): File {
            val am = context.assets
            val baseName = File(assetPath).name
            val versionCode: Long = try {
                val pi = context.packageManager.getPackageInfo(context.packageName, 0)
                if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
            } catch (e: Exception) {
                -1L
            }
            val versionedName = "${versionCode}_$baseName"
            val outFile = File(cacheDir, versionedName)
            if (!outFile.exists()) {
                Log.i(TAG, "Copying asset $assetPath to cache as $versionedName...")
                am.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                deleteStaleModelFiles(cacheDir, baseName, versionedName)
            }
            return outFile
        }

        private fun deleteStaleModelFiles(cacheDir: File, baseName: String, currentName: String) {
            val staleFiles = cacheDir.listFiles { _, name ->
                name.endsWith("_$baseName") && name != currentName
            }
            staleFiles?.forEach { stale ->
                if (stale.delete()) {
                    Log.i(TAG, "Deleted stale cached model: ${stale.name}")
                } else {
                    Log.w(TAG, "Failed to delete stale cached model: ${stale.name}")
                }
            }
        }
    }
}
