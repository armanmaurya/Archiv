package com.armanmaurya.archiv.ml.docquad

import ai.onnxruntime.*
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The DocQuadOrtRunner class provides functionality for running inference on an ONNX model.
 */
class DocQuadOrtRunner private constructor(context: Context, modelAssetPath: String) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val cacheDir = context.cacheDir
        val modelFile = copyAssetToCache(context, modelAssetPath, cacheDir)
        session = createSessionWithFallback(env, modelFile.absolutePath)
        Log.d(TAG, "Model loaded from ${modelFile.absolutePath}")
    }

    data class Outputs(val maskLogits: Array<Array<Array<FloatArray>>>, val cornerHeatmaps: Array<Array<Array<FloatArray>>>)

    fun run(inputNchw: FloatArray): Outputs {
        require(inputNchw.size == 3 * IN_H * IN_W) { "inputNchw must have length ${3 * IN_H * IN_W}" }

        val inputShape = longArrayOf(1, 3, IN_H.toLong(), IN_W.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(inputNchw), inputShape).use { input ->
            session.run(Collections.singletonMap("input", input)).use { results ->
                val maskLogits = getRequiredFloat4d(results, "mask_logits")
                val cornerHeatmaps = getRequiredFloat4d(results, "corner_heatmaps")
                return Outputs(maskLogits, cornerHeatmaps)
            }
        }
    }

    override fun close() {
        session.close()
    }

    companion object {
        private const val TAG = "DocQuadOrtRunner"
        const val IN_H = 256
        const val IN_W = 256
        const val OUT_H = 64
        const val OUT_W = 64

        @Volatile
        private var instance: DocQuadOrtRunner? = null
        private val LOCK = Any()
        private val DEFAULT_EXECUTOR: Executor = Executors.newSingleThreadExecutor()

        fun getInstance(context: Context, modelAssetPath: String): DocQuadOrtRunner {
            return instance ?: synchronized(LOCK) {
                instance ?: DocQuadOrtRunner(context, modelAssetPath).also { instance = it }
            }
        }

        fun getInstanceAsync(context: Context, modelAssetPath: String, executor: Executor = DEFAULT_EXECUTOR): CompletableFuture<DocQuadOrtRunner> {
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

        private fun createSessionWithFallback(env: OrtEnvironment, modelPath: String): OrtSession {
            return try {
                val opts = OrtSession.SessionOptions()
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                opts.setIntraOpNumThreads(maxOf(1, Runtime.getRuntime().availableProcessors() / 2))
                
                // XNNPACK is faster and doesn't have NNAPI initialization overhead
                try {
                    opts.addXnnpack(emptyMap())
                    Log.i(TAG, "XNNPACK EP enabled")
                } catch (t: Throwable) {
                    Log.i(TAG, "XNNPACK not available: ${t.message}")
                }
                
                // NNAPI disabled due to 4-second initialization overhead on some devices
                // If XNNPACK fails, CPU backend will be used
                
                env.createSession(modelPath, opts)
            } catch (e: Exception) {
                Log.w(TAG, "Session creation with accelerated EPs failed, falling back to CPU: ${e.message}")
                val opts = OrtSession.SessionOptions()
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                opts.setIntraOpNumThreads(maxOf(1, Runtime.getRuntime().availableProcessors() / 2))
                env.createSession(modelPath, opts)
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

        @Suppress("UNCHECKED_CAST")
        private fun getRequiredFloat4d(results: OrtSession.Result, outputName: String): Array<Array<Array<FloatArray>>> {
            val ov = results.get(outputName)
            if (ov.isEmpty) throw IllegalStateException("ONNX output missing: '$outputName'")
            val v = ov.get().value
            return v as? Array<Array<Array<FloatArray>>> ?: throw IllegalStateException("Output '$outputName' has unexpected type")
        }
    }
}
