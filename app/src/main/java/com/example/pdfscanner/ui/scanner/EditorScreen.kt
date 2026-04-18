package com.example.pdfscanner.ui.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.example.pdfscanner.bitmap.FilterMode
import com.example.pdfscanner.bitmap.decodeSampledBitmap
import com.example.pdfscanner.bitmap.BitmapCache
import com.example.pdfscanner.bitmap.applyBitmapFilter
import com.example.pdfscanner.bitmap.fullImageBounds
import com.example.pdfscanner.bitmap.orderCorners
import com.example.pdfscanner.ui.scanner.components.EditorControlMode
import com.example.pdfscanner.ui.scanner.components.EditorControls
import com.example.pdfscanner.ui.scanner.components.EditorPage
import com.example.pdfscanner.ui.scanner.components.StepIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun EditorScreen (
    viewModel: ScannerViewModel,
    initialPage: Int,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedElementKeyForUri: (Uri) -> String = { uri -> "page-$uri" }
) {
    val pages = viewModel.pages
    if (pages.isEmpty()) {
        onBack()
        return
    }

    val safeInitialPage = initialPage.coerceIn(0, pages.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeInitialPage, pageCount = { pages.size })
    var mode by remember { mutableStateOf(Mode.DEFAULT) }
    var editingBounds by remember { mutableStateOf(fullImageBounds()) }
    var editingRotationTurns by remember { mutableIntStateOf(0) }
    var editingFilterMode by remember { mutableIntStateOf(FilterMode.NONE) }
    var controlsVisible by remember { mutableStateOf(false) }
    var filterPreviewBaseBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun getCurrentPage() = pages.getOrNull(pagerState.currentPage)

    fun startCrop() {
        val current = getCurrentPage() ?: return
        editingBounds = orderCorners(current.cropBounds)
        editingRotationTurns = ((current.rotation % 4) + 4) % 4
        mode = Mode.CROP
    }

    fun startFilter() {
        val current = getCurrentPage() ?: return
        editingFilterMode = current.filter
        mode = Mode.FILTER
    }

    fun applyCrop() {
        val current = getCurrentPage() ?: return
        viewModel.updateCrop(current.uri, orderCorners(editingBounds))
        viewModel.updateRotation(current.uri, editingRotationTurns)
        mode = Mode.DEFAULT
    }

    fun applyFilter() {
        val current = getCurrentPage() ?: return
        viewModel.updateFilter(current.uri, editingFilterMode)
        mode = Mode.DEFAULT
    }

    fun deleteCurrentPage() {
        val pageIndex = pagerState.currentPage
        val shouldDismiss = pages.size <= 1
        viewModel.removePage(pageIndex)
        if (shouldDismiss) {
            onBack()
        }
    }

    fun rotateCropClockwise() {
        editingBounds = orderCorners(rotateBoundsClockwise(editingBounds))
        editingRotationTurns = (editingRotationTurns + 1) % 4
    }

    fun handleBack() {
        when (mode) {
            Mode.CROP -> {
                val current = getCurrentPage() ?: return
                editingBounds = orderCorners(current.cropBounds)
                mode = Mode.DEFAULT
            }
            Mode.FILTER -> mode = Mode.DEFAULT
            Mode.DEFAULT -> onBack()
        }
    }

    BackHandler {
        handleBack()
    }

    val context = LocalContext.current

    LaunchedEffect(pagerState.currentPage) {
        mode = Mode.DEFAULT
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
        val cached = BitmapCache.getThumbnail(cacheKey) ?: BitmapCache.getPreview(cacheKey)
        val loaded = cached ?: withContext(Dispatchers.IO) { decodeSampledBitmap(context, currentUri, 420) }
        if (loaded != null) {
            BitmapCache.putThumbnail(cacheKey, loaded)
            filterPreviewBaseBitmap = loaded
        } else {
            filterPreviewBaseBitmap = null
        }
    }

    val bwPreview = remember(filterPreviewBaseBitmap) {
        filterPreviewBaseBitmap?.let { applyBitmapFilter(it, FilterMode.BW).asImageBitmap() }
    }
    val sepiaPreview = remember(filterPreviewBaseBitmap) {
        filterPreviewBaseBitmap?.let { applyBitmapFilter(it, FilterMode.SEPIA).asImageBitmap() }
    }

    val controlMode = when (mode) {
        Mode.CROP -> EditorControlMode.Crop
        Mode.FILTER -> EditorControlMode.Filter
        Mode.DEFAULT -> EditorControlMode.Default
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = mode == Mode.DEFAULT,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            EditorPage(
                page = page,
                currentPage = pagerState.currentPage,
                pageState = pages[page],
                mode = mode,
                editingBounds = editingBounds,
                editingRotationTurns = editingRotationTurns,
                editingFilterMode = editingFilterMode,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                sharedElementKeyForUri = sharedElementKeyForUri,
                onEditingBoundsChange = { editingBounds = it }
            )
        }

        IconButton(
            onClick = ::handleBack,
            modifier = Modifier.align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 24.dp, start = 16.dp)
        ) {
            Icon(
                imageVector =
                    if (mode != Mode.DEFAULT) Icons.Default.Close
                    else Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (mode != Mode.DEFAULT) "Cancel tool" else "Back",
                tint = Color.White
            )
        }

        StepIndicator(
            currentPage = pagerState.currentPage + 1,
            totalPages = pages.size,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        EditorControls(
            visible = controlsVisible,
            mode = controlMode,
            selectedFilter = editingFilterMode,
            bwPreview = bwPreview,
            sepiaPreview = sepiaPreview,
            onStartEdit = ::startCrop,
            onStartFilter = ::startFilter,
            onDelete = ::deleteCurrentPage,
            onResetCrop = { editingBounds = fullImageBounds() },
            onRotate = ::rotateCropClockwise,
            onApplyCrop = ::applyCrop,
            onClearFilter = { editingFilterMode = FilterMode.NONE },
            onSelectBw = { editingFilterMode = FilterMode.BW },
            onSelectSepia = { editingFilterMode = FilterMode.SEPIA },
            onApplyFilter = ::applyFilter,
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        )
    }
}

private fun rotateBoundsClockwise(points: List<PointF>): List<PointF> {
    if (points.size != 4) return points
    return points.map { point -> PointF(1f - point.y, point.x) }
}
