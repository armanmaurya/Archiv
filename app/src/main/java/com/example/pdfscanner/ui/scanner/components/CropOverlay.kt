package com.example.pdfscanner.ui.scanner.components

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun CropOverlay(
    bounds: List<PointF>,
    imageWidth: Int,
    imageHeight: Int,
    containerWidth: Int,
    containerHeight: Int,
    onChange: (List<PointF>) -> Unit
) {
    if (bounds.size != 4 || imageWidth <= 0 || imageHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) {
        return
    }

    val fitted = remember(containerWidth, containerHeight, imageWidth, imageHeight) {
        computeFittedRect(
            containerWidth = containerWidth.toFloat(),
            containerHeight = containerHeight.toFloat(),
            imageWidth = imageWidth.toFloat(),
            imageHeight = imageHeight.toFloat()
        )
    }

    val pointsOnScreen = remember(bounds, fitted) {
        bounds.map { point ->
            PointF(
                fitted.offsetX + point.x * fitted.width,
                fitted.offsetY + point.y * fitted.height
            )
        }
    }
    val latestBounds = rememberUpdatedState(bounds)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(pointsOnScreen[0].x, pointsOnScreen[0].y)
                lineTo(pointsOnScreen[1].x, pointsOnScreen[1].y)
                lineTo(pointsOnScreen[2].x, pointsOnScreen[2].y)
                lineTo(pointsOnScreen[3].x, pointsOnScreen[3].y)
                close()
            }
            drawPath(
                path = path,
                color = Color(0xFF2196F3).copy(alpha = 0.9f),
                style = Stroke(width = 6f)
            )
        }

        pointsOnScreen.forEachIndexed { index, point ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (point.x - 14.dp.toPx()).roundToInt(),
                            (point.y - 14.dp.toPx()).roundToInt()
                        )
                    }
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2196F3))
                    .pointerInput(index, fitted.width, fitted.height) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            if (fitted.width <= 1f || fitted.height <= 1f) {
                                return@detectDragGestures
                            }
                            val currentBounds = latestBounds.value
                            if (currentBounds.size != 4) {
                                return@detectDragGestures
                            }

                            val currentScreen = PointF(
                                fitted.offsetX + currentBounds[index].x * fitted.width,
                                fitted.offsetY + currentBounds[index].y * fitted.height
                            )
                            val nextScreenX = (currentScreen.x + drag.x)
                                .coerceIn(fitted.offsetX, fitted.offsetX + fitted.width)
                            val nextScreenY = (currentScreen.y + drag.y)
                                .coerceIn(fitted.offsetY, fitted.offsetY + fitted.height)

                            val nextNormalized = PointF(
                                ((nextScreenX - fitted.offsetX) / fitted.width).coerceIn(0f, 1f),
                                ((nextScreenY - fitted.offsetY) / fitted.height).coerceIn(0f, 1f)
                            )

                            val updated = currentBounds.toMutableList()
                            updated[index] = PointF(
                                nextNormalized.x,
                                nextNormalized.y
                            )
                            onChange(updated)
                        }
                    }
            )
        }
    }
}

@Composable
fun CropOverlay(
    bounds: List<PointF>,
    onChange: (List<PointF>) -> Unit
) {
    CropOverlay(
        bounds = bounds,
        imageWidth = 0,
        imageHeight = 0,
        containerWidth = 0,
        containerHeight = 0,
        onChange = onChange
    )
}

private data class FittedRect(
    val width: Float,
    val height: Float,
    val offsetX: Float,
    val offsetY: Float
)

private fun computeFittedRect(
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Float,
    imageHeight: Float
): FittedRect {
    if (containerWidth <= 0f || containerHeight <= 0f || imageWidth <= 0f || imageHeight <= 0f) {
        return FittedRect(0f, 0f, 0f, 0f)
    }
    val scale = minOf(containerWidth / imageWidth, containerHeight / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    return FittedRect(
        width = width,
        height = height,
        offsetX = (containerWidth - width) / 2f,
        offsetY = (containerHeight - height) / 2f
    )
}
