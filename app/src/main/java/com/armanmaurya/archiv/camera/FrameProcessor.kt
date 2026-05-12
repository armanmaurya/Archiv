package com.armanmaurya.archiv.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import androidx.camera.core.ImageProxy
import com.armanmaurya.archiv.ml.corners.DocCornerDetector
import com.armanmaurya.archiv.ml.corners.DocCornerTFLiteRunner
import org.opencv.core.Core
import java.nio.ByteBuffer

class FrameProcessor {

    private var docCornerDetector: DocCornerDetector? = null
    private var liveStabilizer = QuadStabilizer()

    // Temporal smoothing: keeps last N detected corner sets and averages them
    private val cornerHistory = ArrayDeque<List<PointF>>(HISTORY_SIZE)

    companion object {
        private const val HISTORY_SIZE = 1
        private const val SMOOTH_ALPHA = 0.05f  // minimal smoothing
        private const val LIVE_STABLE_FRAMES_REQUIRED = 3
        private const val TAG = "FrameProcessor"
    }

    fun initialize(context: Context) {
        if (docCornerDetector == null) {
            try {
                val runner = DocCornerTFLiteRunner.getInstance(context, DocCornerDetector.DEFAULT_MODEL_ASSET_PATH)
                docCornerDetector = DocCornerDetector(runner)
                Log.d(TAG, "DocCornerDetector initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize DocCornerDetector", e)
            }
        }
    }

    fun processFrame(imageProxy: ImageProxy, context: Context): Triple<List<PointF>?, Float?, Bitmap?>? {
        // Lazy-initialize detector on first frame (not on compose creation)
        if (docCornerDetector == null) {
            try {
                val runner = DocCornerTFLiteRunner.getInstance(context, DocCornerDetector.DEFAULT_MODEL_ASSET_PATH)
                docCornerDetector = DocCornerDetector(runner)
                Log.d(TAG, "DocCornerDetector initialized on first frame")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize DocCornerDetector", e)
                return null
            }
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val rgbaBitmap = imageProxyToBitmap(imageProxy) ?: return null
        val w = rgbaBitmap.width
        val h = rgbaBitmap.height
        val aspect = if (rotationDegrees % 180 == 0) w.toFloat() / h.toFloat() else h.toFloat() / w.toFloat()

        // Run TFLite detector
        val detector = docCornerDetector
        if (detector != null) {
            val result = detector.detect(rgbaBitmap, context, true)
            rgbaBitmap.recycle()

            if (result.success && result.cornersOriginalTLTRBRBL != null) {
                val corners = result.cornersOriginalTLTRBRBL.map { PointF(it[0].toFloat(), it[1].toFloat()) }
                val rotatedCorners = rotateCornersClockwise(corners, rotationDegrees, w, h)
                val normalized = normalizeCorners(rotatedCorners, rotationDegrees, w, h)
                val smoothed = addToHistoryAndSmooth(normalized)
                val stable = liveStabilizer.update(smoothed)
                if (stable == null) {
                    Log.d(TAG, "Document detected but not yet stable")
                    return Triple(null, aspect, null)
                }
                return Triple(stable, aspect, null)
            }
        }

        rgbaBitmap.recycle()
        Log.d(TAG, "No document detected")
        if (cornerHistory.isNotEmpty()) {
            cornerHistory.removeFirstOrNull()
        }
        return Triple(null, aspect, null)
    }

    // ── Corner history + exponential smoothing ──────────────────────────────

    private fun addToHistoryAndSmooth(corners: List<PointF>): List<PointF> {
        if (cornerHistory.size >= HISTORY_SIZE) {
            cornerHistory.removeFirst()
        }
        cornerHistory.addLast(corners)

        if (cornerHistory.size == 1) return corners

        // Exponential moving average over history
        val base = cornerHistory.first()
        return base.mapIndexed { i, _ ->
            var x = 0f
            var y = 0f
            var weight = 1f
            var totalWeight = 0f
            for (frame in cornerHistory) {
                x += frame[i].x * weight
                y += frame[i].y * weight
                totalWeight += weight
                weight *= (1f + SMOOTH_ALPHA)
            }
            PointF(x / totalWeight, y / totalWeight)
        }
    }

    fun resetHistory() {
        cornerHistory.clear()
        liveStabilizer = QuadStabilizer()
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    // Kept for public use (e.g. overlay drawing)
    fun sortCornersClockwise(points: List<PointF>): List<PointF> {
        if (points.size != 4) return points
        val sorted = points.sortedBy { it.x + it.y }
        val tl = sorted[0]
        val br = sorted[3]
        val remaining = listOf(sorted[1], sorted[2])
        val tr = remaining.maxByOrNull { it.x - it.y } ?: return points
        val bl = remaining.minByOrNull { it.x - it.y } ?: return points
        return listOf(tl, tr, br, bl)
    }

    // ── Image conversion ─────────────────────────────────────────────────────

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val width = imageProxy.width
        val height = imageProxy.height
        if (width <= 0 || height <= 0) return null

        val planeBuffer = imageProxy.planes.firstOrNull()?.buffer ?: return null
        planeBuffer.rewind()

        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            if (planeBuffer.remaining() < 4) return null
            val r = planeBuffer.get().toInt() and 0xFF
            val g = planeBuffer.get().toInt() and 0xFF
            val b = planeBuffer.get().toInt() and 0xFF
            val a = planeBuffer.get().toInt() and 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }

        return bitmap
    }

    private fun rotateCornersClockwise(points: List<PointF>, rotationDegrees: Int, width: Int, height: Int): List<PointF> {
        if (points.size != 4) return points
        return when (((rotationDegrees % 360) + 360) % 360) {
            90 -> points.map { PointF(height - it.y, it.x) }
            180 -> points.map { PointF(width - it.x, height - it.y) }
            270 -> points.map { PointF(it.y, width - it.x) }
            else -> points
        }
    }

    private fun normalizeCorners(points: List<PointF>, rotationDegrees: Int, width: Int, height: Int): List<PointF> {
        if (points.size != 4) return points
        val rotatedWidth = if (rotationDegrees % 180 == 0) width else height
        val rotatedHeight = if (rotationDegrees % 180 == 0) height else width
        return points.map {
            PointF(
                it.x / rotatedWidth.toFloat(),
                it.y / rotatedHeight.toFloat()
            )
        }
    }

    private class QuadStabilizer {
        private var stableCount = 0
        private var lastRawQuad: List<PointF>? = null

        fun update(rawQuad: List<PointF>?): List<PointF>? {
            val previousQuad = lastRawQuad
            lastRawQuad = rawQuad

            if (rawQuad == null) {
                stableCount = 0
                return null
            }

            if (previousQuad == null) {
                stableCount = 1
                return null
            }

            val dist = previousQuad.maxOf { previous ->
                rawQuad.minOf { current ->
                    val dx = (previous.x - current.x).toDouble()
                    val dy = (previous.y - current.y).toDouble()
                    kotlin.math.sqrt(dx * dx + dy * dy).toFloat()
                }
            }

            if (dist < 0.03f) {
                stableCount++
            } else {
                stableCount = 1
            }

            return if (stableCount >= LIVE_STABLE_FRAMES_REQUIRED) rawQuad else null
        }
    }

    private fun dist(a: PointF, b: PointF): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
