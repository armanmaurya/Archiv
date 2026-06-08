package com.armanmaurya.archiv.ui.viewer

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class TextSelection(val pageIndex: Int, val startIndex: Int, val endIndex: Int)
enum class DragHandle { LEFT, RIGHT, NONE }

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

    LaunchedEffect(pdfState) {
        withContext(Dispatchers.IO) {
            pdfState.load()
        }
    }

    // Tracking manual interaction for indicator visibility
    var lastInteractionTime by remember { mutableStateOf(0L) }
    
    LaunchedEffect(lastInteractionTime) {
        if (lastInteractionTime > 0) {
            delay(2500)
            lastInteractionTime = 0
        }
    }

    var activeSelection by remember { mutableStateOf<TextSelection?>(null) }
    val haptic = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
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
        var isDraggingIndicator by remember { mutableStateOf(false) }

        fun getCharacterAtPosition(screenX: Float, screenY: Float): Pair<Int, Int>? {
            val touchX = screenX + horizontalState.value
            val touchY = screenY
            
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            
            for (item in visibleItems) {
                val pageIndex = item.index
                val itemTop = item.offset
                val itemBottom = item.offset + item.size
                
                if (touchY >= itemTop && touchY <= itemBottom) {
                    val localY = touchY - itemTop
                    val localX = touchX
                    
                    val pageSize = pdfState.pageSizes[pageIndex] ?: continue
                    if (pageSize.width > 0 && pageSize.height > 0) {
                        val renderedWidth = screenWidthPx * scale
                        val renderedHeight = item.size
                        
                        val ratioX = pageSize.width.toFloat() / renderedWidth
                        val ratioY = pageSize.height.toFloat() / renderedHeight
                        
                        val pdfX = localX * ratioX
                        val pdfY = localY * ratioY
                        
                        val pageText = pdfState.pageTextCache[pageIndex]
                        if (pageText != null) {
                            for ((charIndex, char) in pageText.characters.withIndex()) {
                                val rect = char.boundingBox
                                // REFINED HIT DETECTION for characters
                                val paddingX = 4f * ratioX
                                val paddingY = 4f * ratioY
                                if (pdfX >= rect.left - paddingX && pdfX <= rect.right + paddingX &&
                                    pdfY >= rect.top - paddingY && pdfY <= rect.bottom + paddingY) {
                                    return Pair(pageIndex, charIndex)
                                }
                            }
                        }
                    }
                }
            }
            return null
        }

        fun getWordRange(pageText: PageText, charIndex: Int): Pair<Int, Int> {
            val chars = pageText.characters
            if (charIndex < 0 || charIndex >= chars.size) return charIndex to charIndex
            
            var start = charIndex
            while (start > 0 && chars[start - 1].text.any { it.isLetterOrDigit() }) {
                start--
            }
            
            var end = charIndex
            while (end < chars.size - 1 && chars[end + 1].text.any { it.isLetterOrDigit() }) {
                end++
            }
            
            return start to end
        }

        fun getHandleAtPosition(screenX: Float, screenY: Float): DragHandle {
            val sel = activeSelection ?: return DragHandle.NONE
            val touchX = screenX + horizontalState.value
            val touchY = screenY
            
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            
            for (item in visibleItems) {
                if (item.index != sel.pageIndex) continue
                
                val itemTop = item.offset
                val itemBottom = item.offset + item.size
                
                if (touchY >= itemTop && touchY <= itemBottom) {
                    val localY = touchY - itemTop
                    val localX = touchX
                    
                    val pageSize = pdfState.pageSizes[item.index] ?: continue
                    if (pageSize.width > 0 && pageSize.height > 0) {
                        val renderedWidth = screenWidthPx * scale
                        val renderedHeight = item.size
                        
                        val ratioX = pageSize.width.toFloat() / renderedWidth
                        val ratioY = pageSize.height.toFloat() / renderedHeight
                        
                        val pdfX = localX * ratioX
                        val pdfY = localY * ratioY
                        
                        val pageText = pdfState.pageTextCache[item.index] ?: continue
                        
                        val actualStart = min(sel.startIndex, sel.endIndex)
                        val actualEnd = max(sel.startIndex, sel.endIndex)
                        
                        val hitPaddingX = with(density) { 32.dp.toPx() } * ratioX
                        val hitPaddingY = with(density) { 32.dp.toPx() } * ratioY
                        
                        // Check LEFT handle (actualStart)
                        val startChar = pageText.characters.getOrNull(actualStart)
                        if (startChar != null) {
                            val rect = startChar.boundingBox
                            if (abs(pdfX - rect.left) < hitPaddingX && abs(pdfY - rect.bottom) < hitPaddingY * 1.5f) {
                                return DragHandle.LEFT
                            }
                        }
                        
                        // Check RIGHT handle (actualEnd)
                        val endChar = pageText.characters.getOrNull(actualEnd)
                        if (endChar != null) {
                            val rect = endChar.boundingBox
                            if (abs(pdfX - rect.right) < hitPaddingX && abs(pdfY - rect.bottom) < hitPaddingY * 1.5f) {
                                return DragHandle.RIGHT
                            }
                        }
                    }
                }
            }
            return DragHandle.NONE
        }

        if (!pdfState.isLoaded && !pdfState.isError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { 
                            if (activeSelection != null) {
                                activeSelection = null
                            } else {
                                isTopBarVisible = !isTopBarVisible 
                            }
                        },
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
                            val down = awaitFirstDown(requireUnconsumed = true)
                            flingJob.value?.cancel()
                            isFlinging = false
                            velocityTracker.resetTracking()
                            velocityTracker.addPosition(down.uptimeMillis, down.position)
                            
                            var activeDragHandle = getHandleAtPosition(down.position.x, down.position.y)
                            if (activeDragHandle != DragHandle.NONE) {
                                down.consume()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }

                            var isLongPressTriggered = false
                            var longPressJob: kotlinx.coroutines.Job? = null
                            
                            if (activeDragHandle == DragHandle.NONE) {
                                longPressJob = scope.launch {
                                    delay(400)
                                    val charAt = getCharacterAtPosition(down.position.x, down.position.y)
                                    if (charAt != null) {
                                        isLongPressTriggered = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val pageText = pdfState.pageTextCache[charAt.first]
                                        if (pageText != null) {
                                            val range = getWordRange(pageText, charAt.second)
                                            activeSelection = TextSelection(charAt.first, range.first, range.second)
                                        }
                                    }
                                }
                            }
                            
                            while (true) {
                                val event = awaitPointerEvent()
                                val anyPressed = event.changes.any { it.pressed }
                                
                                val canceled = event.changes.any { it.isConsumed }
                                if (canceled) break

                                lastInteractionTime = System.currentTimeMillis()

                                if (!anyPressed) {
                                    if (isLongPressTriggered || activeDragHandle != DragHandle.NONE) {
                                        event.changes.forEach { it.consume() }
                                    }
                                    break
                                }

                                if (activeDragHandle != DragHandle.NONE) {
                                    val change = event.changes.first()
                                    val charAt = getCharacterAtPosition(change.position.x, change.position.y)
                                    if (charAt != null && activeSelection != null) {
                                        if (charAt.first == activeSelection!!.pageIndex) {
                                            val currentStart = activeSelection!!.startIndex
                                            val currentEnd = activeSelection!!.endIndex
                                            val actualStart = min(currentStart, currentEnd)
                                            val actualEnd = max(currentStart, currentEnd)
                                            
                                            if (activeDragHandle == DragHandle.LEFT) {
                                                activeSelection = activeSelection!!.copy(startIndex = charAt.second, endIndex = actualEnd)
                                            } else {
                                                activeSelection = activeSelection!!.copy(startIndex = actualStart, endIndex = charAt.second)
                                            }
                                        }
                                    }
                                    change.consume()
                                    continue
                                }

                                if (event.changes.size > 1) {
                                    longPressJob?.cancel()
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
                                    val dragDistance = (change.position - down.position).getDistance()
                                    
                                    if (dragDistance > viewConfiguration.touchSlop && !isLongPressTriggered) {
                                        longPressJob?.cancel()
                                    }
                                    
                                    if (isLongPressTriggered) {
                                        val charAt = getCharacterAtPosition(change.position.x, change.position.y)
                                        if (charAt != null && activeSelection != null) {
                                            if (charAt.first == activeSelection!!.pageIndex) {
                                                activeSelection = activeSelection!!.copy(endIndex = charAt.second)
                                            }
                                        }
                                        change.consume()
                                    } else if (dragDistance > viewConfiguration.touchSlop) {
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        val dragAmount = change.position - change.previousPosition
                                        horizontalState.dispatchRawDelta(-dragAmount.x)
                                        listState.dispatchRawDelta(-dragAmount.y)
                                        change.consume()
                                    }
                                }
                            }
                            longPressJob?.cancel()

                            if (!isZooming && !isLongPressTriggered && activeDragHandle == DragHandle.NONE) {
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
                if (pdfState.isLoaded && pdfState.pageCount > 0) {
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = false,
                        modifier = Modifier
                            .width(screenWidth * scale)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        items(pdfState.pageCount) { pageIndex ->
                            PdfPageItem(
                                pageIndex = pageIndex,
                                pdfState = pdfState,
                                scale = scale,
                                isFlinging = isFlinging,
                                viewportWidthPx = screenWidthPx,
                                viewportHeightPx = screenHeightPx,
                                activeSelection = activeSelection
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
            visible = activeSelection != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        val sel = activeSelection
                        if (sel != null) {
                            val textChars = pdfState.pageTextCache[sel.pageIndex]?.characters
                            if (textChars != null) {
                                val start = min(sel.startIndex, sel.endIndex)
                                val end = max(sel.startIndex, sel.endIndex)
                                val text = textChars.subList(start, end + 1).joinToString("") { it.text }
                                
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Copied Text", text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
                                activeSelection = null
                            }
                        }
                    }) {
                        Text("Copy", style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = { activeSelection = null }) {
                        Text("Clear", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isTopBarVisible && activeSelection == null,
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

        if (pdfState.isLoaded && pdfState.pageCount > 1) {
            val totalPages = pdfState.pageCount
            val firstVisiblePage = listState.firstVisibleItemIndex
            
            // Continuous Progress Calculation (Pixel-level tracking)
            val computedProgress = remember(firstVisiblePage, listState.firstVisibleItemScrollOffset, totalPages) {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) 0f
                else {
                    val firstItem = visibleItems.first()
                    val itemHeight = firstItem.size
                    val totalHeight = itemHeight * totalPages
                    val currentScroll = (firstItem.index * itemHeight) + listState.firstVisibleItemScrollOffset
                    (currentScroll.toFloat() / (totalHeight - layoutInfo.viewportEndOffset)).coerceIn(0f, 1f)
                }
            }

            var manualScrollProgress by remember { mutableStateOf<Float?>(null) }
            val displayProgress = manualScrollProgress ?: computedProgress

            val isIndicatorVisible = (lastInteractionTime > 0 || isDraggingIndicator || isTopBarVisible || isFlinging) && activeSelection == null

            AnimatedVisibility(
                visible = isIndicatorVisible,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .width(160.dp)
                        .fillMaxHeight()
                        .padding(vertical = 48.dp)
                ) {
                    val trackHeightPx = constraints.maxHeight.toFloat()
                    val handleSize = 56.dp
                    val handleSizePx = with(density) { handleSize.toPx() }
                    val maxOffsetPx = (trackHeightPx - handleSizePx).coerceAtLeast(0f)
                    
                    val topOffset = with(density) { (displayProgress * maxOffsetPx).toDp().coerceAtLeast(0.dp) }

                    // Page Bubble (M3 Style)
                    AnimatedVisibility(
                        visible = isDraggingIndicator,
                        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
                        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 2 }),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = topOffset)
                    ) {
                        Surface(
                            tonalElevation = 6.dp,
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                text = "${listState.firstVisibleItemIndex + 1} / $totalPages",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Circular Handle (M3 Style)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = topOffset)
                            .size(handleSize)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    flingJob.value?.cancel() // STOP FLING IMMEDIATELY
                                    isFlinging = false
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                            .pointerInput(totalPages) {
                                coroutineScope {
                                    detectDragGestures(
                                        onDragStart = { 
                                            isDraggingIndicator = true 
                                            flingJob.value?.cancel() // STOP FLING IMMEDIATELY
                                            isFlinging = false
                                            manualScrollProgress = computedProgress
                                            lastInteractionTime = System.currentTimeMillis()
                                        },
                                        onDragEnd = { 
                                            isDraggingIndicator = false 
                                            manualScrollProgress = null
                                            lastInteractionTime = System.currentTimeMillis()
                                        },
                                        onDragCancel = { 
                                            isDraggingIndicator = false 
                                            manualScrollProgress = null
                                            lastInteractionTime = System.currentTimeMillis()
                                        },
                                        onDrag = { change, dragAmount ->
                                            lastInteractionTime = System.currentTimeMillis()
                                            
                                            val currentProgress = manualScrollProgress ?: computedProgress
                                            val deltaProgress = dragAmount.y / maxOffsetPx
                                            val newProgress = (currentProgress + deltaProgress).coerceIn(0f, 1f)
                                            manualScrollProgress = newProgress
                                            
                                            val layoutInfo = listState.layoutInfo
                                            val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                                            if (itemHeight > 0) {
                                                val totalScrollPx = itemHeight * totalPages
                                                val targetScrollPx = newProgress * (totalScrollPx - layoutInfo.viewportEndOffset)
                                                val targetIndex = (targetScrollPx / itemHeight).toInt()
                                                val targetOffset = (targetScrollPx % itemHeight).toInt()
                                                
                                                launch {
                                                    listState.scrollToItem(targetIndex.coerceIn(0, totalPages - 1), targetOffset)
                                                }
                                            }
                                            change.consume()
                                        }
                                    )
                                }
                            },
                        shape = CircleShape,
                        color = if (isDraggingIndicator) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isDraggingIndicator) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                        tonalElevation = 4.dp,
                        shadowElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).offset(y = (-4).dp)
                                )
                            }
                        }
                    }
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
    viewportHeightPx: Int,
    activeSelection: TextSelection?
) {
    val context = LocalContext.current
    val thumbnailBitmapState = remember { mutableStateOf<Bitmap?>(null) }
    val baseBitmapState = remember { mutableStateOf<Bitmap?>(null) }
    val tileBitmapState = remember { mutableStateOf<Bitmap?>(null) }
    val tileRectState = remember { mutableStateOf<android.graphics.RectF?>(null) }
    
    var pageBoundsInWindow by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(pageIndex, pdfState.isLoaded) {
        if (!pdfState.isLoaded) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            thumbnailBitmapState.value = pdfState.renderPage(pageIndex, 200)
        }
        if (!pdfState.pageTextCache.containsKey(pageIndex)) {
            withContext(Dispatchers.Default) {
                val text = pdfState.extractText(pageIndex)
                if (text != null) {
                    pdfState.pageTextCache[pageIndex] = text
                }
            }
        }
    }

    LaunchedEffect(pageIndex, pdfState.isLoaded) {
        if (!pdfState.isLoaded) return@LaunchedEffect
        delay(150)
        withContext(Dispatchers.IO) {
            baseBitmapState.value = pdfState.renderPage(pageIndex, context.resources.displayMetrics.widthPixels)
        }
    }

    var lastTriggeredBounds by remember { mutableStateOf(Rect.Zero) }
    
    LaunchedEffect(pageIndex, scale, isFlinging, pageBoundsInWindow, pdfState.isLoaded) {
        if (!pdfState.isLoaded || scale <= 1.1f) {
            tileBitmapState.value = null
            tileRectState.value = null
            return@LaunchedEffect
        }

        val windowRect = Rect(0f, 0f, viewportWidthPx.toFloat(), viewportHeightPx.toFloat())
        val intersection = windowRect.intersect(pageBoundsInWindow)

        if (intersection.isEmpty || intersection.width < 10 || intersection.height < 10) {
            return@LaunchedEffect
        }

        // FIX: Compare RAW page bounds to detect panning correctly
        val horizontalDelta = abs(pageBoundsInWindow.left - lastTriggeredBounds.left)
        val verticalDelta = abs(pageBoundsInWindow.top - lastTriggeredBounds.top)
        
        if (isFlinging) {
            delay(400)
        } else if (horizontalDelta < 40f && verticalDelta < 40f && tileBitmapState.value != null) {
            return@LaunchedEffect
        } else {
            delay(150)
        }

        withContext(Dispatchers.IO) {
            val currentIntersection = windowRect.intersect(pageBoundsInWindow)
            if (currentIntersection.isEmpty) return@withContext

            val localX = (currentIntersection.left - pageBoundsInWindow.left) / pageBoundsInWindow.width
            val localY = (currentIntersection.top - pageBoundsInWindow.top) / pageBoundsInWindow.height
            val localWidth = currentIntersection.width / pageBoundsInWindow.width
            val localHeight = currentIntersection.height / pageBoundsInWindow.height

            val normalizedRect = android.graphics.RectF(localX, localY, localX + localWidth, localY + localHeight)

            val tile = pdfState.renderTile(
                index = pageIndex,
                tileWidth = currentIntersection.width.roundToInt(),
                tileHeight = currentIntersection.height.roundToInt(),
                normalizedRect = normalizedRect
            )

            if (tile != null) {
                tileBitmapState.value = tile
                tileRectState.value = normalizedRect
                lastTriggeredBounds = pageBoundsInWindow // Track raw bounds
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { layoutCoordinates ->
                val pos = layoutCoordinates.positionInWindow()
                val size = layoutCoordinates.size
                pageBoundsInWindow = Rect(
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
                val currentBase = base ?: thumb
                drawImage(
                    image = currentBase.asImageBitmap(),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = if (base != null) FilterQuality.Medium else FilterQuality.Low
                )
                
                val tile = tileBitmapState.value
                val normRect = tileRectState.value
                if (tile != null && normRect != null) {
                    val left = normRect.left * size.width
                    val top = normRect.top * size.height
                    val width = normRect.width() * size.width
                    val height = normRect.height() * size.height
                    
                    drawImage(
                        image = tile.asImageBitmap(),
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                        filterQuality = FilterQuality.Medium
                    )
                }

                // Draw Text Selection Highlights & Handles
                if (activeSelection != null && activeSelection.pageIndex == pageIndex) {
                    val pageText = pdfState.pageTextCache[pageIndex]
                    val pageSize = pdfState.pageSizes[pageIndex]
                    if (pageText != null && pageSize != null && pageSize.width > 0 && pageSize.height > 0) {
                        val ratioX = size.width.toFloat() / pageSize.width.toFloat()
                        val ratioY = size.height.toFloat() / pageSize.height.toFloat()
                        
                        val start = min(activeSelection.startIndex, activeSelection.endIndex)
                        val end = max(activeSelection.startIndex, activeSelection.endIndex)
                        
                        val selectedChars = (start..end).mapNotNull { pageText.characters.getOrNull(it) }
                        
                        // Group characters into lines and calculate padded bounds
                        val lineBoundsMap = mutableMapOf<TextCharacter, android.graphics.RectF>()
                        val lineRects = mutableListOf<android.graphics.RectF>()
                        
                        val lines = mutableListOf<MutableList<TextCharacter>>()
                        for (char in selectedChars) {
                            val rect = char.boundingBox
                            val matchingLine = lines.find { line ->
                                val lineRect = line.first().boundingBox
                                val intersectTop = max(rect.top, lineRect.top)
                                val intersectBottom = min(rect.bottom, lineRect.bottom)
                                val overlap = intersectBottom - intersectTop
                                overlap > (rect.height() * 0.5f)
                            }
                            if (matchingLine != null) {
                                matchingLine.add(char)
                            } else {
                                lines.add(mutableListOf(char))
                            }
                        }

                        for (line in lines) {
                            val minX = line.minOf { it.boundingBox.left }
                            val maxX = line.maxOf { it.boundingBox.right }
                            val minY = line.minOf { it.boundingBox.top }
                            val maxY = line.maxOf { it.boundingBox.bottom }
                            
                            val paddingY = (maxY - minY) * 0.35f
                            val rect = android.graphics.RectF(minX, minY - paddingY, maxX, maxY + paddingY)
                            lineRects.add(rect)
                            line.forEach { lineBoundsMap[it] = rect }
                        }
                        
                        val highlightColor = Color(0xFF0066CC).copy(alpha = 0.3f)
                        for (rect in lineRects) {
                            drawRect(
                                color = highlightColor,
                                topLeft = Offset(rect.left * ratioX, rect.top * ratioY),
                                size = androidx.compose.ui.geometry.Size(rect.width() * ratioX, rect.height() * ratioY)
                            )
                        }

                        // Draw Handles
                        val handleColor = Color(0xFF0066CC)
                        val startChar = pageText.characters.getOrNull(start)
                        val endChar = pageText.characters.getOrNull(end)

                        if (startChar != null) {
                            val lineRect = lineBoundsMap[startChar]
                            if (lineRect != null) {
                                val x = startChar.boundingBox.left * ratioX
                                drawLine(handleColor, Offset(x, lineRect.top * ratioY), Offset(x, lineRect.bottom * ratioY), 2.dp.toPx())
                                drawCircle(handleColor, 6.dp.toPx(), Offset(x, lineRect.bottom * ratioY + 6.dp.toPx()))
                            }
                        }

                        if (endChar != null) {
                            val lineRect = lineBoundsMap[endChar]
                            if (lineRect != null) {
                                val x = endChar.boundingBox.right * ratioX
                                drawLine(handleColor, Offset(x, lineRect.top * ratioY), Offset(x, lineRect.bottom * ratioY), 2.dp.toPx())
                                drawCircle(handleColor, 6.dp.toPx(), Offset(x, lineRect.bottom * ratioY + 6.dp.toPx()))
                            }
                        }
                    }
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
    var renderer: PdfRenderer? = null
    var fileDescriptor: ParcelFileDescriptor? = null
    var pdDocument: PDDocument? = null
    
    var pageCount by mutableStateOf(0)
    var isError by mutableStateOf(false)
    var isLoaded by mutableStateOf(false)
    
    val pageSizes = mutableMapOf<Int, IntSize>()
    val pageTextCache = mutableStateMapOf<Int, PageText>()

    fun load() {
        try {
            PDFBoxResourceLoader.init(context)
            if (uri != null) {
                fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                fileDescriptor?.let {
                    renderer = PdfRenderer(it)
                    pageCount = renderer?.pageCount ?: 0
                    
                    for (i in 0 until pageCount) {
                        try {
                            renderer?.openPage(i)?.use { page ->
                                pageSizes[i] = IntSize(page.width, page.height)
                            }
                        } catch (e: Exception) {}
                    }
                }
                
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        pdDocument = PDDocument.load(inputStream)
                    }
                } catch (e: Exception) {}
            }
            isLoaded = true
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
                bitmap.eraseColor(android.graphics.Color.WHITE) // FIX TRANSPARENCY
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
                bitmap.eraseColor(android.graphics.Color.WHITE) // FIX TRANSPARENCY
                val matrix = android.graphics.Matrix()
                
                val scaleX = tileWidth / (normalizedRect.width() * page.width)
                val scaleY = tileHeight / (normalizedRect.height() * page.height)
                matrix.postScale(scaleX, scaleY)
                
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
            PageText(stripper.characters)
        } catch (e: Exception) {
            null
        }
    }

    fun close() {
        renderer?.close()
        fileDescriptor?.close()
        pdDocument?.close()
        pageTextCache.clear()
        pageSizes.clear()
    }
}

data class PageText(val characters: List<TextCharacter>)
data class TextCharacter(val text: String, val boundingBox: android.graphics.RectF)

class TextPositionStripper : PDFTextStripper() {
    val characters = mutableListOf<TextCharacter>()

    override fun writeString(string: String?, textPositions: MutableList<TextPosition>?) {
        textPositions?.forEach { pos ->
            val rect = android.graphics.RectF(
                pos.xDirAdj,
                pos.yDirAdj - pos.heightDir,
                pos.xDirAdj + pos.widthDirAdj,
                pos.yDirAdj
            )
            characters.add(TextCharacter(pos.unicode, rect))
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
fun PdfViewerFallback(
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

fun launchOpenWith(
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

fun Context.findFragmentActivity(): FragmentActivity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

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

suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitFirstDown(
    requireUnconsumed: Boolean = true
): PointerInputChange {
    var event: PointerInputChange
    do {
        event = awaitPointerEvent().changes.first()
    } while (event.pressed && (requireUnconsumed && event.isConsumed))
    return event
}
