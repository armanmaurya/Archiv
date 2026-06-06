package com.armanmaurya.archiv.ml.corners

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import android.util.Size
import com.armanmaurya.archiv.ml.DoccornernetModelFp16
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The DocCornerTFLiteRunner class provides functionality for running inference on a TFLite model.
 * Model expects NHWC format input: [1, 224, 224, 3]
 * Model outputs corner heatmaps [1, 56, 56, 4] and presence score [1, 1]
 */
class DocCornerTFLiteRunner private constructor(context: Context) : AutoCloseable {

    private val model: DoccornernetModelFp16 = DoccornernetModelFp16.newInstance(context)
    private val inputHeight: Int = 224
    private val inputWidth: Int = 224

    init {
        Log.d(TAG, "Model loaded using DoccornernetModelFp16")
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

        fun maskSize() = Size(width, height)
    }

    fun run(inputNhwc: FloatArray): Outputs {
        // Generic runner for single-input models
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, inputHeight, inputWidth, 3), DataType.FLOAT32)
        val byteBuffer = ByteBuffer.allocateDirect(inputNhwc.size * 4).apply { order(ByteOrder.nativeOrder()) }
        byteBuffer.asFloatBuffer().put(inputNhwc)
        inputFeature0.loadBuffer(byteBuffer)

        val outputs = model.process(inputFeature0)
        // Heuristic: pick the output with the larger size as the main output
        val out0 = outputs.outputFeature0AsTensorBuffer
        val out1 = outputs.outputFeature1AsTensorBuffer
        val s0 = out0.flatSize
        val s1 = out1.flatSize
        val outputBuffer = if (s0 >= s1) out0 else out1
        
        val shape = outputBuffer.shape
        val (outH, outW, outC) = when (shape.size) {
            4 -> Triple(shape[1], shape[2], shape[3])
            3 -> Triple(shape[0], shape[1], shape[2])
            2 -> Triple(shape[0], shape[1], 1)
            else -> Triple(1, 1, shape.firstOrNull() ?: 1)
        }

        val probmap = outputBuffer.floatArray
        // If multiple channels, we might need to pick one or handle it.
        // For simplicity, returning the first channel if it's a spatial output.
        return if (outC > 1) {
            val singleChannel = FloatArray(outH * outW)
            for (i in 0 until outH * outW) {
                singleChannel[i] = probmap[i * outC]
            }
            Outputs(singleChannel, outW, outH)
        } else {
            Outputs(probmap, outW, outH)
        }
    }

    @Synchronized
    fun runSegmentationAndReturn(bitmap: Bitmap): SegmentationResult? {
        return try {
            val startTime = SystemClock.uptimeMillis()

            val imageProcessor =
                ImageProcessor.Builder()
                    .add(ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
                    .add(NormalizeOp(127.5f, 127.5f))
                    .build()

            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bitmap)
            val processedImage = imageProcessor.process(tensorImage)

            val outputs = model.process(processedImage.tensorBuffer)
            val out0 = outputs.outputFeature0AsTensorBuffer
            val out1 = outputs.outputFeature1AsTensorBuffer
            val outputBuffer = if (out0.flatSize >= out1.flatSize) out0 else out1
            
            val shape = outputBuffer.shape
            val (outH, outW) = when (shape.size) {
                4 -> shape[1] to shape[2]
                3 -> shape[0] to shape[1]
                else -> 1 to 1
            }
            
            val segmentation = Segmentation(outputBuffer.floatArray, outW, outH)
            val inferenceTime = SystemClock.uptimeMillis() - startTime
            SegmentationResult(segmentation, inferenceTime)
        } catch (t: Throwable) {
            Log.e(TAG, "Segmentation failed", t)
            null
        }
    }

    /**
     * Run model and try to extract multi-channel heatmaps and an optional presence score.
     */
    fun runHeatmapsAndPresence(tensorImage: TensorImage): HeatmapsResult? {
        val startTime = SystemClock.uptimeMillis()

        return try {
            val outputs = model.process(tensorImage.tensorBuffer)
            val out0 = outputs.outputFeature0AsTensorBuffer
            val out1 = outputs.outputFeature1AsTensorBuffer

            // Heuristic: heatmap has significantly more elements than presence score
            val s0 = out0.flatSize
            val s1 = out1.flatSize
            val (heatmapBuffer, presenceBuffer) = if (s0 >= s1) out0 to out1 else out1 to out0

            val hShape = heatmapBuffer.shape // Expected [1, H, W, C]
            val (h, w, c) = when (hShape.size) {
                4 -> Triple(hShape[1], hShape[2], hShape[3])
                3 -> Triple(hShape[0], hShape[1], hShape[2])
                else -> {
                    Log.e(TAG, "Unexpected heatmap shape rank ${hShape.size}: ${hShape.contentToString()}")
                    return null
                }
            }

            val heatmapFloats = heatmapBuffer.floatArray
            val heatmaps = Array(c) { ch ->
                val arr = FloatArray(w * h)
                for (i in 0 until w * h) {
                    val idx = i * c + ch
                    if (idx < heatmapFloats.size) {
                        arr[i] = heatmapFloats[idx].coerceIn(0f, 1f)
                    }
                }
                arr
            }

            val presenceFloats = presenceBuffer.floatArray
            val presence = if (presenceFloats.isNotEmpty()) presenceFloats[0] else 0f

            val inferenceTime = SystemClock.uptimeMillis() - startTime
            HeatmapsResult(heatmaps, w, h, presence, inferenceTime)
        } catch (t: Throwable) {
            Log.e(TAG, "runHeatmapsAndPresence failed", t)
            null
        }
    }

    @Synchronized
    fun runHeatmapsAndPresence(bitmap: Bitmap): HeatmapsResult? {
        return try {
            val imageProcessor =
                ImageProcessor.Builder()
                    .add(ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
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

    override fun close() {
        model.close()
    }

    companion object {
        private const val TAG = "DocCornerTFLiteRunner"

        @Volatile
        private var instance: DocCornerTFLiteRunner? = null
        private val LOCK = Any()
        private val DEFAULT_EXECUTOR: Executor = Executors.newSingleThreadExecutor()

        fun getInstance(context: Context): DocCornerTFLiteRunner {
            return instance ?: synchronized(LOCK) {
                instance ?: DocCornerTFLiteRunner(context.applicationContext).also { instance = it }
            }
        }

        fun getInstanceAsync(context: Context, executor: Executor = DEFAULT_EXECUTOR): CompletableFuture<DocCornerTFLiteRunner> {
            return CompletableFuture.supplyAsync({
                getInstance(context)
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
    }
}
