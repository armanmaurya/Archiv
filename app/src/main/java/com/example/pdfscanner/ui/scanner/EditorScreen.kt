package com.example.pdfscanner.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.example.pdfscanner.decodeScaledBitmap
import com.example.pdfscanner.image.BitmapMemoryCache
import com.example.pdfscanner.image.applyBitmapFilter
import com.example.pdfscanner.image.fullImageBounds
import com.example.pdfscanner.image.orderCorners
import com.example.pdfscanner.image.rotateBitmapQuarterTurns
import com.example.pdfscanner.image.warpBitmapWithQuad
import com.example.pdfscanner.ui.scanner.FILTER_MODE_BW
import com.example.pdfscanner.ui.scanner.FILTER_MODE_NONE
import com.example.pdfscanner.ui.scanner.FILTER_MODE_SEPIA
import com.example.pdfscanner.ui.scanner.ScannerViewModel
import com.example.pdfscanner.ui.scanner.components.CropOverlay
import com.example.pdfscanner.ui.scanner.components.EditorControlMode
import com.example.pdfscanner.ui.scanner.components.EditorControls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen (
    viewModel: ScannerViewModel,
    initialPage: Int,
    onBack: () -> Unit,
) {
    val pages = viewModel.pages
    if (pages.isEmpty()) {
        onBack()
        return
    }

    val safeInitialPage = initialPage.coerceIn(0, pages.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeInitialPage, pageCount = { pages.size })
    var isEditMode by remember { mutableStateOf(false) }
    var isFilterMode by remember { mutableStateOf(false) }
    var editingBounds by remember { mutableStateOf(fullImageBounds()) }
    var editingRotationTurns by remember { mutableIntStateOf(0) }
    var editingFilterMode by remember { mutableIntStateOf(FILTER_MODE_NONE) }
    var controlsVisible by remember { mutableStateOf(false) }
    var filterPreviewBaseBitmap by remember { mutableStateOf<Bitmap?>(null) }

    BackHandler {
        when {
            isEditMode -> {
                val current = pages.getOrNull(pagerState.currentPage)
                if (current != null) {
                    editingBounds = orderCorners(current.cropBounds)
                }
                isEditMode = false
            }
            isFilterMode -> isFilterMode = false
            else -> onBack()
        }
    }

    val context = LocalContext.current

    LaunchedEffect(pagerState.currentPage) {
        isEditMode = false
        isFilterMode = false
    }

    LaunchedEffect(pages.size) {
        if (pages.isEmpty()) {
            onBack()
        } else if (pagerState.currentPage > pages.lastIndex) {
            pagerState.animateScrollToPage(pages.lastIndex)
        }
    }

    LaunchedEffect(Unit) {
        controlsVisible = false
        delay(180)
        controlsVisible = true
    }

    LaunchedEffect(pagerState.currentPage, pages) {
        val currentUri = pages.getOrNull(pagerState.currentPage)?.uri ?: return@LaunchedEffect
        val cacheKey = currentUri.toString()
        val cached = BitmapMemoryCache.getThumbnail(cacheKey) ?: BitmapMemoryCache.getPreview(cacheKey)
        val loaded = cached ?: withContext(Dispatchers.IO) { decodeScaledBitmap(context, currentUri, 420) }
        if (loaded != null) {
            BitmapMemoryCache.putThumbnail(cacheKey, loaded)
            filterPreviewBaseBitmap = loaded
        } else {
            filterPreviewBaseBitmap = null
        }
    }

    val bwPreview = remember(filterPreviewBaseBitmap) {
        filterPreviewBaseBitmap?.let { applyBitmapFilter(it, FILTER_MODE_BW).asImageBitmap() }
    }
    val sepiaPreview = remember(filterPreviewBaseBitmap) {
        filterPreviewBaseBitmap?.let { applyBitmapFilter(it, FILTER_MODE_SEPIA).asImageBitmap() }
    }

    val controlMode = when {
        isEditMode -> EditorControlMode.Crop
        isFilterMode -> EditorControlMode.Filter
        else -> EditorControlMode.Default
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isEditMode && !isFilterMode,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageState = pages[page]
            val uri = pageState.uri
            val cacheKey = uri.toString()
            var baseBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
            var displayedBitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
            val pageBounds = if (isEditMode && page == pagerState.currentPage) editingBounds else pageState.cropBounds
            val persistedRotationTurns = ((pageState.rotation % 4) + 4) % 4
            val effectiveRotationTurns =
                if (isEditMode && page == pagerState.currentPage) editingRotationTurns else persistedRotationTurns
            val persistedFilterMode = pageState.filter
            val effectiveFilterMode =
                if (isFilterMode && page == pagerState.currentPage) editingFilterMode else persistedFilterMode
            val processedBitmapCache = remember(uri) { mutableMapOf<String, Bitmap>() }
            var containerWidth by remember(uri) { mutableStateOf(0) }
            var containerHeight by remember(uri) { mutableStateOf(0) }

            LaunchedEffect(uri) {
                BitmapMemoryCache.getPreview(cacheKey)?.let { baseBitmap = it }
                    ?: BitmapMemoryCache.getThumbnail(cacheKey)?.let { baseBitmap = it }

                val bitmap = withContext(Dispatchers.IO) { decodeScaledBitmap(context, uri, 2200) }
                if (bitmap != null) {
                    BitmapMemoryCache.putPreview(cacheKey, bitmap)
                    baseBitmap = bitmap
                }
            }

            LaunchedEffect(
                baseBitmap,
                pageBounds,
                isEditMode,
                page,
                pagerState.currentPage,
                effectiveRotationTurns,
                effectiveFilterMode
            ) {
                val current = baseBitmap ?: return@LaunchedEffect
                val processedKey = "$effectiveRotationTurns-$effectiveFilterMode"
                val processed =
                    processedBitmapCache.getOrPut(processedKey) {
                        val rotated = rotateBitmapQuarterTurns(current, effectiveRotationTurns)
                        applyBitmapFilter(rotated, effectiveFilterMode)
                    }

                if (isEditMode && page == pagerState.currentPage) {
                    displayedBitmap = processed.asImageBitmap()
                } else {
                    val warped = withContext(Dispatchers.Default) { warpBitmapWithQuad(processed, pageBounds) }
                    displayedBitmap = (warped ?: processed).asImageBitmap()
                }
            }

            Box(
                modifier = Modifier.fillMaxSize().onSizeChanged {
                    containerWidth = it.width
                    containerHeight = it.height
                },
                contentAlignment = Alignment.Center
            ) {
                displayedBitmap?.let { bitmapToShow ->
                    Image(
                        bitmap = bitmapToShow,
                        contentDescription = "Captured image ${page + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isEditMode && page == pagerState.currentPage && baseBitmap != null) {
                        CropOverlay(
                            bounds = editingBounds,
                            imageWidth = baseBitmap!!.width,
                            imageHeight = baseBitmap!!.height,
                            containerWidth = containerWidth,
                            containerHeight = containerHeight,
                            onChange = { editingBounds = it }
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = {
                if (isEditMode) {
                    val current = pages.getOrNull(pagerState.currentPage)
                    if (current != null) {
                        editingBounds = orderCorners(current.cropBounds)
                    }
                    isEditMode = false
                } else if (isFilterMode) {
                    isFilterMode = false
                } else {
                    onBack()
                }
            },
            modifier = Modifier.align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 24.dp, start = 16.dp)
        ) {
            Icon(
                imageVector =
                    if (isEditMode || isFilterMode) Icons.Default.Close
                    else Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (isEditMode || isFilterMode) "Cancel tool" else "Back",
                tint = Color.White
            )
        }

        Text(
            text = "${pagerState.currentPage + 1} / ${pages.size}",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        EditorControls(
            visible = controlsVisible,
            mode = controlMode,
            selectedFilter = editingFilterMode,
            bwPreview = bwPreview,
            sepiaPreview = sepiaPreview,
            onStartEdit = {
                val current = pages.getOrNull(pagerState.currentPage)
                if (current != null) {
                    editingBounds = orderCorners(current.cropBounds)
                    editingRotationTurns = ((current.rotation % 4) + 4) % 4
                    isFilterMode = false
                    isEditMode = true
                }
            },
            onStartFilter = {
                val current = pages.getOrNull(pagerState.currentPage)
                if (current != null) {
                    editingFilterMode = current.filter
                    isEditMode = false
                    isFilterMode = true
                }
            },
            onDelete = {
                val pageIndex = pagerState.currentPage
                val shouldDismiss = pages.size <= 1
                viewModel.removePage(pageIndex)
                if (shouldDismiss) {
                    onBack()
                }
            },
            onResetCrop = { editingBounds = fullImageBounds() },
            onRotate = {
                editingBounds = orderCorners(rotateBoundsClockwise(editingBounds))
                editingRotationTurns = (editingRotationTurns + 1) % 4
            },
            onApplyCrop = {
                val current = pages.getOrNull(pagerState.currentPage)
                if (current != null) {
                    viewModel.updateCrop(current.uri, orderCorners(editingBounds))
                    viewModel.updateRotation(current.uri, editingRotationTurns)
                    isEditMode = false
                }
            },
            onClearFilter = { editingFilterMode = FILTER_MODE_NONE },
            onSelectBw = { editingFilterMode = FILTER_MODE_BW },
            onSelectSepia = { editingFilterMode = FILTER_MODE_SEPIA },
            onApplyFilter = {
                val current = pages.getOrNull(pagerState.currentPage)
                if (current != null) {
                    viewModel.updateFilter(current.uri, editingFilterMode)
                    isFilterMode = false
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        )
    }
}

private fun rotateBoundsClockwise(points: List<android.graphics.PointF>): List<android.graphics.PointF> {
    if (points.size != 4) return points
    return points.map { point -> android.graphics.PointF(1f - point.y, point.x) }
}
