package com.example.pdfscanner.bitmap

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

fun fullImageBounds(): List<PointF> = listOf(
    PointF(0f, 0f),
    PointF(1f, 0f),
    PointF(1f, 1f),
    PointF(0f, 1f)
)

fun orderCorners(points: List<PointF>): List<PointF> {
    if (points.size != 4) return points
    val topLeft = points.minByOrNull { it.x + it.y } ?: points[0]
    val bottomRight = points.maxByOrNull { it.x + it.y } ?: points[2]
    val topRight = points.minByOrNull { it.y - it.x } ?: points[1]
    val bottomLeft = points.maxByOrNull { it.y - it.x } ?: points[3]
    return listOf(topLeft, topRight, bottomRight, bottomLeft)
}

fun warpBitmapWithQuad(bitmap: Bitmap, normalizedCorners: List<PointF>): Bitmap? {
    if (normalizedCorners.size != 4) return null
    val ordered = orderCorners(normalizedCorners)
    val mapped = ordered.map { point ->
        PointF(
            point.x.coerceIn(0f, 1f) * bitmap.width.toFloat(),
            point.y.coerceIn(0f, 1f) * bitmap.height.toFloat()
        )
    }

    val targetWidth = maxOf(
        edgeDistance(mapped[2], mapped[3]),
        edgeDistance(mapped[1], mapped[0])
    ).toInt().coerceAtLeast(1)
    val targetHeight = maxOf(
        edgeDistance(mapped[1], mapped[2]),
        edgeDistance(mapped[0], mapped[3])
    ).toInt().coerceAtLeast(1)

    val source = Mat()
    val warped = Mat()
    val srcPoints = MatOfPoint2f(
        Point(mapped[0].x.toDouble(), mapped[0].y.toDouble()),
        Point(mapped[1].x.toDouble(), mapped[1].y.toDouble()),
        Point(mapped[2].x.toDouble(), mapped[2].y.toDouble()),
        Point(mapped[3].x.toDouble(), mapped[3].y.toDouble())
    )
    val dstPoints = MatOfPoint2f(
        Point(0.0, 0.0),
        Point((targetWidth - 1).toDouble(), 0.0),
        Point((targetWidth - 1).toDouble(), (targetHeight - 1).toDouble()),
        Point(0.0, (targetHeight - 1).toDouble())
    )
    return try {
        Utils.bitmapToMat(bitmap, source)
        val perspective = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
        Imgproc.warpPerspective(source, warped, perspective, Size(targetWidth.toDouble(), targetHeight.toDouble()))
        perspective.release()
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(warped, output)
        output
    } finally {
        source.release()
        warped.release()
        srcPoints.release()
        dstPoints.release()
    }
}

private fun edgeDistance(a: PointF, b: PointF): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}
