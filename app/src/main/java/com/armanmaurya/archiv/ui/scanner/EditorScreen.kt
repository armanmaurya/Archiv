package com.armanmaurya.archiv.ui.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.armanmaurya.archiv.bitmap.FilterMode
import com.armanmaurya.archiv.bitmap.decodeSampledBitmap
import com.armanmaurya.archiv.bitmap.BitmapCache
import com.armanmaurya.archiv.bitmap.applyBitmapFilter
import com.armanmaurya.archiv.bitmap.fullImageBounds
import com.armanmaurya.archiv.bitmap.orderCorners
import com.armanmaurya.archiv.bitmap.rotateBitmapQuarterTurns
import com.armanmaurya.archiv.bitmap.warpBitmapWithQuad
import com.armanmaurya.archiv.ui.scanner.components.EditorControlMode
import com.armanmaurya.archiv.ui.scanner.components.EditorControls
import com.armanmaurya.archiv.ui.scanner.components.EditorPage
import com.armanmaurya.archiv.ui.scanner.components.GalleryButton
import com.armanmaurya.archiv.ui.scanner.components.StepIndicator
import com.armanmaurya.archiv.ui.scanner.components.ThumbnailStrip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun EditorScreen (
    viewModel: ScannerViewModel,
    initialPage: Int,
    onBack: () -> Unit,
    onOpenDocumentList: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedElementKeyForUri: (Uri) -> String = { uri -> "page-$uri" }
) {
    val pages = viewModel.pages
    val pendingSavedDocumentId = viewModel.pendingSavedDocumentId
    var navigatedToDocumentListAfterSave by remember { mutableStateOf(false) }

    LaunchedEffect(pendingSavedDocumentId) {
        if (pendingSavedDocumentId != null) {
            navigatedToDocumentListAfterSave = true
            viewModel.consumeSavedDocumentEvent()
            onOpenDocumentList()
        }
    }

    if (pages.isEmpty()) {
        LaunchedEffect(pendingSavedDocumentId, navigatedToDocumentListAfterSave) {
            if (pendingSavedDocumentId == null && !navigatedToDocumentListAfterSave) {
                onBack()
            }
        }
        return
    }

    val safeInitialPage = initialPage.coerceIn(0, pages.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeInitialPage, pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(Mode.DEFAULT) }
    var editingBounds by remember { mutableStateOf(fullImageBounds()) }
    var editingRotationTurns by remember { mutableIntStateOf(0) }
    var editingFilterMode by remember { mutableIntStateOf(FilterMode.NONE) }
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var isImportBusy by remember { mutableStateOf(false) }
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
    val topControlsClearance = topChipHeight + 32.dp
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
        if (pagerState.currentPage > pages.lastIndex) {
            pagerState.animateScrollToPage(pages.lastIndex)
        }
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
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = mode == Mode.DEFAULT,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = topControlsClearance)
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

            EditorControls(
                visible = true,
                mode = controlMode,
                selectedFilter = editingFilterMode,
                nonePreview = nonePreview,
                bwPreview = bwPreview,
                sepiaPreview = sepiaPreview,
                onStartEdit = ::startCrop,
                onStartFilter = ::startFilter,
                onResetCrop = { editingBounds = fullImageBounds() },
                onRotate = ::rotateCropClockwise,
                onApplyCrop = ::applyCrop,
                onClearFilter = { editingFilterMode = FilterMode.NONE },
                onSelectBw = { editingFilterMode = FilterMode.BW },
                onSelectSepia = { editingFilterMode = FilterMode.SEPIA },
                onApplyFilter = ::applyFilter,
                bottomContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .padding(horizontal = 8.dp)
                    ) {
                        ThumbnailStrip(
                            pages = pages,
                            onOpenEditor = { index ->
                                if (index in pages.indices && index != pagerState.currentPage) {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                }
                            },
                            onDelete = { index -> pendingDeleteIndex = index },
                            onReorder = { _, _ -> },
                            scrollToIndexHint = null,
                            onScrollHintConsumed = {},
                            selectedIndex = pagerState.currentPage,
                            enableReorder = false,
                            enabled = mode == Mode.DEFAULT && !isImportBusy,
                            autoScrollToLastOnSizeChange = false,
                            sharedTransitionScope = null,
                            animatedVisibilityScope = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 64.dp)
                        )
                        GalleryButton(
                            onImagesSelected = { uris ->
                                isImportBusy = true
                                coroutineScope.launch {
                                    val copiedUris = withContext(Dispatchers.IO) {
                                        uris.mapNotNull { uri -> copyUriToCache(context, uri) }
                                    }
                                    copiedUris.forEach { uri -> viewModel.addPage(uri) }
                                    isImportBusy = false
                                }
                            },
                            enabled = mode == Mode.DEFAULT && !viewModel.isSavingPdf && !isImportBusy,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(56.dp)
                                .padding(vertical = 8.dp)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
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
            when {
                viewModel.isSavingPdf -> {
                    CircularProgressIndicator(
                        color = topChipContentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                }
                mode != Mode.DEFAULT -> {
                    Text(
                        text = "Done",
                        style = topChipTextStyle
                    )
                }
                else -> {
                    Text(
                        text = "Save",
                        style = topChipTextStyle
                    )
                }
            }
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

private fun rotateBoundsClockwise(points: List<PointF>): List<PointF> {
    if (points.size != 4) return points
    return points.map { point -> PointF(1f - point.y, point.x) }
}
