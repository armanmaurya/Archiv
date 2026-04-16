package com.example.pdfscanner.ui.scanner

import android.graphics.PointF
import android.net.Uri
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.example.pdfscanner.decodeScaledBitmap
import com.example.pdfscanner.image.BitmapMemoryCache
import com.example.pdfscanner.image.fullImageBounds
import com.example.pdfscanner.image.warpBitmapWithQuad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScannerScreen(
        hasCameraPermission: Boolean,
        capturedPageUris: List<Uri>,
        detectedCorners: List<PointF>?,
        imageAspectRatio: Float,
        isBusy: Boolean,
        errorMessage: String?,
        onCameraPreviewReady: (PreviewView) -> Unit,
        onRequestPermission: () -> Unit,
        onCapturePage: () -> Unit,
        onSavePdf: () -> Unit,
        onOpenGallery: (Int) -> Unit,
        onClearPages: () -> Unit,
        onDismissError: () -> Unit,
        onReorderPages: (fromIndex: Int, toIndex: Int) -> Unit,
        onRemovePage: (index: Int) -> Unit,
        scrollToIndexHint: Int? = null,
        onScrollHintConsumed: () -> Unit = {},
        cropBoundsByUri: Map<String, List<PointF>> = emptyMap(),
        sharedTransitionScope: SharedTransitionScope? = null,
        animatedVisibilityScope: AnimatedVisibilityScope? = null,
        sharedElementKeyForUri: (Uri) -> String = { uri -> "page-$uri" }
) {
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Camera permission is required.")
                Button(onClick = onRequestPermission) { Text("Grant permission") }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            CameraPreview(
                    onCameraPreviewReady = onCameraPreviewReady,
                    modifier =
                            Modifier.fillMaxSize()
                                    .clip(
                                            RoundedCornerShape(
                                                    bottomStart = 24.dp,
                                                    bottomEnd = 24.dp
                                            )
                                    )
            )

            // Detected Outline Layer
            detectedCorners?.let { corners ->
                if (corners.size == 4) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val targetRatio = size.width / size.height

                        val scaledWidth: Float
                        val scaledHeight: Float
                        val offsetX: Float
                        val offsetY: Float

                        if (imageAspectRatio > targetRatio) {
                            // Image is wider than target area (cropped left/right)
                            scaledHeight = size.height
                            scaledWidth = size.height * imageAspectRatio
                            offsetX = (size.width - scaledWidth) / 2f
                            offsetY = 0f
                        } else {
                            // Image is taller than target area (cropped top/bottom)
                            scaledWidth = size.width
                            scaledHeight = size.width / imageAspectRatio
                            offsetX = 0f
                            offsetY = (size.height - scaledHeight) / 2f
                        }

                        val path = Path()
                        path.moveTo(
                                corners[0].x * scaledWidth + offsetX,
                                corners[0].y * scaledHeight + offsetY
                        )
                        path.lineTo(
                                corners[1].x * scaledWidth + offsetX,
                                corners[1].y * scaledHeight + offsetY
                        )
                        path.lineTo(
                                corners[2].x * scaledWidth + offsetX,
                                corners[2].y * scaledHeight + offsetY
                        )
                        path.lineTo(
                                corners[3].x * scaledWidth + offsetX,
                                corners[3].y * scaledHeight + offsetY
                        )
                        path.close()

                        drawPath(
                                path = path,
                                color = Color(0xFF2196F3).copy(alpha = 0.9f),
                                style = Stroke(width = 8f)
                        )
                    }
                }
            }

            // Top error message
            errorMessage?.let { message ->
                Surface(
                        modifier =
                                Modifier.align(Alignment.TopCenter)
                                        .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                                text = message,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        IconButton(onClick = onDismissError) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss error")
                        }
                    }
                }
            }

            // Searching Pill
            Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                    )
                    Text(text = "Searching for documents", fontWeight = FontWeight.Medium)
                }
            }
        }

        // Bottom controls
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Left gallery button — always opens the image selection menu
            Box(
                    modifier =
                            Modifier.size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF005A8D))
                                    .clickable { onOpenGallery(-1) },
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Open Gallery",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                )
            }

            // Center shutter button
            Box(
                    modifier =
                            Modifier.size(80.dp)
                                    .border(3.dp, Color.White, CircleShape)
                                    .padding(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable(enabled = !isBusy) { onCapturePage() }
            )

            // Right action button
            Box(
                    modifier =
                            Modifier.size(56.dp).clickable(
                                            enabled = !isBusy && capturedPageUris.isNotEmpty()
                                    ) { onSavePdf() },
                    contentAlignment = Alignment.Center
            ) {
                // A custom shape similar to the screenshot
                Icon(
                        Icons.Default.Check,
                        contentDescription = "Save PDF",
                        tint = if (capturedPageUris.isNotEmpty()) Color(0xFF67B5E8) else Color.Gray,
                        modifier = Modifier.size(40.dp)
                )
            }
        } // Row

        // Thumbnail list with drag-to-reorder at the absolute bottom
        val lazyListState = rememberLazyListState()
        var draggingIndex by remember { mutableStateOf<Int?>(null) }
        var dragOffsetX by remember { mutableFloatStateOf(0f) }
        var isSettling by remember { mutableStateOf(false) }
        val currentOnReorderPages by rememberUpdatedState(onReorderPages)

        LaunchedEffect(scrollToIndexHint, capturedPageUris.size) {
            val target = scrollToIndexHint
            if (target != null && target in capturedPageUris.indices) {
                lazyListState.scrollToItem(target)
                onScrollHintConsumed()
            }
        }

        // Auto-scroll to the last item whenever a new page is captured
        LaunchedEffect(capturedPageUris.size) {
            if (capturedPageUris.isNotEmpty()) {
                lazyListState.animateScrollToItem(capturedPageUris.size - 1)
            }
        }

        // Animate dragOffsetX → 0 on release, then clear dragging state
        LaunchedEffect(isSettling) {
            if (isSettling) {
                animate(
                        initialValue = dragOffsetX,
                        targetValue = 0f,
                        animationSpec =
                                spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                )
                ) { value, _ -> dragOffsetX = value }
                draggingIndex = null
                isSettling = false
            }
        }

        LazyRow(
                state = lazyListState,
                modifier =
                        Modifier.fillMaxWidth()
                                .height(
                                        96.dp
                                ) // Fixed height to prevent UI jumps (80dp height + 16dp total
                                // padding)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                            onDragStart = { offset ->
                                                isSettling = false // cancel any ongoing settle
                                                val item =
                                                        lazyListState.layoutInfo.visibleItemsInfo
                                                                .firstOrNull { info ->
                                                                    offset.x >= info.offset &&
                                                                            offset.x <=
                                                                                    info.offset +
                                                                                            info.size
                                                                }
                                                draggingIndex = item?.index
                                                dragOffsetX = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetX += dragAmount.x
                                                val currentIndex =
                                                        draggingIndex
                                                                ?: return@detectDragGesturesAfterLongPress
                                                val visibleItems =
                                                        lazyListState.layoutInfo.visibleItemsInfo
                                                val draggingItemInfo =
                                                        visibleItems.firstOrNull {
                                                            it.index == currentIndex
                                                        }
                                                                ?: return@detectDragGesturesAfterLongPress
                                                val draggingCenter =
                                                        draggingItemInfo.offset +
                                                                draggingItemInfo.size / 2 +
                                                                dragOffsetX.toInt()
                                                val target =
                                                        visibleItems.firstOrNull { info ->
                                                            info.index != currentIndex &&
                                                                    draggingCenter >= info.offset &&
                                                                    draggingCenter <=
                                                                            info.offset + info.size
                                                        }
                                                if (target != null) {
                                                    // Preserve visual position: shift offset by how
                                                    // far the item physically moved
                                                    dragOffsetX +=
                                                            (draggingItemInfo.offset -
                                                                            target.offset)
                                                                    .toFloat()
                                                    currentOnReorderPages(
                                                            currentIndex,
                                                            target.index
                                                    )
                                                    draggingIndex = target.index
                                                }
                                            },
                                            onDragEnd = { isSettling = true },
                                            onDragCancel = { isSettling = true }
                                    )
                                },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(capturedPageUris, key = { it.toString() }) { uri ->
                val pageIndex = capturedPageUris.indexOf(uri)
                val isDragging = draggingIndex == pageIndex
                var imageBitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
                var hasError by remember(uri) { mutableStateOf(false) }
                val context = LocalContext.current
                val cacheKey = uri.toString()
                val bounds = cropBoundsByUri[cacheKey] ?: fullImageBounds()
                val thumbnailCacheKey = "$cacheKey-thumb-${bounds.hashCode()}"

                LaunchedEffect(uri, bounds) {
                    BitmapMemoryCache.getThumbnail(thumbnailCacheKey)?.let {
                        imageBitmap = it.asImageBitmap()
                        return@LaunchedEffect
                    }
                    BitmapMemoryCache.getPreview(cacheKey)?.let {
                        val warped = warpBitmapWithQuad(it, bounds)
                        val preview = warped ?: it
                        BitmapMemoryCache.putThumbnail(thumbnailCacheKey, preview)
                        imageBitmap = preview.asImageBitmap()
                        return@LaunchedEffect
                    }

                    val bitmap =
                            withContext(Dispatchers.IO) {
                                try {
                                    Log.d("ThumbnailLoading", "Attempting to load URI: $uri")
                                    Log.d(
                                            "ThumbnailLoading",
                                            "URI scheme: ${uri.scheme}, path: ${uri.path}"
                                    )

                                    // Try content resolver first (for file:// URIs)
                                    var decodedBitmap = decodeScaledBitmap(context, uri, 400)

                                    // If that failed and it's a file:// URI, try opening as file
                                    // path
                                    if (decodedBitmap == null && uri.scheme == "file") {
                                        val filePath = uri.path
                                        if (filePath != null) {
                                            Log.d(
                                                    "ThumbnailLoading",
                                                    "Trying direct file path: $filePath"
                                            )
                                            val file = java.io.File(filePath)
                                            if (file.exists()) {
                                                Log.d(
                                                        "ThumbnailLoading",
                                                        "File exists: true, size: ${file.length()} bytes"
                                                )
                                                // Read bitmap directly
                                                decodedBitmap =
                                                        android.graphics.BitmapFactory.decodeFile(
                                                                filePath
                                                        )
                                            } else {
                                                Log.e(
                                                        "ThumbnailLoading",
                                                        "File does not exist: $filePath"
                                                )
                                            }
                                        }
                                    }

                                    if (decodedBitmap != null) {
                                        Log.d(
                                                "ThumbnailLoading",
                                                "Successfully decoded bitmap: ${decodedBitmap.width}x${decodedBitmap.height}"
                                        )
                                    }
                                    decodedBitmap
                                } catch (e: Exception) {
                                    Log.e(
                                            "ThumbnailLoading",
                                            "Exception loading URI $uri: ${e.message}",
                                            e
                                    )
                                    null
                                }
                            }

                    if (bitmap != null) {
                        val warped = warpBitmapWithQuad(bitmap, bounds)
                        val preview = warped ?: bitmap
                        BitmapMemoryCache.putThumbnail(thumbnailCacheKey, preview)
                        imageBitmap = preview.asImageBitmap()
                    } else {
                        Log.e("ThumbnailLoading", "Failed to decode bitmap for URI: $uri")
                        hasError = true
                    }
                }

                Box(
                        modifier =
                                Modifier.then(if (!isDragging) Modifier.animateItem() else Modifier)
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .height(80.dp)
                                        .width(60.dp)
                                        .graphicsLayer {
                                            translationX = if (isDragging) dragOffsetX else 0f
                                            scaleX = if (isDragging) 1.08f else 1f
                                            scaleY = if (isDragging) 1.08f else 1f
                                            alpha = if (isDragging) 0.85f else 1f
                                            shadowElevation = if (isDragging) 8f else 0f
                                        }
                                        .border(
                                                width = 2.dp,
                                                color =
                                                        if (isDragging) Color.White
                                                        else MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(8.dp)
                                        )
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.DarkGray)
                                        .clickable(enabled = !hasError && !isDragging) {
                                            if (pageIndex >= 0) {
                                                onOpenGallery(pageIndex)
                                            }
                                        }
                ) {
                    if (hasError) {
                        Icon(
                                Icons.Default.Close,
                                contentDescription = "Error loading",
                                tint = Color.Red,
                                modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        imageBitmap?.let {
                            val sharedImageModifier =
                                    if (sharedTransitionScope != null &&
                                                    animatedVisibilityScope != null
                                    ) {
                                        with(sharedTransitionScope) {
                                            Modifier.sharedElement(
                                                    state =
                                                            rememberSharedContentState(
                                                                    sharedElementKeyForUri(uri)
                                                            ),
                                                    animatedVisibilityScope =
                                                            animatedVisibilityScope
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                            Image(
                                    bitmap = it,
                                    contentDescription = "Captured Page",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().then(sharedImageModifier)
                            )
                        }
                    }
                    // Remove button
                    Box(
                            modifier =
                                    Modifier.align(Alignment.TopEnd)
                                            .padding(3.dp)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.7f))
                                            .clickable {
                                                if (pageIndex >= 0) pendingDeleteIndex = pageIndex
                                            },
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove page",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
    } // Column (main background container)

    // Overlays for the whole screen
    if (isBusy) {
        Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(color = Color.White) }
    }

    pendingDeleteIndex?.let { index ->
        AlertDialog(
                onDismissRequest = { pendingDeleteIndex = null },
                title = { Text("Delete image?") },
                text = { Text("This will remove the selected image from the current scan.") },
                confirmButton = {
                    TextButton(
                            onClick = {
                                if (index in capturedPageUris.indices) {
                                    onRemovePage(index)
                                }
                                pendingDeleteIndex = null
                            }
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteIndex = null }) { Text("Cancel") }
                }
        )
    }
}

@Composable
fun CameraPreview(onCameraPreviewReady: (PreviewView) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
            modifier = modifier,
            factory = {
                PreviewView(context).also { previewView -> onCameraPreviewReady(previewView) }
            }
    )
}
