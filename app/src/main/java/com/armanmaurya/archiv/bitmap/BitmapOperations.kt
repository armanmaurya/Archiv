package com.armanmaurya.archiv.bitmap

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.createBitmap

internal fun rotateBitmapQuarterTurns(bitmap: Bitmap, turns: Int): Bitmap {
    val normalized = ((turns % 4) + 4) % 4
    if (normalized == 0) return bitmap

    val matrix = Matrix().apply {
        postRotate(90f * normalized)
    }

    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true
    )
}

internal fun applyBitmapFilter(bitmap: Bitmap, mode: Int): Bitmap {
    if (mode == FilterMode.NONE) return bitmap

    val output = createBitmap(bitmap.width, bitmap.height)

    val canvas = AndroidCanvas(output)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = when (mode) {
            FilterMode.BW -> ColorMatrixColorFilter(
                ColorMatrix().apply { setSaturation(0f) }
            )

            FilterMode.SEPIA -> ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f, 0f,
                        0.349f, 0.686f, 0.168f, 0f, 0f,
                        0.272f, 0.534f, 0.131f, 0f, 0f,
                        0f,     0f,     0f,     1f, 0f
                    )
                )
            )

            else -> null
        }
    }

    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return output
}
