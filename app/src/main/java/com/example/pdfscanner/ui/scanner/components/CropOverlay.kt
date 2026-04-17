package com.example.pdfscanner.ui.scanner.components

import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun CropOverlay(
    bounds: List<PointF>,
    onChange: (List<PointF>) -> Unit
) {
    var localBounds by remember { mutableStateOf(bounds) }

    Box(modifier = Modifier.fillMaxSize()) {

        // Draw polygon
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (localBounds.size == 4) {
                val path = Path().apply {
                    moveTo(localBounds[0].x, localBounds[0].y)
                    lineTo(localBounds[1].x, localBounds[1].y)
                    lineTo(localBounds[2].x, localBounds[2].y)
                    lineTo(localBounds[3].x, localBounds[3].y)
                    close()
                }

                drawPath(path, color = Color.Blue, style = Stroke(width = 5f))
            }
        }

        // Drag points (simplified)
        localBounds.forEachIndexed { index, point ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(point.x.toInt(), point.y.toInt()) }
                    .size(24.dp)
                    .background(Color.Blue)
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()

                            val updated = localBounds.toMutableList()
                            updated[index] = PointF(
                                point.x + drag.x,
                                point.y + drag.y
                            )

                            localBounds = updated
                            onChange(updated)
                        }
                    }
            )
        }
    }
}