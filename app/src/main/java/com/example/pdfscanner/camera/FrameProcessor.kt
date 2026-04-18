package com.example.pdfscanner.camera

import android.graphics.PointF
import android.util.Log
import androidx.camera.core.ImageProxy
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class FrameProcessor {
    fun sortCornersClockwise(points: List<PointF>): List<PointF> {
        if (points.size != 4) return points

        val cx = points.map { it.x }.sum() / 4f
        val cy = points.map { it.y }.sum() / 4f

        return points.sortedWith { a, b ->
            val angleA = kotlin.math.atan2((a.y - cy).toDouble(), (a.x - cx).toDouble())
            val angleB = kotlin.math.atan2((b.y - cy).toDouble(), (b.x - cx).toDouble())
            angleA.compareTo(angleB)
        }
    }

    fun processFrame(imageProxy: ImageProxy): Pair<List<PointF>, Float>? {
        val srcMat = imageProxyToMat(imageProxy) ?: return null

        val processedWidth = srcMat.width()
        val processedHeight = srcMat.height()

        val blurMat = Mat()
        val tempMat = Mat()

        Imgproc.medianBlur(srcMat, tempMat, 5)
        Imgproc.bilateralFilter(tempMat, blurMat, 9, 75.0, 75.0)
        tempMat.release()

        val edgesMat = Mat()
        Imgproc.Canny(blurMat, edgesMat, 50.0, 150.0)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(edgesMat, edgesMat, Imgproc.MORPH_CLOSE, kernel)

        val corners = detectCornersFromHough(edgesMat, processedWidth, processedHeight)

        srcMat.release()
        blurMat.release()
        edgesMat.release()

        return corners?.let {
            Log.d("FrameProcessor", "Found document corners from Hough lines")
            val aspect = processedWidth.toFloat() / processedHeight.toFloat()
            val normalized =
                    it.map { point ->
                        PointF(
                                point.x / processedWidth.toFloat(),
                                point.y / processedHeight.toFloat()
                        )
                    }
            Pair(normalized, aspect)
        }
    }

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

    private fun detectCornersFromHough(
            edges: Mat,
            width: Int,
            height: Int
    ): List<PointF>? {
        val lines = Mat()
        try {
            Imgproc.HoughLinesP(
                    edges,
                    lines,
                    1.0,
                    Math.PI / 180.0,
                    80,
                    (width * 0.25).coerceAtLeast(60.0),
                    16.0
            )

            if (lines.rows() == 0) return null

            val horizontal = mutableListOf<DoubleArray>()
            val vertical = mutableListOf<DoubleArray>()

            for (i in 0 until lines.rows()) {
                val line = lines.get(i, 0) ?: continue
                if (line.size < 4) continue
                val dx = line[2] - line[0]
                val dy = line[3] - line[1]
                val angle = kotlin.math.abs(Math.toDegrees(Math.atan2(dy, dx)))
                if (angle < 25.0 || angle > 155.0) {
                    horizontal.add(line)
                } else if (angle > 65.0 && angle < 115.0) {
                    vertical.add(line)
                }
            }

            if (horizontal.size < 2 || vertical.size < 2) return null

            val top = horizontal.minByOrNull { (it[1] + it[3]) * 0.5 } ?: return null
            val bottom = horizontal.maxByOrNull { (it[1] + it[3]) * 0.5 } ?: return null
            val left = vertical.minByOrNull { (it[0] + it[2]) * 0.5 } ?: return null
            val right = vertical.maxByOrNull { (it[0] + it[2]) * 0.5 } ?: return null

            val topLeft = intersect(top, left)
            val topRight = intersect(top, right)
            val bottomRight = intersect(bottom, right)
            val bottomLeft = intersect(bottom, left)

            if (topLeft == null || topRight == null || bottomRight == null || bottomLeft == null) {
                return null
            }

            val quad = listOf(topLeft, topRight, bottomRight, bottomLeft)
            if (!isValidQuadrilateral(quad, width, height)) return null
            return quad
        } finally {
            lines.release()
        }
    }

    private fun intersect(lineA: DoubleArray, lineB: DoubleArray): PointF? {
        val p1 = Point(lineA[0], lineA[1])
        val p2 = Point(lineA[2], lineA[3])
        val p3 = Point(lineB[0], lineB[1])
        val p4 = Point(lineB[2], lineB[3])

        val a1 = p2.y - p1.y
        val b1 = p1.x - p2.x
        val c1 = a1 * p1.x + b1 * p1.y

        val a2 = p4.y - p3.y
        val b2 = p3.x - p4.x
        val c2 = a2 * p3.x + b2 * p3.y

        val det = a1 * b2 - a2 * b1
        if (kotlin.math.abs(det) < 1e-6) return null

        val x = (b2 * c1 - b1 * c2) / det
        val y = (a1 * c2 - a2 * c1) / det
        return PointF(x.toFloat(), y.toFloat())
    }

    private fun isValidQuadrilateral(points: List<PointF>, width: Int, height: Int): Boolean {
        if (points.size != 4) return false
        if (points.any { it.x.isNaN() || it.y.isNaN() }) return false

        val contour =
                MatOfPoint(
                        Point(points[0].x.toDouble(), points[0].y.toDouble()),
                        Point(points[1].x.toDouble(), points[1].y.toDouble()),
                        Point(points[2].x.toDouble(), points[2].y.toDouble()),
                        Point(points[3].x.toDouble(), points[3].y.toDouble())
                )
        if (!Imgproc.isContourConvex(contour)) return false

        val area = kotlin.math.abs(Imgproc.contourArea(contour))
        contour.release()
        if (area < width * height * 0.05) return false

        val centerX = points.map { it.x }.average().toFloat()
        val centerY = points.map { it.y }.average().toFloat()
        val ordered =
                points.sortedWith { a, b ->
                    val angleA =
                            kotlin.math.atan2(
                                    (a.y - centerY).toDouble(),
                                    (a.x - centerX).toDouble()
                            )
                    val angleB =
                            kotlin.math.atan2(
                                    (b.y - centerY).toDouble(),
                                    (b.x - centerX).toDouble()
                            )
                    angleA.compareTo(angleB)
                }
        val top = distance(ordered[0], ordered[1])
        val right = distance(ordered[1], ordered[2])
        val bottom = distance(ordered[2], ordered[3])
        val left = distance(ordered[3], ordered[0])
        val avgW = (top + bottom) / 2.0
        val avgH = (left + right) / 2.0
        if (avgW <= 1.0 || avgH <= 1.0) return false
        val ratio = avgW / avgH
        return ratio in 0.35..2.2
    }

    private fun distance(a: PointF, b: PointF): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
