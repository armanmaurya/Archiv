package com.example.pdfscanner.ui.scanner.components

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.pdfscanner.decodeScaledBitmap
import com.example.pdfscanner.image.rotateBitmapQuarterTurns
import com.example.pdfscanner.image.applyBitmapFilter
import com.example.pdfscanner.image.warpBitmapWithQuad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImageRenderer(
    uri: Uri,
    bounds: List<PointF>,
    rotation: Int,
    filter: Int
) {
    val context = LocalContext.current

    var baseBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var displayedBitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }

    val cacheKey = uri.toString()
    val processedCache = remember(uri) { mutableMapOf<String, Bitmap>() }

    // Load image
    LaunchedEffect(uri) {
        val bitmap = withContext(Dispatchers.IO) {
            decodeScaledBitmap(context, uri, 2200)
        }
        baseBitmap = bitmap
    }

    // Process image (rotation + filter + crop)
    LaunchedEffect(baseBitmap, bounds, rotation, filter) {
        val current = baseBitmap ?: return@LaunchedEffect

        val key = "$rotation-$filter"

        val processed = processedCache.getOrPut(key) {
            val rotated = rotateBitmapQuarterTurns(current, rotation)
            applyBitmapFilter(rotated, filter)
        }

        val warped = withContext(Dispatchers.Default) {
            warpBitmapWithQuad(processed, bounds)
        }

        displayedBitmap = (warped ?: processed).asImageBitmap()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        displayedBitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}