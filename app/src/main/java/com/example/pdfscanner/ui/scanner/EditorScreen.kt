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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.pdfscanner.bitmap.FilterMode
import com.example.pdfscanner.bitmap.decodeSampledBitmap
import com.example.pdfscanner.bitmap.BitmapCache
import com.example.pdfscanner.bitmap.applyBitmapFilter
import com.example.pdfscanner.bitmap.fullImageBounds
import com.example.pdfscanner.bitmap.orderCorners
import com.example.pdfscanner.bitmap.rotateBitmapQuarterTurns
import com.example.pdfscanner.bitmap.warpBitmapWithQuad
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
    val pdfStorage = remember(context) { ScannerPdfStorage(context) }
    val topChipColor = MaterialTheme.colorScheme.secondaryContainer
    val topChipContentColor = MaterialTheme.colorScheme.onSecondaryContainer
    val topChipHeight = 44.dp
    val topChipTextStyle = MaterialTheme.typography.titleMedium

    fun handleTopAction() {
        when (mode) {
            Mode.CROP -> applyCrop()
            Mode.FILTER -> applyFilter()
            Mode.DEFAULT -> viewModel.savePagesAsPdf(context, pdfStorage)
        }
    }

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
        val currentPageState = pages.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        val currentUri = currentPageState.uri
        val cacheKey = currentUri.toString()
        val normalizedRotation = ((currentPageState.rotation % 4) + 4) % 4
        val filterPreviewKey =
            "$cacheKey-filter-${currentPageState.cropBounds.hashCode()}-$normalizedRotation"
        val cached = BitmapCache.getThumbnail(filterPreviewKey)
        if (cached != null) {
            filterPreviewBaseBitmap = cached
            return@LaunchedEffect
        }

        val sourceBitmap = BitmapCache.getPreview(cacheKey)
            ?: withContext(Dispatchers.IO) { decodeSampledBitmap(context, currentUri, 420) }
        if (sourceBitmap != null) {
            BitmapCache.putPreview(cacheKey, sourceBitmap)
            val previewBitmap = withContext(Dispatchers.Default) {
                val rotated = rotateBitmapQuarterTurns(sourceBitmap, normalizedRotation)
                warpBitmapWithQuad(rotated, currentPageState.cropBounds) ?: rotated
            }
            BitmapCache.putThumbnail(filterPreviewKey, previewBitmap)
            filterPreviewBaseBitmap = previewBitmap
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
    val nonePreview = remember(filterPreviewBaseBitmap) {
        filterPreviewBaseBitmap?.asImageBitmap()
    }

    val controlMode = when (mode) {
        Mode.CROP -> EditorControlMode.Crop
        Mode.FILTER -> EditorControlMode.Filter
        Mode.DEFAULT -> EditorControlMode.Default
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 24.dp, start = 16.dp)
                .clip(CircleShape)
                .background(topChipColor)
        ) {
            IconButton(
                onClick = ::handleBack,
                modifier = Modifier.size(topChipHeight)
            ) {
                Icon(
                    imageVector =
                        if (mode != Mode.DEFAULT) Icons.Default.Close
                        else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (mode != Mode.DEFAULT) "Cancel tool" else "Back",
                    tint = topChipContentColor
                )
            }
        }

        TextButton(
            onClick = ::handleTopAction,
            enabled = !viewModel.isSavingPdf,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 24.dp, end = 16.dp)
                .height(topChipHeight)
                .clip(RoundedCornerShape(50))
                .background(topChipColor),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = topChipContentColor
            )
        ) {
            Text(
                text = when {
                    mode != Mode.DEFAULT -> "Done"
                    viewModel.isSavingPdf -> "Saving..."
                    else -> "Save"
                },
                style = topChipTextStyle
            )
        }

        StepIndicator(
            currentPage = pagerState.currentPage + 1,
            totalPages = pages.size,
            modifier = Modifier.align(Alignment.TopCenter),
            chipHeight = topChipHeight,
            textStyle = topChipTextStyle,
            containerColor = topChipColor,
            contentColor = topChipContentColor
        )

        EditorControls(
            visible = controlsVisible,
            mode = controlMode,
            selectedFilter = editingFilterMode,
            nonePreview = nonePreview,
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
