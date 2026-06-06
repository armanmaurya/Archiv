package com.armanmaurya.archiv.bitmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
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

    val srcPoints = floatArrayOf(
        mapped[0].x, mapped[0].y,
        mapped[1].x, mapped[1].y,
        mapped[2].x, mapped[2].y,
        mapped[3].x, mapped[3].y
    )
    val dstPoints = floatArrayOf(
        0f, 0f,
        targetWidth.toFloat(), 0f,
        targetWidth.toFloat(), targetHeight.toFloat(),
        0f, targetHeight.toFloat()
    )

    val matrix = Matrix()
    val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)
    if (!success) return null

    val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(bitmap, matrix, paint)

    return output
}

private fun edgeDistance(a: PointF, b: PointF): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}
