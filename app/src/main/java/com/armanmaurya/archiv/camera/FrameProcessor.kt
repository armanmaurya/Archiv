package com.armanmaurya.archiv.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import androidx.camera.core.ImageProxy
import com.armanmaurya.archiv.ml.corners.DocQuadDetector
import com.armanmaurya.archiv.ml.corners.OpenCVCornerDetector
import com.armanmaurya.archiv.ml.docquad.DocQuadOrtRunner
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class FrameProcessor {

    private var docQuadDetector: DocQuadDetector? = null
    private val openCVDetector = OpenCVCornerDetector()

    // Temporal smoothing: keeps last N detected corner sets and averages them
    private val cornerHistory = ArrayDeque<List<PointF>>(HISTORY_SIZE)

    companion object {
        private const val HISTORY_SIZE = 1
        private const val SMOOTH_ALPHA = 0.05f  // minimal smoothing
        private const val TAG = "FrameProcessor"
    }

    fun initialize(context: Context) {
        if (docQuadDetector == null) {
            try {
                val runner = DocQuadOrtRunner.getInstance(context, DocQuadDetector.DEFAULT_MODEL_ASSET_PATH)
                docQuadDetector = DocQuadDetector(runner)
                Log.d(TAG, "DocQuadDetector initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize DocQuadDetector", e)
            }
        }
    }

    fun processFrame(imageProxy: ImageProxy, context: Context): Triple<List<PointF>?, Float?, Bitmap?>? {
        // Lazy-initialize detector on first frame (not on compose creation)
        if (docQuadDetector == null) {
            try {
                val runner = DocQuadOrtRunner.getInstance(context, DocQuadDetector.DEFAULT_MODEL_ASSET_PATH)
                docQuadDetector = DocQuadDetector(runner)
                Log.d(TAG, "DocQuadDetector initialized on first frame")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize DocQuadDetector", e)
                return null
            }
        }

        val gray = imageProxyToMat(imageProxy) ?: return null
        val w = gray.width()
        val h = gray.height()
        val aspect = w.toFloat() / h.toFloat()

        // 1. Try DocQuadDetector (ML-based) - Primary
        val detector = docQuadDetector
        if (detector != null) {
            val bitmap = Mat(h, w, CvType.CV_8UC1)
            gray.copyTo(bitmap)
            val rgbBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(bitmap, rgbBitmap)
            bitmap.release()

            val result = detector.detect(rgbBitmap, context)
            rgbBitmap.recycle()

            if (result.success && result.cornersOriginalTLTRBRBL != null) {
                val corners = result.cornersOriginalTLTRBRBL.map { PointF(it[0].toFloat(), it[1].toFloat()) }
                val normalized = corners.map { PointF(it.x / w.toFloat(), it.y / h.toFloat()) }
                val smoothed = addToHistoryAndSmooth(normalized)
                gray.release()
                return Triple(smoothed, aspect, null)
            }
        }

        /*
        // 2. OpenCV-based detection (Disabled)
        val openCvResult = openCVDetector.detectMat(gray)
        if (openCvResult.success && openCvResult.cornersOriginalTLTRBRBL != null) {
            val corners = openCvResult.cornersOriginalTLTRBRBL.map { PointF(it[0].toFloat(), it[1].toFloat()) }
            val normalized = corners.map { PointF(it.x / w.toFloat(), it.y / h.toFloat()) }
            val smoothed = addToHistoryAndSmooth(normalized)
            gray.release()
            return Triple(smoothed, aspect, null)
        }
        */

        gray.release()
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

    private fun imageProxyToMat(imageProxy: ImageProxy): Mat? {
        val yBuffer = imageProxy.planes[0].buffer
        val ySize = yBuffer.remaining()
        if (ySize <= 0) return null

        val yArray = ByteArray(ySize)
        yBuffer.get(yArray)

        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = imageProxy.planes[0].rowStride

        val mat = Mat(height, width, CvType.CV_8UC1)
        if (rowStride == width) {
            mat.put(0, 0, yArray)
        } else {
            val validData = ByteArray(width * height)
            for (y in 0 until height) {
                val pos = y * rowStride
                val lengthToCopy = minOf(width, yArray.size - pos)
                if (lengthToCopy > 0) {
                    System.arraycopy(yArray, pos, validData, y * width, lengthToCopy)
                }
            }
            mat.put(0, 0, validData)
        }
        return rotateMat(mat, imageProxy.imageInfo.rotationDegrees)
    }

    private fun rotateMat(src: Mat, rotation: Int): Mat {
        if (rotation == 0) return src
        val rotated = Mat()
        when (rotation) {
            90 -> Core.rotate(src, rotated, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(src, rotated, Core.ROTATE_180)
            270 -> Core.rotate(src, rotated, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> return src
        }
        src.release()
        return rotated
    }

    private fun dist(a: PointF, b: PointF): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

