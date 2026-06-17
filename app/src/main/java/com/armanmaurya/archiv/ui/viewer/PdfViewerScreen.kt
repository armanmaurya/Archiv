package com.armanmaurya.archiv.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.armanmaurya.archiv.ui.viewer.components.BrightnessSlider
import com.armanmaurya.archiv.ui.viewer.components.TopBar
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.PointerEvent

@Stable
class PdfZoomPanState(
    val listState: androidx.compose.foundation.lazy.LazyListState,
    val horizontalState: androidx.compose.foundation.ScrollState
) {
    var scale by mutableFloatStateOf(1f)
    var listPositionInWindow by mutableStateOf(Offset.Zero)

    suspend fun reset() {
        coroutineScope {
            launch {
                animate(scale, 1f) { value, _ ->
                    scale = value
                }
            }
            launch { horizontalState.animateScrollTo(0) }
            launch { listState.animateScrollToItem(listState.firstVisibleItemIndex, 0) }
        }
    }
}

@Composable
fun rememberPdfZoomPanState(
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    horizontalState: androidx.compose.foundation.ScrollState = rememberScrollState()
) = remember { PdfZoomPanState(listState, horizontalState) }


@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun PdfViewerScreen(
    uri: Uri,
    title: String,
    pdfViewModel: PdfViewerViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val fragmentActivity = remember(context) { context.findFragmentActivity() }

    DisposableEffect(pdfViewModel.manualBrightness, pdfViewModel.isAutoBrightness, fragmentActivity) {
        val window = fragmentActivity?.window
        val layoutParams = window?.attributes
        val originalBrightness = layoutParams?.screenBrightness ?: -1f

        layoutParams?.screenBrightness = if (pdfViewModel.isAutoBrightness) -1f else pdfViewModel.manualBrightness
        window?.attributes = layoutParams

        onDispose {
            layoutParams?.screenBrightness = originalBrightness
            window?.attributes = layoutParams
        }
    }

    LaunchedEffect(uri) {
        pdfViewModel.loadDocument(uri = uri, context = context)
    }

    val haptic = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val zoomPanState = rememberPdfZoomPanState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenWidthPx = with(density) { screenWidth.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pdfViewModel.currentSearchIndex) {
        val index = pdfViewModel.currentSearchIndex
        if (index >= 0 && index < pdfViewModel.searchResults.size) {
            val result = pdfViewModel.searchResults[index]
            zoomPanState.listState.animateScrollToItem(result.pageIndex)
        }
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(pdfViewModel.isSearchActive) {
        if (pdfViewModel.isSearchActive) {
            delay(100)
            focusRequester.requestFocus()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {

        val velocityTracker = remember { VelocityTracker() }
        val flingJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        val splineDecay = rememberSplineBasedDecay<Float>()

        var isFlinging by remember { mutableStateOf(false) }

        fun getLinkAtPosition(screenX: Float, screenY: Float): PdfLink? {
            val touchX = screenX + zoomPanState.horizontalState.value
            val touchY = screenY - zoomPanState.listPositionInWindow.y

            val layoutInfo = zoomPanState.listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo

            for (item in visibleItems) {
                val itemTop = item.offset
                val itemBottom = item.offset + item.size

                if (touchY >= itemTop && touchY <= itemBottom) {
                    val localY = touchY - itemTop

                    val pageIndex = item.index
                    val pageSize = pdfViewModel.pageSizes[pageIndex] ?: continue
                    if (pageSize.width > 0 && pageSize.height > 0) {
                        val renderedWidth = screenWidthPx * zoomPanState.scale
                        val ratioX = pageSize.width.toFloat() / renderedWidth
                        val ratioY = pageSize.height.toFloat() / item.size.toFloat()

                        val pdfX = touchX * ratioX
                        val pdfY = localY * ratioY

                        val pageLinks = pdfViewModel.pageLinksCache[pageIndex]
                        if (pageLinks != null) {
                            for (link in pageLinks.links) {
                                val rect = link.boundingBox
                                if (pdfX >= rect.left && pdfX <= rect.right &&
                                    pdfY >= rect.top && pdfY <= rect.bottom) {
                                    return link
                                }
                            }
                        }
                    }
                }
            }
            return null
        }

        fun getCharacterAtPosition(screenX: Float, screenY: Float): Pair<Int, Int>? {
            val touchX = screenX + zoomPanState.horizontalState.value
            val touchY = screenY - zoomPanState.listPositionInWindow.y

            val layoutInfo = zoomPanState.listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo

            for (item in visibleItems) {
                val itemTop = item.offset
                val itemBottom = item.offset + item.size

                if (touchY >= itemTop && touchY <= itemBottom) {
                    val localY = touchY - itemTop

                    val pageIndex = item.index
                    val pageSize = pdfViewModel.pageSizes[pageIndex] ?: continue
                    if (pageSize.width > 0 && pageSize.height > 0) {
                        val renderedWidth = screenWidthPx * zoomPanState.scale
                        val ratioX = pageSize.width.toFloat() / renderedWidth
                        val ratioY = pageSize.height.toFloat() / item.size.toFloat()

                        val pdfX = touchX * ratioX
                        val pdfY = localY * ratioY

                        val pageText = pdfViewModel.pageTextCache[pageIndex]
                        if (pageText != null) {
                            for ((charIndex, char) in pageText.characters.withIndex()) {
                                val rect = char.boundingBox
                                // More generous hit area for touch: ~15-20 PDF units
                                val paddingX = 15f * ratioX
                                val paddingY = 20f * ratioY
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

        fun getHandleAtPosition(screenX: Float, screenY: Float): DragHandle {
            val sel = pdfViewModel.activeSelection ?: return DragHandle.NONE
            val touchX = screenX + zoomPanState.horizontalState.value
            val touchY = screenY - zoomPanState.listPositionInWindow.y

            val layoutInfo = zoomPanState.listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo

            for (item in visibleItems) {
                if (item.index != sel.startPageIndex && item.index != sel.endPageIndex) continue

                val itemTop = item.offset
                if (touchY >= itemTop && touchY <= itemTop + item.size) {
                    val localY = touchY - itemTop

                    val pageSize = pdfViewModel.pageSizes[item.index] ?: continue
                    if (pageSize.width > 0 && pageSize.height > 0) {
                        val renderedWidth = screenWidthPx * zoomPanState.scale
                        val ratioX = pageSize.width.toFloat() / renderedWidth
                        val ratioY = pageSize.height.toFloat() / item.size.toFloat()

                        val pdfX = touchX * ratioX
                        val pdfY = localY * ratioY

                        val pageText = pdfViewModel.pageTextCache[item.index] ?: continue
                        val hitPaddingX = with(density) { 32.dp.toPx() } * ratioX
                        val hitPaddingY = with(density) { 32.dp.toPx() } * ratioY

                        if (item.index == sel.startPageIndex) {
                            val startChar = pageText.characters.getOrNull(sel.startIndex)
                            if (startChar != null) {
                                val rect = startChar.boundingBox
                                if (abs(pdfX - rect.left) < hitPaddingX && abs(pdfY - rect.bottom) < hitPaddingY * 1.5f) {
                                    return DragHandle.LEFT
                                }
                            }
                        }

                        if (item.index == sel.endPageIndex) {
                            val endChar = pageText.characters.getOrNull(sel.endIndex)
                            if (endChar != null) {
                                val rect = endChar.boundingBox
                                if (abs(pdfX - rect.right) < hitPaddingX && abs(pdfY - rect.bottom) < hitPaddingY * 1.5f) {
                                    return DragHandle.RIGHT
                                }
                            }
                        }
                    }
                }
            }
            return DragHandle.NONE
        }

        if (!pdfViewModel.isLoaded && !pdfViewModel.isError) {
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
                        onTap = { offset ->
                            val link = getLinkAtPosition(offset.x, offset.y)
                            if (link != null) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.uri))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                }
                            } else if (pdfViewModel.activeSelection != null) {
                                pdfViewModel.clearSelection()
                            } else {
                                pdfViewModel.toggleTopBar()
                            }
                        },
                        onDoubleTap = { centroid ->
                            flingJob.value?.cancel()
                            coroutineScope.launch {
                                if (abs(zoomPanState.scale - 1f) > 0.05f) {
                                    zoomPanState.reset()
                                } else {
                                    val startScale = zoomPanState.scale
                                    val targetScale = 3f

                                    val hStart = zoomPanState.horizontalState.value.toFloat()
                                    val info = zoomPanState.listState.layoutInfo
                                    val firstVisible = info.visibleItemsInfo.firstOrNull() ?: return@launch

                                    val pinIndex = firstVisible.index
                                    val pinOffsetStart = zoomPanState.listState.firstVisibleItemScrollOffset.toFloat()

                                    var lastH = hStart
                                    var lastVOffset = pinOffsetStart

                                    animate(startScale, targetScale) { value, _ ->
                                        val currentScale = value
                                        val zoomFactor = currentScale / startScale

                                        val hTarget = hStart * zoomFactor + centroid.x * (zoomFactor - 1)
                                        val vOffsetTarget = (pinOffsetStart + centroid.y) * zoomFactor - centroid.y

                                        zoomPanState.scale = currentScale
                                        zoomPanState.horizontalState.dispatchRawDelta(hTarget - lastH)
                                        zoomPanState.listState.dispatchRawDelta(vOffsetTarget - lastVOffset)

                                        lastH = hTarget
                                        lastVOffset = vOffsetTarget
                                    }
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        flingJob.value?.cancel()
                        isFlinging = false
                        velocityTracker.resetTracking()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        val activeDragHandle = getHandleAtPosition(down.position.x, down.position.y)
                        if (activeDragHandle != DragHandle.NONE) {
                            down.consume()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        var isLongPressTriggered = false
                        var longPressJob: kotlinx.coroutines.Job? = null
                        var currentPointerPosition = down.position

                        if (activeDragHandle == DragHandle.NONE) {
                            longPressJob = coroutineScope.launch {
                                delay(viewConfiguration.longPressTimeoutMillis)
                                val charAt = getCharacterAtPosition(currentPointerPosition.x, currentPointerPosition.y)
                                if (charAt != null) {
                                    isLongPressTriggered = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val range = pdfViewModel.getWordRange(charAt.first, charAt.second)
                                    pdfViewModel.updateSelection(TextSelection(charAt.first, range.first, charAt.first, range.second))
                                }
                            }
                        } else {
                            // If we grabbed a handle, we need to know which one is base and which is extent
                            val sel = pdfViewModel.activeSelection
                            if (sel != null) {
                                if (activeDragHandle == DragHandle.LEFT) {
                                    pdfViewModel.updateSelection(sel.copy(basePageIndex = sel.endPageIndex, baseIndex = sel.endIndex, extentPageIndex = sel.startPageIndex, extentIndex = sel.startIndex))
                                } else {
                                    pdfViewModel.updateSelection(sel.copy(basePageIndex = sel.startPageIndex, baseIndex = sel.startIndex, extentPageIndex = sel.endPageIndex, extentIndex = sel.endIndex))
                                }
                            }
                        }

                        var event: PointerEvent
                        while (true) {
                            event = awaitPointerEvent()
                            val anyPressed = event.changes.any { it.pressed }

                            if (event.changes.any { it.isConsumed }) break
                            if (!anyPressed) {
                                if (isLongPressTriggered || activeDragHandle != DragHandle.NONE) {
                                    event.changes.forEach { it.consume() }
                                }
                                break
                            }

                            if (activeDragHandle != DragHandle.NONE) {
                                val change = event.changes.first()
                                currentPointerPosition = change.position
                                val charAt = getCharacterAtPosition(change.position.x, change.position.y)
                                if (charAt != null && pdfViewModel.activeSelection != null) {
                                    val sel = pdfViewModel.activeSelection!!
                                    pdfViewModel.updateSelection(sel.copy(extentPageIndex = charAt.first, extentIndex = charAt.second))
                                }
                                change.consume()
                                continue
                            }

                            if (event.changes.size > 1) {
                                longPressJob?.cancel()

                                if (pdfViewModel.isTopBarVisible && !pdfViewModel.isInteractingWithSlider) {
                                    pdfViewModel.updateTopBarVisibility(false)
                                }

                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)

                                val oldScale = zoomPanState.scale
                                zoomPanState.scale = (zoomPanState.scale * zoomChange).coerceIn(0.5f, 10f)
                                val actualZoomFactor = zoomPanState.scale / oldScale

                                val hZoomDelta = (zoomPanState.horizontalState.value + centroid.x) * (actualZoomFactor - 1)
                                val vZoomDelta = (zoomPanState.listState.firstVisibleItemScrollOffset + centroid.y) * (actualZoomFactor - 1)

                                zoomPanState.horizontalState.dispatchRawDelta(hZoomDelta - panChange.x)
                                zoomPanState.listState.dispatchRawDelta(vZoomDelta - panChange.y)

                                event.changes.forEach { it.consume() }
                            } else {
                                val change = event.changes.first()
                                currentPointerPosition = change.position
                                val dragDistance = (change.position - down.position).getDistance()

                                if (dragDistance > viewConfiguration.touchSlop && !isLongPressTriggered) {
                                    longPressJob?.cancel()
                                }

                                if (isLongPressTriggered) {
                                    val charAt = getCharacterAtPosition(change.position.x, change.position.y)
                                    if (charAt != null && pdfViewModel.activeSelection != null) {
                                        val sel = pdfViewModel.activeSelection!!
                                        val range = pdfViewModel.getWordRange(charAt.first, charAt.second)
                                        val isBackward = charAt.first < sel.basePageIndex || (charAt.first == sel.basePageIndex && charAt.second < sel.baseIndex)
                                        val newExtentIndex = if (isBackward) range.first else range.second
                                        pdfViewModel.updateSelection(sel.copy(extentPageIndex = charAt.first, extentIndex = newExtentIndex))
                                    }
                                    change.consume()
                                } else if (dragDistance > viewConfiguration.touchSlop) {
                                    if (pdfViewModel.isTopBarVisible && !pdfViewModel.isInteractingWithSlider) {
                                        pdfViewModel.updateTopBarVisibility(false)
                                    }
                                    
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val dragAmount = change.position - change.previousPosition
                                    zoomPanState.horizontalState.dispatchRawDelta(-dragAmount.x)
                                    zoomPanState.listState.dispatchRawDelta(-dragAmount.y)
                                    change.consume()
                                }
                            }
                        }
                        longPressJob?.cancel()

                        if (event.changes.size <= 1 && !isLongPressTriggered && activeDragHandle == DragHandle.NONE) {
                            val velocity = velocityTracker.calculateVelocity()
                            if (abs(velocity.x) > 100 || abs(velocity.y) > 100) {
                                flingJob.value = coroutineScope.launch {
                                    isFlinging = true
                                    try {
                                        coroutineScope {
                                            launch { zoomPanState.horizontalState.fling(-velocity.x, splineDecay) }
                                            launch { zoomPanState.listState.fling(-velocity.y, splineDecay) }
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
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(zoomPanState.horizontalState, enabled = false)
                    .onGloballyPositioned { zoomPanState.listPositionInWindow = it.positionInWindow() }
            ) {
                if (pdfViewModel.isLoaded && pdfViewModel.pageCount > 0) {
                    LazyColumn(
                        state = zoomPanState.listState,
                        userScrollEnabled = false,
                        modifier = Modifier
                            .width(screenWidth * zoomPanState.scale)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        items(pdfViewModel.pageCount) { pageIndex ->
                            PdfPageItem(
                                pageIndex = pageIndex,
                                pdfViewModel = pdfViewModel,
                                scale = zoomPanState.scale,
                                isFlinging = isFlinging,
                                viewportWidthPx = screenWidthPx,
                                viewportHeightPx = screenHeightPx
                            )
                            if (pageIndex < pdfViewModel.pageCount - 1) {
                                Spacer(modifier = Modifier.height(8.dp * zoomPanState.scale))
                            }
                        }
                    }
                } else if (pdfViewModel.isError) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Unable to load PDF",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        
        AnimatedVisibility(
            visible = pdfViewModel.activeSelection != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 32.dp)
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { pdfViewModel.selectAll() }) {
                        Text("Select All", style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = {
                        val sel = pdfViewModel.activeSelection
                        if (sel != null) {
                            val fullText = StringBuilder()
                            for (pIdx in sel.startPageIndex..sel.endPageIndex) {
                                val textChars = pdfViewModel.pageTextCache[pIdx]?.characters
                                if (!textChars.isNullOrEmpty()) {
                                    val start = (if (pIdx == sel.startPageIndex) sel.startIndex else 0).coerceIn(textChars.indices)
                                    val end = (if (pIdx == sel.endPageIndex) sel.endIndex else textChars.lastIndex).coerceIn(textChars.indices)
                                    if (start <= end) {
                                        val pageText = textChars.subList(start, end + 1).joinToString("") { it.text }
                                        fullText.append(pageText)
                                    }
                                    if (pIdx < sel.endPageIndex) fullText.append("\n")
                                }
                            }
                            
                            if (fullText.isNotEmpty()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Copied Text", fullText.toString())
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
                                pdfViewModel.clearSelection()
                            }
                        }
                    }) {
                        Text("Copy", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        var topControlsHeightPx by remember { mutableStateOf(0) }
        var bottomControlsHeightPx by remember { mutableStateOf(0) }

        AnimatedVisibility(
            visible = pdfViewModel.isTopBarVisible && pdfViewModel.activeSelection == null && !pdfViewModel.isSearchActive,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { layoutCoordinates ->
                        topControlsHeightPx = layoutCoordinates.size.height
                    }
            ) {
                TopBar(
                    title = title,
                    onBackClick = onBackClick
                )
                BrightnessSlider(
                    manualBrightness = pdfViewModel.manualBrightness,
                    isAutoBrightness = pdfViewModel.isAutoBrightness,
                    onBrightnessChange = { pdfViewModel.updateBrightness(it) },
                    onToggleAuto = { pdfViewModel.toggleAutoBrightness(true) },
                    onInteractionFinished = { pdfViewModel.updateSliderInteraction(false) }
                )
            }
        }

        AnimatedVisibility(
            visible = pdfViewModel.isTopBarVisible && pdfViewModel.activeSelection == null && !pdfViewModel.isSearchActive,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { layoutCoordinates ->
                        bottomControlsHeightPx = layoutCoordinates.size.height
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { pdfViewModel.openSearch() }) {
                        Icon(
                            imageVector = Icons.Default.Search, 
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        val shareUri = if (uri.scheme == "file") {
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                File(uri.path!!)
                            )
                        } else {
                            uri
                        }
                        
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            type = "application/pdf"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share, 
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = pdfViewModel.isSearchActive,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                modifier = Modifier.fillMaxWidth().imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = pdfViewModel.searchQuery,
                            onValueChange = { pdfViewModel.onSearchQueryChanged(it) },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            placeholder = { Text("Search...") },
                            trailingIcon = {
                                if (pdfViewModel.isSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { /* Done by onValueChange */ })
                        )
                        
                        IconButton(onClick = { pdfViewModel.closeSearch() }) {
                            Icon(
                                imageVector = Icons.Default.Close, 
                                contentDescription = "Close Search",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    if (pdfViewModel.searchResults.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "${pdfViewModel.currentSearchIndex + 1} / ${pdfViewModel.searchResults.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { pdfViewModel.previousSearchResult() }) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp, 
                                    contentDescription = "Previous",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { pdfViewModel.nextSearchResult() }) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown, 
                                    contentDescription = "Next",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        AnimatedVisibility(
            visible = pdfViewModel.isTopBarVisible && pdfViewModel.activeSelection == null && !pdfViewModel.isSearchActive,
            enter = (if (isLandscape) slideInVertically(initialOffsetY = { it }) else slideInHorizontally(initialOffsetX = { it })) + fadeIn(),
            exit = (if (isLandscape) slideOutVertically(targetOffsetY = { it }) else slideOutHorizontally(targetOffsetX = { it })) + fadeOut(),
            modifier = Modifier
                .align(if (isLandscape) Alignment.BottomCenter else Alignment.TopEnd)
                .padding(
                    end = if (isLandscape) 16.dp else 16.dp,
                    start = if (isLandscape) 16.dp else 0.dp,
                    top = if (isLandscape) 0.dp else if (topControlsHeightPx > 0) with(density) { topControlsHeightPx.toDp() } + 24.dp else 160.dp,
                    bottom = if (isLandscape) {
                        if (bottomControlsHeightPx > 0) with(density) { bottomControlsHeightPx.toDp() } + 16.dp else 80.dp
                    } else {
                        if (bottomControlsHeightPx > 0) with(density) { bottomControlsHeightPx.toDp() } + 24.dp else 48.dp
                    }
                )
        ) {
            val scrollProgress by derivedStateOf {
                if (pdfViewModel.pageCount > 0) {
                    val info = zoomPanState.listState.layoutInfo
                    val firstVisible = info.visibleItemsInfo.firstOrNull()
                    if (firstVisible != null) {
                        if (!zoomPanState.listState.canScrollForward) {
                            1f
                        } else if (!zoomPanState.listState.canScrollBackward) {
                            0f
                        } else {
                            val index = firstVisible.index
                            val offset = zoomPanState.listState.firstVisibleItemScrollOffset
                            val size = firstVisible.size.coerceAtLeast(1).toFloat()
                            val fraction = offset / size
                            
                            val viewportHeight = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                            val viewportPages = viewportHeight / size
                            val maxScrollablePages = max(0.001f, pdfViewModel.pageCount.toFloat() - viewportPages)
                            
                            ((index + fraction) / maxScrollablePages).coerceIn(0f, 1f)
                        }
                    } else 0f
                } else 0f
            }
            
            val currentPage by derivedStateOf {
                if (pdfViewModel.pageCount > 0) {
                    val info = zoomPanState.listState.layoutInfo
                    val firstVisible = info.visibleItemsInfo.firstOrNull()
                    if (firstVisible != null) {
                        firstVisible.index + 1
                    } else 1
                } else 0
            }
            
            var sliderSizePx by remember { mutableStateOf(0) }

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val targetIndex = max(0, currentPage - 2)
                                zoomPanState.listState.animateScrollToItem(targetIndex)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowLeft, 
                            contentDescription = "Previous page",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), CircleShape)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (pdfViewModel.pageCount > 0) {
                            Text(
                                text = "$currentPage",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .onGloballyPositioned { sliderSizePx = it.size.width },
                            contentAlignment = Alignment.Center
                        ) {
                            Slider(
                                value = scrollProgress,
                                onValueChange = { newValue ->
                                    pdfViewModel.updateSliderInteraction(true)
                                    coroutineScope.launch {
                                        val info = zoomPanState.listState.layoutInfo
                                        val firstVisible = info.visibleItemsInfo.firstOrNull()
                                        val size = firstVisible?.size?.coerceAtLeast(1)?.toFloat() ?: 100f
                                        val viewportHeight = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                                        val viewportPages = viewportHeight / size
                                        val maxScrollablePages = max(0.001f, pdfViewModel.pageCount.toFloat() - viewportPages)
                                        
                                        val targetContinuousPage = newValue * maxScrollablePages
                                        val targetIndex = targetContinuousPage.toInt().coerceIn(0, max(0, pdfViewModel.pageCount - 1))
                                        val fraction = targetContinuousPage - targetIndex
                                        
                                        val pageSize = pdfViewModel.pageSizes[targetIndex]
                                        val aspectRatio = if (pageSize != null && pageSize.height > 0) pageSize.width.toFloat() / pageSize.height else 1f / 1.414f
                                        val renderedWidthPx = screenWidthPx * zoomPanState.scale
                                        val estimatedHeightPx = renderedWidthPx / aspectRatio
                                        
                                        val targetOffsetPx = (fraction * estimatedHeightPx).toInt()
                                        zoomPanState.listState.scrollToItem(targetIndex, targetOffsetPx)
                                    }
                                },
                                onValueChangeFinished = { pdfViewModel.updateSliderInteraction(false) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }

                        if (pdfViewModel.pageCount > 0) {
                            Text(
                                text = "${pdfViewModel.pageCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val targetIndex = min(pdfViewModel.pageCount - 1, currentPage)
                                zoomPanState.listState.animateScrollToItem(targetIndex)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight, 
                            contentDescription = "Next page",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val targetIndex = max(0, currentPage - 2)
                                zoomPanState.listState.animateScrollToItem(targetIndex)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Previous page",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), CircleShape)
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (pdfViewModel.pageCount > 0) {
                            Text(
                                text = "$currentPage",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .onGloballyPositioned { layoutCoordinates ->
                                    sliderSizePx = layoutCoordinates.size.height
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (sliderSizePx > 0) {
                                val sliderHeightDp = with(density) { sliderSizePx.toDp() }
                                Slider(
                                    value = scrollProgress,
                                    onValueChange = { newValue ->
                                        pdfViewModel.updateSliderInteraction(true)
                                        coroutineScope.launch {
                                            val info = zoomPanState.listState.layoutInfo
                                            val firstVisible = info.visibleItemsInfo.firstOrNull()
                                            val size = firstVisible?.size?.coerceAtLeast(1)?.toFloat() ?: 100f
                                            val viewportHeight = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                                            val viewportPages = viewportHeight / size
                                            val maxScrollablePages = max(0.001f, pdfViewModel.pageCount.toFloat() - viewportPages)
                                            
                                            val targetContinuousPage = newValue * maxScrollablePages
                                            val targetIndex = targetContinuousPage.toInt().coerceIn(0, max(0, pdfViewModel.pageCount - 1))
                                            val fraction = targetContinuousPage - targetIndex
                                            
                                            val pageSize = pdfViewModel.pageSizes[targetIndex]
                                            val aspectRatio = if (pageSize != null && pageSize.height > 0) pageSize.width.toFloat() / pageSize.height else 1f / 1.414f
                                            val renderedWidthPx = screenWidthPx * zoomPanState.scale
                                            val estimatedHeightPx = renderedWidthPx / aspectRatio
                                            
                                            val targetOffsetPx = (fraction * estimatedHeightPx).toInt()
                                            zoomPanState.listState.scrollToItem(targetIndex, targetOffsetPx)
                                        }
                                    },
                                    onValueChangeFinished = { pdfViewModel.updateSliderInteraction(false) },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.requiredWidth(sliderHeightDp).rotate(90f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            }
                        }

                        if (pdfViewModel.pageCount > 0) {
                            Text(
                                text = "${pdfViewModel.pageCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val targetIndex = min(pdfViewModel.pageCount - 1, currentPage)
                                zoomPanState.listState.animateScrollToItem(targetIndex)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Next page",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}


@Composable
fun PdfPageItem(
    pageIndex: Int,
    pdfViewModel: PdfViewerViewModel,
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
    
    var pageBoundsInWindow by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(pageIndex, pdfViewModel.isLoaded) {
        if (!pdfViewModel.isLoaded) return@LaunchedEffect
        thumbnailBitmapState.value = pdfViewModel.renderPage(pageIndex, 200)
        pdfViewModel.requestTextExtraction(pageIndex)
    }

    LaunchedEffect(pageIndex, pdfViewModel.isLoaded) {
        if (!pdfViewModel.isLoaded) return@LaunchedEffect
        delay(150)
        baseBitmapState.value = pdfViewModel.renderPage(pageIndex, context.resources.displayMetrics.widthPixels)
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    LaunchedEffect(pageIndex, scale, isFlinging, pdfViewModel.isLoaded) {
        snapshotFlow { pageBoundsInWindow }
            .filter { pdfViewModel.isLoaded && scale > 1.05f }
            .debounce(if (isFlinging) 400 else 150)
            .collectLatest { bounds ->
                val windowRect = Rect(0f, 0f, viewportWidthPx.toFloat(), viewportHeightPx.toFloat())
                val intersection = windowRect.intersect(bounds)
                if (intersection.isEmpty || intersection.width < 10 || intersection.height < 10) return@collectLatest

                val localX = (intersection.left - bounds.left) / bounds.width
                val localY = (intersection.top - bounds.top) / bounds.height
                val localWidth = intersection.width / bounds.width
                val localHeight = intersection.height / bounds.height

                val normalizedRect = android.graphics.RectF(localX, localY, localX + localWidth, localY + localHeight)
                val tile = pdfViewModel.renderTile(
                    index = pageIndex,
                    tileWidth = intersection.width.roundToInt(),
                    tileHeight = intersection.height.roundToInt(),
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
                pageBoundsInWindow = Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height)
            }
    ) {
        val thumb = thumbnailBitmapState.value
        val base = baseBitmapState.value
        
        if (thumb != null) {
            val aspectRatio = thumb.width.toFloat() / thumb.height.toFloat()
            Canvas(
                modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio)
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

                val activeSelection = pdfViewModel.activeSelection
                if (activeSelection != null && pageIndex in activeSelection.startPageIndex..activeSelection.endPageIndex) {
                    val pageText = pdfViewModel.pageTextCache[pageIndex]
                    val pageSize = pdfViewModel.pageSizes[pageIndex]
                    if (pageText != null && pageSize != null && pageSize.width > 0 && pageSize.height > 0) {
                        val ratioX = size.width / pageSize.width.toFloat()
                        val ratioY = size.height / pageSize.height.toFloat()
                        
                        val start = (if (pageIndex == activeSelection.startPageIndex) activeSelection.startIndex else 0).coerceIn(pageText.characters.indices)
                        val end = (if (pageIndex == activeSelection.endPageIndex) activeSelection.endIndex else pageText.characters.lastIndex).coerceIn(pageText.characters.indices)
                        val selectedChars = if (start <= end) pageText.characters.subList(start, end + 1) else emptyList()
                        
                        val lineBoundsMap = mutableMapOf<TextCharacter, android.graphics.RectF>()
                        val lineRectList = mutableListOf<android.graphics.RectF>()
                        val lines = mutableListOf<MutableList<TextCharacter>>()
                        
                        for (char in selectedChars) {
                            val rect = char.boundingBox
                            val matchingLine = lines.find { line ->
                                val lineRect = line.first().boundingBox
                                val overlap = min(rect.bottom, lineRect.bottom) - max(rect.top, lineRect.top)
                                overlap > (rect.height() * 0.5f)
                            }
                            if (matchingLine != null) matchingLine.add(char) else lines.add(mutableListOf(char))
                        }

                        for (line in lines) {
                            val minX = line.minOf { it.boundingBox.left }
                            val maxX = line.maxOf { it.boundingBox.right }
                            val minY = line.minOf { it.boundingBox.top }
                            val maxY = line.maxOf { it.boundingBox.bottom }
                            val paddingY = (maxY - minY) * 0.35f
                            val rect = android.graphics.RectF(minX, minY - paddingY, maxX, maxY + paddingY)
                            lineRectList.add(rect)
                            line.forEach { lineBoundsMap[it] = rect }
                        }
                        
                        val highlightColor = Color(0xFF0066CC).copy(alpha = 0.3f)
                        for (rect in lineRectList) {
                            drawRect(
                                color = highlightColor,
                                topLeft = Offset(rect.left * ratioX, rect.top * ratioY),
                                size = androidx.compose.ui.geometry.Size(rect.width() * ratioX, rect.height() * ratioY)
                            )
                        }

                        val handleColor = Color(0xFF0066CC)
                        if (pageIndex == activeSelection.startPageIndex) {
                            val startChar = pageText.characters.getOrNull(activeSelection.startIndex)
                            if (startChar != null) {
                                lineBoundsMap[startChar]?.let { lineRect ->
                                    val x = startChar.boundingBox.left * ratioX
                                    drawLine(handleColor, Offset(x, lineRect.top * ratioY), Offset(x, lineRect.bottom * ratioY), 2.dp.toPx())
                                    drawCircle(handleColor, 6.dp.toPx(), Offset(x, lineRect.bottom * ratioY + 6.dp.toPx()))
                                }
                            }
                        }
                        
                        if (pageIndex == activeSelection.endPageIndex) {
                            val endChar = pageText.characters.getOrNull(activeSelection.endIndex)
                            if (endChar != null) {
                                lineBoundsMap[endChar]?.let { lineRect ->
                                    val x = endChar.boundingBox.right * ratioX
                                    drawLine(handleColor, Offset(x, lineRect.top * ratioY), Offset(x, lineRect.bottom * ratioY), 2.dp.toPx())
                                    drawCircle(handleColor, 6.dp.toPx(), Offset(x, lineRect.bottom * ratioY + 6.dp.toPx()))
                                }
                            }
                        }
                    }
                }

                // Search highlighting
                if (pdfViewModel.isSearchActive && pdfViewModel.searchQuery.isNotEmpty()) {
                    val pageText = pdfViewModel.pageTextCache[pageIndex]
                    val pageSize = pdfViewModel.pageSizes[pageIndex]
                    if (pageText != null && pageSize != null && pageSize.width > 0 && pageSize.height > 0) {
                        val ratioX = size.width / pageSize.width.toFloat()
                        val ratioY = size.height / pageSize.height.toFloat()

                        val results = pdfViewModel.searchResults.filter { it.pageIndex == pageIndex }
                        results.forEach { result ->
                            val isActive = pdfViewModel.searchResults.getOrNull(pdfViewModel.currentSearchIndex) == result
                            val highlightColor = if (isActive) Color(0xFFFF9800).copy(alpha = 0.5f) else Color(0xFFFFFF00).copy(alpha = 0.4f)
                            
                            val startIndex = result.startIndex.coerceIn(pageText.characters.indices)
                            val endIndex = result.endIndex.coerceIn(pageText.characters.indices)
                            
                            val matchingChars = pageText.characters.subList(startIndex, endIndex + 1)
                            val lines = mutableListOf<MutableList<TextCharacter>>()
                            for (char in matchingChars) {
                                val matchingLine = lines.find { line ->
                                    val lineRect = line.first().boundingBox
                                    val overlap = min(char.boundingBox.bottom, lineRect.bottom) - max(char.boundingBox.top, lineRect.top)
                                    overlap > (char.boundingBox.height() * 0.5f)
                                }
                                if (matchingLine != null) matchingLine.add(char) else lines.add(mutableListOf(char))
                            }

                            for (line in lines) {
                                val minX = line.minOf { it.boundingBox.left }
                                val maxX = line.maxOf { it.boundingBox.right }
                                val minY = line.minOf { it.boundingBox.top }
                                val maxY = line.maxOf { it.boundingBox.bottom }
                                
                                val paddingY = (maxY - minY) * 0.15f
                                
                                drawRect(
                                    color = highlightColor,
                                    topLeft = Offset(minX * ratioX, (minY - paddingY) * ratioY),
                                    size = androidx.compose.ui.geometry.Size((maxX - minX) * ratioX, (maxY - minY + 2 * paddingY) * ratioY)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f / 1.414f).background(Color.LightGray))
        }
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
    decay: androidx.compose.animation.core.DecayAnimationSpec<Float>
) {
    var lastValue = value.toFloat()
    scroll {
        Animatable(initialValue = lastValue).animateDecay(initialVelocity, decay) {
            val delta = value - lastValue
            scrollBy(delta)
            lastValue = value
        }
    }
}

suspend fun androidx.compose.foundation.lazy.LazyListState.fling(
    initialVelocity: Float,
    decay: androidx.compose.animation.core.DecayAnimationSpec<Float>
) {
    var lastValue = 0f
    scroll {
        Animatable(initialValue = 0f).animateDecay(initialVelocity, decay) {
            val delta = value - lastValue
            scrollBy(delta)
            lastValue = value
        }
    }
}
