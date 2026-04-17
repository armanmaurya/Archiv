package com.example.pdfscanner.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PointF
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.pdfscanner.camera.DocumentCornerDetector
import com.example.pdfscanner.decodeScaledBitmap
import com.example.pdfscanner.image.BitmapMemoryCache
import com.example.pdfscanner.image.fullImageBounds
import com.example.pdfscanner.image.orderCorners
import com.example.pdfscanner.image.warpBitmapWithQuad
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScannerScreen(
        viewModel: ScannerViewModel,
        onOpenEditor: (Int) -> Unit,
        scrollToIndexHint: Int? = null,
        onScrollHintConsumed: () -> Unit = {},
        sharedTransitionScope: SharedTransitionScope? = null,
        animatedVisibilityScope: AnimatedVisibilityScope? = null,
        sharedElementKeyForUri: (Uri) -> String = { uri -> "page-$uri" }
) {
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    val pages = viewModel.pages
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val detector = remember { DocumentCornerDetector() }
    val pdfStorage = remember(context) { ScannerPdfStorage(context) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { analyzerExecutor.shutdown() }
    }

    var hasCameraPermission by remember { mutableStateOf(isCameraPermissionGranted(context)) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var detectedCorners by remember { mutableStateOf<List<PointF>?>(null) }
    var imageAspectRatio by remember { mutableFloatStateOf(0.75f) }
    var scannerErrorMessage by remember { mutableStateOf<String?>(null) }
    var isScannerBusy by remember { mutableStateOf(false) }
    val isScreenBusy = viewModel.isSavingPdf || isScannerBusy
    val currentDetectedCorners by rememberUpdatedState(detectedCorners)

    val requestCameraPermissionLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                hasCameraPermission = granted
                if (!granted) {
                    scannerErrorMessage = "Camera permission is required to scan documents."
                } else {
                    scannerErrorMessage = null
                }
            }

    val pickImagesLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
                if (uris.isEmpty()) return@rememberLauncherForActivityResult
                isScannerBusy = true
                coroutineScope.launch {
                    val copiedUris = withContext(Dispatchers.IO) {
                        uris.mapNotNull { uri -> copyUriToCache(context, uri) }
                    }
                    copiedUris.forEach { uri -> viewModel.addPage(uri) }
                    if (copiedUris.isEmpty()) {
                        scannerErrorMessage = "Unable to import selected images."
                    }
                    isScannerBusy = false
                }
            }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(previewView, hasCameraPermission, lifecycleOwner) {
        val targetPreviewView = previewView
        if (!hasCameraPermission || targetPreviewView == null) {
            imageCapture = null
            detectedCorners = null
            return@LaunchedEffect
        }

        bindCameraPreview(
                context = context,
                lifecycleOwner = lifecycleOwner,
                detector = detector,
                analyzerExecutor = analyzerExecutor,
                previewView = targetPreviewView,
                onImageCaptureReady = { captureUseCase -> imageCapture = captureUseCase },
                onCornersUpdated = { corners, aspect ->
                    if (corners != null && aspect != null) {
                        detectedCorners = corners
                        imageAspectRatio = aspect
                    } else {
                        detectedCorners = null
                    }
                },
                onError = { message -> scannerErrorMessage = message }
        )
    }

    fun capturePage() {
        if (!hasCameraPermission) {
            scannerErrorMessage = "Camera permission is required to capture pages."
            return
        }

        val captureUseCase = imageCapture
        if (captureUseCase == null) {
            scannerErrorMessage = "Camera is not ready yet."
            return
        }

        val cacheDirectory = File(context.cacheDir, "captured_pages")
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            scannerErrorMessage = "Unable to create cache directory for captured pages."
            return
        }

        isScannerBusy = true
        scannerErrorMessage = null
        val outputFile = File(cacheDirectory, "page_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        captureUseCase.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val savedUri = outputFile.toUri()
                        val initialBounds = sanitizeInitialBounds(
                                currentDetectedCorners
                                        ?.takeIf { it.size == 4 }
                                        ?.let { orderCorners(it) }
                        )
                        viewModel.addPage(savedUri, initialBounds)
                        isScannerBusy = false
                    }

                    override fun onError(exception: ImageCaptureException) {
                        isScannerBusy = false
                        scannerErrorMessage = exception.message ?: "Failed to capture page."
                        Log.e("CapturePage", "ImageCapture error: ${exception.message}", exception)
                    }
                }
        )
    }

    val visibleErrorMessage = scannerErrorMessage ?: viewModel.saveErrorMessage

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Camera permission is required.")
                Button(onClick = { requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant permission")
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            CameraPreview(
                    onCameraPreviewReady = { preview -> previewView = preview },
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
            visibleErrorMessage?.let { message ->
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
                        IconButton(
                                onClick = {
                                    if (scannerErrorMessage != null) {
                                        scannerErrorMessage = null
                                    } else {
                                        viewModel.dismissSaveError()
                                    }
                                }
                        ) {
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
                                    .clickable(enabled = !isScreenBusy) {
                                        pickImagesLauncher.launch("image/*")
                                    },
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
                                    .clickable(enabled = !isScreenBusy) { capturePage() }
            )

            // Right action button
            Box(
                    modifier =
                            Modifier.size(56.dp).clickable(
                                            enabled = !isScreenBusy && pages.isNotEmpty()
                                    ) {
                                        viewModel.savePagesAsPdf(context, pdfStorage)
                                    },
                    contentAlignment = Alignment.Center
            ) {
                // A custom shape similar to the screenshot
                Icon(
                        Icons.Default.Check,
                        contentDescription = "Save PDF",
                        tint = if (pages.isNotEmpty()) Color(0xFF67B5E8) else Color.Gray,
                        modifier = Modifier.size(40.dp)
                )
            }
        } // Row

        // Thumbnail list with drag-to-reorder at the absolute bottom
        val lazyListState = rememberLazyListState()
        var draggingIndex by remember { mutableStateOf<Int?>(null) }
        var dragOffsetX by remember { mutableFloatStateOf(0f) }
        var isSettling by remember { mutableStateOf(false) }

        LaunchedEffect(scrollToIndexHint, pages.size) {
            val target = scrollToIndexHint
            if (target != null && target in pages.indices) {
                lazyListState.scrollToItem(target)
                onScrollHintConsumed()
            }
        }

        // Auto-scroll to the last item whenever a new page is captured
        LaunchedEffect(pages.size) {
            if (pages.isNotEmpty()) {
                lazyListState.animateScrollToItem(pages.size - 1)
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
                                                    viewModel.reorderPages(
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
            items(pages, key = { it.uri.toString() }) { pageState ->
                val uri = pageState.uri
                val pageIndex = pages.indexOf(pageState)
                val isDragging = draggingIndex == pageIndex
                val bounds = pageState.cropBounds
                var imageBitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
                var hasError by remember(uri) { mutableStateOf(false) }
                val context = LocalContext.current
                val cacheKey = uri.toString()
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
                                                onOpenEditor(pageIndex)
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
    if (isScreenBusy) {
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
                                if (index in pages.indices) {
                                    viewModel.removePage(index)
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

private fun isCameraPermissionGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}

private fun bindCameraPreview(
        context: Context,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        detector: DocumentCornerDetector,
        analyzerExecutor: ExecutorService,
        previewView: PreviewView,
        onImageCaptureReady: (ImageCapture) -> Unit,
        onCornersUpdated: (List<PointF>?, Float?) -> Unit,
        onError: (String) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                        .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageCapture = ImageCapture.Builder()
                        .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                onImageCaptureReady(imageCapture)

                val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                var lastDetectedCorners: List<PointF>? = null
                var lastAspectRatio: Float? = null
                var strikeOutCount = 0

                imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    try {
                        val result = detector.detectDocumentCorners(imageProxy)
                        val smoothedResult = if (result != null) {
                            strikeOutCount = 0
                            val (rawCorners, aspect) = result
                            val sorted = detector.sortCornersClockwise(rawCorners)
                            val currentLast = lastDetectedCorners
                            val smoothed = if (currentLast != null && currentLast.size == 4) {
                                sorted.mapIndexed { index, newPt ->
                                    val oldPt = currentLast[index]
                                    PointF(
                                            oldPt.x * 0.7f + newPt.x * 0.3f,
                                            oldPt.y * 0.7f + newPt.y * 0.3f
                                    )
                                }
                            } else {
                                sorted
                            }

                            lastDetectedCorners = smoothed
                            lastAspectRatio = aspect
                            Pair(smoothed, aspect)
                        } else {
                            strikeOutCount++
                            if (strikeOutCount > 5) {
                                lastDetectedCorners = null
                                null
                            } else {
                                val corners = lastDetectedCorners
                                val aspect = lastAspectRatio
                                if (corners != null && aspect != null) Pair(corners, aspect) else null
                            }
                        }

                        ContextCompat.getMainExecutor(context).execute {
                            if (smoothedResult != null) {
                                onCornersUpdated(smoothedResult.first, smoothedResult.second)
                            } else {
                                onCornersUpdated(null, null)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("detectDocumentCorners", "Error locating document corners", e)
                    } finally {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                            imageAnalysis
                    )
                } catch (error: IllegalStateException) {
                    onError("Unable to bind camera: ${error.message ?: "unknown error"}")
                } catch (error: IllegalArgumentException) {
                    onError("Unable to bind camera: ${error.message ?: "unknown error"}")
                }
            },
            ContextCompat.getMainExecutor(context)
    )
}

private fun copyUriToCache(context: Context, uri: Uri): Uri? {
    return try {
        val cacheDirectory = File(context.cacheDir, "captured_pages").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
        val outputFile = File(
                cacheDirectory,
                "gallery_${System.currentTimeMillis()}_${uri.lastPathSegment?.takeLast(20)?.replace("/", "_")}.jpg"
        )
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outputFile).use { output -> input.copyTo(output) }
        } ?: run {
            Log.e("PickImages", "Could not open input stream for $uri")
            return null
        }
        if (outputFile.length() == 0L) {
            outputFile.delete()
            Log.e("PickImages", "Copied file is empty for $uri")
            return null
        }
        outputFile.toUri()
    } catch (e: Exception) {
        Log.e("PickImages", "Failed to copy URI $uri: ${e.message}", e)
        null
    }
}

private fun sanitizeInitialBounds(bounds: List<PointF>?): List<PointF> {
    if (bounds == null || bounds.size != 4) return fullImageBounds()
    val clamped = bounds.map { point ->
        PointF(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
    }
    val area = kotlin.math.abs(polygonArea(clamped))
    if (area < 0.02f) return fullImageBounds()
    return clamped
}

private fun polygonArea(points: List<PointF>): Float {
    if (points.size < 3) return 0f
    var sum = 0f
    for (i in points.indices) {
        val j = (i + 1) % points.size
        sum += points[i].x * points[j].y - points[j].x * points[i].y
    }
    return sum * 0.5f
}
