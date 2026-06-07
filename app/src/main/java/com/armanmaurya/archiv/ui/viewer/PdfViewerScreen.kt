package com.armanmaurya.archiv.ui.viewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.armanmaurya.archiv.ui.document.DocumentViewModel
import com.armanmaurya.archiv.ui.viewer.components.BrightnessSlider
import com.armanmaurya.archiv.ui.viewer.components.TopBar
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

@Composable
fun PdfViewerScreen(
    documentId: String,
    viewModel: DocumentViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val documentUri = remember(documentId) { viewModel.getDocumentFileUri(documentId) }
    
    var isTopBarVisible by remember { mutableStateOf(false) }
    val autoHideMillis = 2500L
    
    var manualBrightness by remember { mutableStateOf(0.5f) }
    var isAutoBrightness by remember { mutableStateOf(true) }
    var isInteractingWithSlider by remember { mutableStateOf(false) }

    val fragmentActivity = remember(context) { context.findFragmentActivity() }

    DisposableEffect(manualBrightness, isAutoBrightness, fragmentActivity) {
        val window = fragmentActivity?.window
        val layoutParams = window?.attributes
        val originalBrightness = layoutParams?.screenBrightness ?: -1f
        
        layoutParams?.screenBrightness = if (isAutoBrightness) -1f else manualBrightness
        window?.attributes = layoutParams
        
        onDispose {
            layoutParams?.screenBrightness = originalBrightness
            window?.attributes = layoutParams
        }
    }

    LaunchedEffect(isTopBarVisible, isInteractingWithSlider) {
        if (isTopBarVisible && !isInteractingWithSlider) {
            delay(autoHideMillis)
            isTopBarVisible = false
        }
    }

    val pdfState = rememberPdfState(uri = documentUri, context = context)

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.DarkGray)) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val density = LocalDensity.current
        val screenWidthPx = with(density) { screenWidth.roundToPx() }
        val screenHeightPx = with(density) { screenHeight.roundToPx() }
        val scope = rememberCoroutineScope()
        
        var scale by remember { mutableStateOf(1f) }
        val listState = rememberLazyListState()
        val horizontalState = rememberScrollState()
        
        val velocityTracker = remember { VelocityTracker() }
        val flingJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        val splineDecay = rememberSplineBasedDecay<Float>()
        
        var isFlinging by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { isTopBarVisible = !isTopBarVisible },
                        onDoubleTap = { centroid ->
                            flingJob.value?.cancel()
                            scope.launch {
                                if (scale > 1.5f) {
                                    scale = 1f
                                    horizontalState.animateScrollTo(0)
                                    listState.animateScrollToItem(listState.firstVisibleItemIndex, 0)
                                } else {
                                    val oldScale = scale
                                    scale = 5f
                                    val zoomFactor = scale / oldScale
                                    
                                    val hDelta = (horizontalState.value + centroid.x) * (zoomFactor - 1)
                                    val vDelta = (listState.firstVisibleItemScrollOffset + centroid.y) * (zoomFactor - 1)
                                    
                                    launch { horizontalState.scrollBy(hDelta) }
                                    launch { listState.scrollBy(vDelta) }
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    coroutineScope {
                        awaitEachGesture {
                            var isZooming = false
                            
                            // 1. Initial Down: Stop momentum immediately
                            val down = awaitFirstDown(requireUnconsumed = false)
                            flingJob.value?.cancel()
                            isFlinging = false
                            velocityTracker.addPosition(down.uptimeMillis, down.position)
                            
                            while (true) {
                                val event = awaitPointerEvent()
                                val anyPressed = event.changes.any { it.pressed }
                                if (!anyPressed) break
                                
                                val canceled = event.changes.any { it.isConsumed }
                                if (canceled) break

                                if (event.changes.size > 1) {
                                    isZooming = true
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    
                                    val oldScale = scale
                                    scale = (scale * zoomChange).coerceIn(1f, 10f)
                                    val actualZoomFactor = scale / oldScale

                                    val hZoomDelta = (horizontalState.value + centroid.x) * (actualZoomFactor - 1)
                                    val vZoomDelta = (listState.firstVisibleItemScrollOffset + centroid.y) * (actualZoomFactor - 1)

                                    horizontalState.dispatchRawDelta(hZoomDelta - panChange.x)
                                    listState.dispatchRawDelta(vZoomDelta - panChange.y)
                                    
                                    event.changes.forEach { it.consume() }
                                } else {
                                    val change = event.changes.first()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    
                                    if (!isZooming) {
                                        val dragAmount = change.position - change.previousPosition
                                        horizontalState.dispatchRawDelta(-dragAmount.x)
                                        listState.dispatchRawDelta(-dragAmount.y)
                                        change.consume()
                                    }
                                }
                            }

                            // 2. Fling on release
                            if (!isZooming) {
                                val velocity = velocityTracker.calculateVelocity()
                                if (abs(velocity.x) > 100 || abs(velocity.y) > 100) {
                                    flingJob.value = scope.launch {
                                        isFlinging = true
                                        try {
                                            coroutineScope {
                                                launch { horizontalState.fling(-velocity.x, splineDecay, scope) }
                                                launch { listState.fling(-velocity.y, splineDecay, scope) }
                                            }
                                        } finally {
                                            isFlinging = false
                                        }
                                    }
                                }
                            }
                            velocityTracker.resetTracking()
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalState, enabled = false)
            ) {
                if (pdfState.pageCount > 0) {
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = false,
                        modifier = Modifier
                            .width(screenWidth * scale)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(pdfState.pageCount) { pageIndex ->
                            PdfPageItem(
                                pageIndex = pageIndex,
                                pdfState = pdfState,
                                scale = scale,
                                isFlinging = isFlinging,
                                viewportWidthPx = screenWidthPx,
                                viewportHeightPx = screenHeightPx
                            )
                            if (pageIndex < pdfState.pageCount - 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                } else if (pdfState.isError) {
                    PdfViewerFallback(
                        modifier = Modifier.fillMaxSize(),
                        onOpenWith = { launchOpenWith(context, viewModel, documentId) },
                        isSupported = true
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isTopBarVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopBar(
                    title = documentId,
                    onBackClick = onBackClick
                )
                BrightnessSlider(
                    manualBrightness = manualBrightness,
                    isAutoBrightness = isAutoBrightness,
                    onBrightnessChange = { 
                        manualBrightness = it
                        isAutoBrightness = false
                        isInteractingWithSlider = true
                    },
                    onToggleAuto = { isAutoBrightness = true },
                    onInteractionFinished = { isInteractingWithSlider = false }
                )
            }
        }

        // Vertical Scroll Indicator on the Right (Google Drive Style)
        if (pdfState.pageCount > 1) {
            val totalPages = pdfState.pageCount
            val firstVisiblePage = listState.firstVisibleItemIndex
            
            val progress = remember(firstVisiblePage, totalPages) {
                (firstVisiblePage.toFloat() / (totalPages - 1)).coerceIn(0f, 1f)
            }

            var isDragging by remember { mutableStateOf(false) }
            var isIndicatorVisible by remember { mutableStateOf(false) }
            
            LaunchedEffect(listState.isScrollInProgress, horizontalState.isScrollInProgress, isDragging) {
                if (listState.isScrollInProgress || horizontalState.isScrollInProgress || isDragging) {
                    isIndicatorVisible = true
                } else {
                    delay(2000)
                    isIndicatorVisible = false
                }
            }

            AnimatedVisibility(
                visible = isIndicatorVisible || isTopBarVisible,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .width(120.dp) // Wide enough to hold bubble + handle
                        .fillMaxHeight(0.7f)
                ) {
                    val trackHeightPx = constraints.maxHeight.toFloat()
                    val handleHeight = 52.dp
                    val handleHeightPx = with(density) { handleHeight.toPx() }
                    val maxOffsetPx = trackHeightPx - handleHeightPx
                    
                    val topOffset = with(density) { (progress * maxOffsetPx).toDp() }

                    // Interaction Layer (invisible wide track for easier grabbing)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(totalPages) {
                                detectDragGestures(
                                    onDragStart = { isDragging = true },
                                    onDragEnd = { isDragging = false },
                                    onDragCancel = { isDragging = false },
                                    onDrag = { change, _ ->
                                        val newProgress = (change.position.y / trackHeightPx).coerceIn(0f, 1f)
                                        val targetPage = (newProgress * (totalPages - 1)).toInt()
                                        scope.launch {
                                            listState.scrollToItem(targetPage)
                                        }
                                        change.consume()
                                    }
                                )
                            }
                    )

                    // Page Bubble (Appears to the left of the handle when dragging)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isDragging,
                        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
                        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 2 }),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = topOffset)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color.DarkGray.copy(alpha = 0.9f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Page ${firstVisiblePage + 1} / $totalPages",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }

                    // Vertical Handle (The small bar on the right edge)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = topOffset)
                            .width(6.dp)
                            .height(handleHeight)
                            .background(
                                color = if (isDragging) Color.White else Color.White.copy(alpha = 0.5f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun PdfPageItem(
    pageIndex: Int,
    pdfState: PdfState,
    scale: Float,
    isFlinging: Boolean,
    viewportWidthPx: Int,
    viewportHeightPx: Int
) {
    val context = LocalContext.current
    val thumbnailBitmapState = remember { mutableStateOf<Bitmap?>(null) }
    val baseBitmapState = remember { mutableStateOf<Bitmap?>(null) }
    val tileBitmapState = remember { mutableStateOf<Bitmap?>(null) }
    val tileRectState = remember { mutableStateOf<android.graphics.RectF?>(null) }
    
    var pageBoundsInWindow by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    // Tier 1: Instant Thumbnail (Ultra low-res for speed)
    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            thumbnailBitmapState.value = pdfState.renderPage(pageIndex, 200)
        }
    }

    // Tier 2: Normal Base Layer (Screen width)
    LaunchedEffect(pageIndex) {
        delay(150) // Slight delay to prioritize thumbnail and scrolling
        withContext(Dispatchers.IO) {
            baseBitmapState.value = pdfState.renderPage(pageIndex, context.resources.displayMetrics.widthPixels)
        }
    }

    // Tier 3: High-Res Tile Layer (Zoomed region)
    LaunchedEffect(pageIndex, scale, pageBoundsInWindow.left, pageBoundsInWindow.top, isFlinging) {
        if (scale <= 1.1f) {
            tileBitmapState.value = null
            tileRectState.value = null
            return@LaunchedEffect
        }

        delay(if (isFlinging) 400 else 250)

        withContext(Dispatchers.IO) {
            val windowRect = androidx.compose.ui.geometry.Rect(0f, 0f, viewportWidthPx.toFloat(), viewportHeightPx.toFloat())
            val intersection = windowRect.intersect(pageBoundsInWindow)

            if (intersection.isEmpty || intersection.width < 10 || intersection.height < 10) {
                return@withContext
            }

            val localX = (intersection.left - pageBoundsInWindow.left) / pageBoundsInWindow.width
            val localY = (intersection.top - pageBoundsInWindow.top) / pageBoundsInWindow.height
            val localWidth = intersection.width / pageBoundsInWindow.width
            val localHeight = intersection.height / pageBoundsInWindow.height

            val normalizedRect = android.graphics.RectF(localX, localY, localX + localWidth, localY + localHeight)

            val tile = pdfState.renderTile(
                index = pageIndex,
                tileWidth = intersection.width.toInt(),
                tileHeight = intersection.height.toInt(),
                normalizedRect = normalizedRect
            )

            if (tile != null) {
                tileBitmapState.value = tile
                tileRectState.value = normalizedRect
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { layoutCoordinates ->
                val pos = layoutCoordinates.positionInWindow()
                val size = layoutCoordinates.size
                pageBoundsInWindow = androidx.compose.ui.geometry.Rect(
                    pos.x, pos.y, pos.x + size.width, pos.y + size.height
                )
            }
    ) {
        val thumb = thumbnailBitmapState.value
        val base = baseBitmapState.value
        
        if (thumb != null) {
            val aspectRatio = thumb.width.toFloat() / thumb.height.toFloat()
            
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
            ) {
                // Draw Tier 1 or Tier 2
                val currentBase = base ?: thumb
                drawImage(
                    image = currentBase.asImageBitmap(),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = if (base != null) FilterQuality.Medium else FilterQuality.Low
                )
                
                // Draw Tier 3 (HD Tile)
                val tile = tileBitmapState.value
                val normRect = tileRectState.value
                if (tile != null && normRect != null) {
                    val left = normRect.left * size.width
                    val top = normRect.top * size.height
                    val width = normRect.width() * size.width
                    val height = normRect.height() * size.height
                    
                    drawImage(
                        image = tile.asImageBitmap(),
                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                        dstSize = IntSize(width.toInt(), height.toInt()),
                        filterQuality = FilterQuality.Medium
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / 1.414f)
                    .background(Color.LightGray)
            )
        }
    }
}

class PdfState(
    private val uri: Uri?,
    private val context: Context
) {
    private var renderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdDocument: PDDocument? = null
    
    var pageCount by mutableStateOf(0)
    var isError by mutableStateOf(false)

    init {
        try {
            PDFBoxResourceLoader.init(context)
            if (uri != null) {
                fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                fileDescriptor?.let {
                    renderer = PdfRenderer(it)
                    pageCount = renderer?.pageCount ?: 0
                }
                
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        pdDocument = PDDocument.load(inputStream)
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            isError = true
        }
    }

    fun renderPage(index: Int, targetWidth: Int = 0): Bitmap? {
        val renderer = renderer ?: return null
        if (index < 0 || index >= pageCount) return null
        
        return try {
            renderer.openPage(index).use { page ->
                val width: Int
                val height: Int
                
                if (targetWidth > 0) {
                    val aspectRatio = page.width.toFloat() / page.height.toFloat()
                    width = targetWidth
                    height = (targetWidth / aspectRatio).toInt()
                } else {
                    width = page.width
                    height = page.height
                }
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    fun renderTile(
        index: Int,
        tileWidth: Int,
        tileHeight: Int,
        normalizedRect: android.graphics.RectF
    ): Bitmap? {
        val renderer = renderer ?: return null
        if (index < 0 || index >= pageCount) return null
        
        return try {
            renderer.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap(tileWidth, tileHeight, Bitmap.Config.ARGB_8888)
                val matrix = android.graphics.Matrix()
                
                // Scale to match the tile's density on screen
                val scaleX = tileWidth / (normalizedRect.width() * page.width)
                val scaleY = tileHeight / (normalizedRect.height() * page.height)
                matrix.postScale(scaleX, scaleY)
                
                // Translate so the top-left of normalizedRect starts at (0,0) in bitmap
                matrix.postTranslate(-normalizedRect.left * page.width * scaleX, -normalizedRect.top * page.height * scaleY)
                
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    fun extractText(index: Int): PageText? {
        val doc = pdDocument ?: return null
        if (index < 0 || index >= doc.numberOfPages) return null
        
        return try {
            val stripper = TextPositionStripper()
            stripper.startPage = index + 1
            stripper.endPage = index + 1
            stripper.getText(doc)
            PageText(stripper.words)
        } catch (e: Exception) {
            null
        }
    }

    fun close() {
        renderer?.close()
        fileDescriptor?.close()
        pdDocument?.close()
    }
}

data class PageText(val words: List<TextWord>)
data class TextWord(val text: String, val boundingBox: android.graphics.RectF)

class TextPositionStripper : PDFTextStripper() {
    val words = mutableListOf<TextWord>()
    private var currentWord = StringBuilder()
    private var currentRect: android.graphics.RectF? = null

    override fun writeString(string: String?, textPositions: MutableList<TextPosition>?) {
        textPositions?.forEach { pos ->
            val char = pos.unicode
            if (char.isBlank()) {
                if (currentWord.isNotEmpty()) {
                    words.add(TextWord(currentWord.toString(), android.graphics.RectF(currentRect!!)))
                    currentWord.clear()
                    currentRect = null
                }
            } else {
                val rect = android.graphics.RectF(
                    pos.xDirAdj,
                    pos.yDirAdj - pos.heightDir,
                    pos.xDirAdj + pos.widthDirAdj,
                    pos.yDirAdj
                )
                if (currentRect == null) {
                    currentRect = rect
                } else {
                    currentRect!!.union(rect)
                }
                currentWord.append(char)
            }
        }
        if (currentWord.isNotEmpty()) {
            words.add(TextWord(currentWord.toString(), android.graphics.RectF(currentRect!!)))
            currentWord.clear()
            currentRect = null
        }
    }
}

@Composable
fun rememberPdfState(uri: Uri?, context: Context): PdfState {
    val state = remember(uri) { PdfState(uri, context) }
    DisposableEffect(state) {
        onDispose {
            state.close()
        }
    }
    return state
}

@Composable
private fun PdfViewerFallback(
    modifier: Modifier,
    onOpenWith: () -> Unit,
    isSupported: Boolean
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        val message = if (isSupported) {
            "Unable to load this PDF in app."
        } else {
            "In-app viewer is not supported on this device."
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
        Button(
            onClick = onOpenWith,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Open with")
        }
    }
}

private fun launchOpenWith(
    context: Context,
    viewModel: DocumentViewModel,
    documentId: String
) {
    val openIntent = viewModel.createOpenIntent(documentId) ?: return
    try {
        context.startActivity(Intent.createChooser(openIntent, "Open scan"))
    } catch (_: ActivityNotFoundException) {
        viewModel.onOpenAppUnavailable()
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

// Extension to provide standard Android fling behavior to manual scroll states
suspend fun androidx.compose.foundation.ScrollState.fling(
    initialVelocity: Float,
    decay: androidx.compose.animation.core.DecayAnimationSpec<Float>,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var lastValue = value.toFloat()
    Animatable(initialValue = lastValue).animateDecay(initialVelocity, decay) {
        val delta = value - lastValue
        scope.launch { scrollBy(delta) }
        lastValue = value
    }
}

suspend fun androidx.compose.foundation.lazy.LazyListState.fling(
    initialVelocity: Float,
    decay: androidx.compose.animation.core.DecayAnimationSpec<Float>,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var lastValue = 0f
    Animatable(initialValue = 0f).animateDecay(initialVelocity, decay) {
        val delta = value - lastValue
        scope.launch { scrollBy(delta) }
        lastValue = value
    }
}

// Standard pointer input helper
suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitFirstDown(
    requireUnconsumed: Boolean = true
): PointerInputChange {
    var event: PointerInputChange
    do {
        event = awaitPointerEvent().changes.first()
    } while (event.pressed && (requireUnconsumed && event.isConsumed))
    return event
}
