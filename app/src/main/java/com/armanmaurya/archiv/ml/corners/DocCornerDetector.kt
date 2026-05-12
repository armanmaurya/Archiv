package com.armanmaurya.archiv.ml.corners

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

class DocCornerDetector(private val runner: DocCornerTFLiteRunner) : CornerDetector {
    private val openCvFallback = OpenCVCornerDetector()

    companion object {
        private const val TAG = "DocCornerDetector"
        const val DEFAULT_MODEL_ASSET_PATH = "doccornernet_model_fp16.tflite"
    }

    override fun detect(src: Bitmap, ctx: Context, isLiveAnalysis: Boolean): DetectionResult {
        return try {
            val srcW = src.width
            val srcH = src.height
            if (srcW <= 0 || srcH <= 0) return DetectionResult.fail(Source.DOCQUAD)

            // Try to run heatmap-based model (4-channel corner heatmaps + presence)
            val heatmapsResult = runner.runHeatmapsAndPresence(src)
            if (heatmapsResult != null) {
                // If model predicts no document present, fail
                if (heatmapsResult.presence <= 0.5f) return DetectionResult.fail(Source.DOCQUAD)

                val hmW = heatmapsResult.width
                val hmH = heatmapsResult.height
                val hmChannels = heatmapsResult.heatmaps.size
                if (hmChannels < 4) return DetectionResult.fail(Source.DOCQUAD)

                // Extract corner per-channel by argmax
                val corners = mutableListOf<DoubleArray>()
                for (ch in 0 until 4) {
                    val map = heatmapsResult.heatmaps[ch]
                    var bestIdx = 0
                    var bestVal = Float.NEGATIVE_INFINITY
                    for (i in map.indices) {
                        val v = map[i]
                        if (v > bestVal) {
                            bestVal = v
                            bestIdx = i
                        }
                    }
                    val y = bestIdx / hmW
                    val x = bestIdx % hmW
                    val srcX = x.toDouble() * srcW / hmW
                    val srcY = y.toDouble() * srcH / hmH
                    corners.add(doubleArrayOf(srcX, srcY))
                }

                val sourceQuad = corners.toTypedArray()

                if (!isValidQuad(sourceQuad, srcW, srcH)) {
                    return DetectionResult.fail(Source.DOCQUAD)
                }

                return DetectionResult.successDebug(
                    Source.DOCQUAD,
                    sourceQuad,
                    DEFAULT_MODEL_ASSET_PATH,
                    null,
                    null
                )
            }

            // If heatmap inference is unavailable, fallback to classic OpenCV corners.
            openCvFallback.detect(src, ctx, isLiveAnalysis)
        } catch (t: Throwable) {
            Log.e(TAG, "Detection failed", t)
            DetectionResult.fail(Source.DOCQUAD)
        }
    }

    private fun isValidQuad(c: Array<DoubleArray>?, w: Int, h: Int): Boolean {
        if (c == null || c.size != 4) return false
        for (i in 0..3) {
            if (c[i].size != 2) return false
            val x = c[i][0]
            val y = c[i][1]
            if (!x.isFinite() || !y.isFinite()) return false
            if (x < -w * 0.25 || x > w * 1.25) return false
            if (y < -h * 0.25 || y > h * 1.25) return false
        }
        return true
    }
}
