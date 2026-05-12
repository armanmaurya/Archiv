package com.armanmaurya.archiv.ml.corners

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class OpenCVCornerDetector : CornerDetector {
    override fun detect(src: Bitmap, ctx: Context, isLiveAnalysis: Boolean): DetectionResult {
        val mat = Mat()
        Utils.bitmapToMat(src, mat)
        
        val gray = Mat()
        if (mat.channels() > 1) {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        } else {
            mat.copyTo(gray)
        }
        
        val result = detectMat(gray)
        
        mat.release()
        gray.release()
        return result
    }

    fun detectMat(gray: Mat): DetectionResult {
        val w = gray.width()
        val h = gray.height()
        
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(blurred, edges, 75.0, 200.0)

        val dilated = Mat()
        Imgproc.dilate(edges, dilated, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(dilated, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val bestQuad = findBestQuadrilateral(contours, w, h)

        // Cleanup Mat objects
        blurred.release()
        edges.release()
        dilated.release()
        hierarchy.release()
        contours.forEach { it.release() }

        return if (bestQuad != null) {
            val cornersArray = Array(4) { i ->
                doubleArrayOf(bestQuad[i].x.toDouble(), bestQuad[i].y.toDouble())
            }
            DetectionResult.success(Source.OPENCV, cornersArray)
        } else {
            DetectionResult.fail(Source.OPENCV)
        }
    }

    private fun findBestQuadrilateral(
        contours: List<MatOfPoint>,
        width: Int,
        height: Int
    ): List<PointF>? {
        val imageArea = width.toDouble() * height.toDouble()
        val candidates = mutableListOf<Pair<List<PointF>, Double>>() // (corners, area)

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)

            // Too small: skip noise
            if (area < imageArea * 0.04) continue

            // Too large: this is probably the frame border itself — skip it
            if (area > imageArea * 0.97) continue

            // Approximate contour to polygon
            val contour2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()

            // Try a few epsilon values to find one that gives exactly 4 points
            var quad4: List<PointF>? = null
            for (epsFactor in listOf(0.02, 0.03, 0.04, 0.05, 0.06)) {
                Imgproc.approxPolyDP(contour2f, approx, epsFactor * peri, true)
                if (approx.total() == 4L) {
                    quad4 = approx.toArray().map { PointF(it.x.toFloat(), it.y.toFloat()) }
                    break
                }
            }
            contour2f.release()
            approx.release()

            if (quad4 == null) continue

            val ordered = orderCornersTLTRBRBL(quad4) ?: continue

            if (!isValidQuad(ordered, width, height, area, imageArea)) continue

            // Store with area as score
            candidates.add(Pair(ordered, area))
        }

        if (candidates.isEmpty()) return null

        // Return largest by area
        return candidates.maxByOrNull { it.second }?.first
    }

    private fun orderCornersTLTRBRBL(pts: List<PointF>): List<PointF>? {
        if (pts.size != 4) return null
        val sorted = pts.sortedBy { it.x + it.y }
        val tl = sorted[0]
        val br = sorted[3]
        val remaining = listOf(sorted[1], sorted[2])
        val tr = remaining.maxByOrNull { it.x - it.y } ?: return null
        val bl = remaining.minByOrNull { it.x - it.y } ?: return null
        return listOf(tl, tr, br, bl)
    }

    private fun isValidQuad(
        pts: List<PointF>,
        width: Int,
        height: Int,
        contourArea: Double,
        imageArea: Double
    ): Boolean {
        if (pts.size != 4) return false
        if (pts.any { it.x.isNaN() || it.y.isNaN() }) return false

        // All corners must be inside the image with a small margin
        val margin = 2f
        if (pts.any { it.x < margin || it.y < margin || it.x > width - margin || it.y > height - margin }) return false

        // Must be convex
        val mat = MatOfPoint(*pts.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
        val convex = Imgproc.isContourConvex(mat)
        mat.release()
        if (!convex) return false

        // Minimum area 4% of image
        if (contourArea < imageArea * 0.04) return false

        // Check side lengths are reasonable
        val (tl, tr, br, bl) = pts
        val top = dist(tl, tr)
        val right = dist(tr, br)
        val bottom = dist(bl, br)
        val left = dist(tl, bl)

        if (top < 40 || right < 40 || bottom < 40 || left < 40) return false

        // Aspect ratio sanity check
        val avgW = (top + bottom) / 2.0
        val avgH = (left + right) / 2.0
        val ratio = if (avgH > 0) avgW / avgH else 0.0
        return ratio in 0.1..10.0
    }

    private fun dist(a: PointF, b: PointF): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
