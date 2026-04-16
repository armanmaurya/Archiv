package com.example.pdfscanner.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.pdfscanner.decodeScaledBitmap
import com.example.pdfscanner.image.BitmapMemoryCache
import com.example.pdfscanner.image.fullImageBounds
import com.example.pdfscanner.image.orderCorners
import com.example.pdfscanner.image.warpBitmapWithQuad
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FullScreenImagePager(
        imageUris: List<Uri>,
        cropBoundsByUri: Map<String, List<PointF>>,
        rotationTurnsByUri: Map<String, Int>,
        filterModeByUri: Map<String, Int>,
        onUpdateCropBounds: (String, List<PointF>) -> Unit,
        onUpdateRotationTurns: (String, Int) -> Unit,
        onUpdateFilterMode: (String, Int) -> Unit,
        onRemovePage: (Int) -> Unit,
        initialPage: Int,
        onDismiss: (Int) -> Unit,
        sharedTransitionScope: SharedTransitionScope? = null,
        animatedVisibilityScope: AnimatedVisibilityScope? = null,
        sharedElementKeyForUri: (Uri) -> String = { uri -> "page-$uri" }
) {
    if (imageUris.isEmpty()) return

    val safeInitialPage = initialPage.coerceIn(0, imageUris.lastIndex)
    val pagerState =
            rememberPagerState(initialPage = safeInitialPage, pageCount = { imageUris.size })
    var isEditMode by remember { mutableStateOf(false) }
    var isFilterMode by remember { mutableStateOf(false) }
    var editingBounds by remember { mutableStateOf(fullImageBounds()) }
    var editingRotationTurns by remember { mutableIntStateOf(0) }
    var editingFilterMode by remember { mutableIntStateOf(FILTER_MODE_NONE) }
    BackHandler {
        if (isEditMode) {
            val currentUri = imageUris.getOrNull(pagerState.currentPage)?.toString()
            if (currentUri != null) {
                editingBounds = orderCorners(cropBoundsByUri[currentUri] ?: fullImageBounds())
            }
            isEditMode = false
        } else if (isFilterMode) {
            isFilterMode = false
        } else {
            onDismiss(pagerState.currentPage)
        }
    }
    val context = LocalContext.current
    var controlsVisible by remember { mutableStateOf(false) }
    var filterPreviewBaseBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pagerState.currentPage) {
        isEditMode = false
        isFilterMode = false
    }

    LaunchedEffect(Unit) {
        controlsVisible = false
        delay(180)
        controlsVisible = true
    }

    LaunchedEffect(pagerState.currentPage, imageUris) {
        val currentUri = imageUris.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        val cacheKey = currentUri.toString()
        val cached =
                BitmapMemoryCache.getThumbnail(cacheKey) ?: BitmapMemoryCache.getPreview(cacheKey)
        val loaded =
                cached
                        ?: withContext(Dispatchers.IO) {
                            decodeScaledBitmap(context, currentUri, 420)
                        }
        if (loaded != null) {
            BitmapMemoryCache.putThumbnail(cacheKey, loaded)
            filterPreviewBaseBitmap = loaded
        } else {
            filterPreviewBaseBitmap = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {
        HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isEditMode && !isFilterMode,
                modifier = Modifier.fillMaxSize()
        ) { page ->
            val uri = imageUris[page]
            var baseBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
            var displayedBitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
            val cacheKey = uri.toString()
            val bounds = cropBoundsByUri[cacheKey] ?: fullImageBounds()
            val pageBounds =
                    if (isEditMode && page == pagerState.currentPage) editingBounds else bounds
            val persistedRotationTurns = ((rotationTurnsByUri[cacheKey] ?: 0) % 4 + 4) % 4
            val effectiveRotationTurns =
                    if (isEditMode && page == pagerState.currentPage) editingRotationTurns
                    else persistedRotationTurns
            val persistedFilterMode = filterModeByUri[cacheKey] ?: FILTER_MODE_NONE
            val effectiveFilterMode =
                    if (isFilterMode && page == pagerState.currentPage) editingFilterMode
                    else persistedFilterMode
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
                if (isEditMode) {
                    displayedBitmap = processed.asImageBitmap()
                } else {
                    val warped =
                            withContext(Dispatchers.Default) {
                                warpBitmapWithQuad(processed, pageBounds)
                            }
                    displayedBitmap = (warped ?: processed).asImageBitmap()
                }
            }

            Box(
                    modifier =
                            Modifier.fillMaxSize().onSizeChanged {
                                containerWidth = it.width
                                containerHeight = it.height
                            },
                    contentAlignment = Alignment.Center
            ) {
                displayedBitmap?.let { bitmapToShow ->
                    val sharedImageModifier =
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedElement(
                                            state =
                                                    rememberSharedContentState(
                                                            sharedElementKeyForUri(uri)
                                                    ),
                                            animatedVisibilityScope = animatedVisibilityScope
                                    )
                                }
                            } else {
                                Modifier
                            }
                    Image(
                            bitmap = bitmapToShow,
                            contentDescription = "Captured image ${page + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().then(sharedImageModifier)
                    )

                    if (isEditMode && page == pagerState.currentPage && baseBitmap != null) {
                        val fitted =
                                computeFittedRect(
                                        containerWidth = containerWidth.toFloat(),
                                        containerHeight = containerHeight.toFloat(),
                                        imageWidth = baseBitmap!!.width.toFloat(),
                                        imageHeight = baseBitmap!!.height.toFloat()
                                )
                        val pointsOnScreen =
                                editingBounds.map { point ->
                                    PointF(
                                            fitted.offsetX + point.x * fitted.width,
                                            fitted.offsetY + point.y * fitted.height
                                    )
                                }

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (pointsOnScreen.size == 4) {
                                val path =
                                        Path().apply {
                                            moveTo(pointsOnScreen[0].x, pointsOnScreen[0].y)
                                            lineTo(pointsOnScreen[1].x, pointsOnScreen[1].y)
                                            lineTo(pointsOnScreen[2].x, pointsOnScreen[2].y)
                                            lineTo(pointsOnScreen[3].x, pointsOnScreen[3].y)
                                            close()
                                        }
                                drawPath(
                                        path = path,
                                        color = Color(0xFF2196F3).copy(alpha = 0.9f),
                                        style = Stroke(width = 6f)
                                )
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            pointsOnScreen.forEachIndexed { index, point ->
                                Box(
                                        modifier =
                                                Modifier.offset {
                                                    IntOffset(
                                                            (point.x - 14.dp.toPx()).roundToInt(),
                                                            (point.y - 14.dp.toPx()).roundToInt()
                                                    )
                                                }
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(Color(0xFF2196F3))
                                                        .pointerInput(
                                                                cacheKey,
                                                                index,
                                                                fitted.width,
                                                                fitted.height
                                                        ) {
                                                            detectDragGestures { change, dragAmount
                                                                ->
                                                                change.consume()
                                                                if (editingBounds.size != 4 ||
                                                                                fitted.width <=
                                                                                        1f ||
                                                                                fitted.height <= 1f
                                                                )
                                                                        return@detectDragGestures
                                                                val currentScreen =
                                                                        PointF(
                                                                                fitted.offsetX +
                                                                                        editingBounds[
                                                                                                        index]
                                                                                                .x *
                                                                                                fitted.width,
                                                                                fitted.offsetY +
                                                                                        editingBounds[
                                                                                                        index]
                                                                                                .y *
                                                                                                fitted.height
                                                                        )
                                                                val nextScreenX =
                                                                        (currentScreen.x +
                                                                                        dragAmount
                                                                                                .x)
                                                                                .coerceIn(
                                                                                        fitted.offsetX,
                                                                                        fitted.offsetX +
                                                                                                fitted.width
                                                                                )
                                                                val nextScreenY =
                                                                        (currentScreen.y +
                                                                                        dragAmount
                                                                                                .y)
                                                                                .coerceIn(
                                                                                        fitted.offsetY,
                                                                                        fitted.offsetY +
                                                                                                fitted.height
                                                                                )
                                                                val nextNormalized =
                                                                        PointF(
                                                                                ((nextScreenX -
                                                                                                fitted.offsetX) /
                                                                                                fitted.width)
                                                                                        .coerceIn(
                                                                                                0f,
                                                                                                1f
                                                                                        ),
                                                                                ((nextScreenY -
                                                                                                fitted.offsetY) /
                                                                                                fitted.height)
                                                                                        .coerceIn(
                                                                                                0f,
                                                                                                1f
                                                                                        )
                                                                        )
                                                                val updated =
                                                                        editingBounds
                                                                                .toMutableList()
                                                                updated[index] = nextNormalized
                                                                editingBounds = updated
                                                            }
                                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        IconButton(
                onClick = {
                    if (isEditMode) {
                        val currentUri = imageUris.getOrNull(pagerState.currentPage)?.toString()
                        if (currentUri != null) {
                            editingBounds =
                                    orderCorners(cropBoundsByUri[currentUri] ?: fullImageBounds())
                        }
                        isEditMode = false
                    } else if (isFilterMode) {
                        isFilterMode = false
                    } else {
                        onDismiss(pagerState.currentPage)
                    }
                },
                modifier =
                        Modifier.align(Alignment.TopStart)
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
                text = "${pagerState.currentPage + 1} / ${imageUris.size}",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier =
                        Modifier.align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(top = 24.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val bwPreview =
                remember(filterPreviewBaseBitmap) {
                    filterPreviewBaseBitmap?.let {
                        applyBitmapFilter(it, FILTER_MODE_BW).asImageBitmap()
                    }
                }
        val sepiaPreview =
                remember(filterPreviewBaseBitmap) {
                    filterPreviewBaseBitmap?.let {
                        applyBitmapFilter(it, FILTER_MODE_SEPIA).asImageBitmap()
                    }
                }

        AnimatedVisibility(
                visible = controlsVisible,
                modifier =
                        Modifier.align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    tonalElevation = 6.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                AnimatedContent(
                        targetState =
                                when {
                                    isEditMode -> "crop"
                                    isFilterMode -> "filter"
                                    else -> "default"
                                },
                        label = "bottom-bar-mode"
                ) { mode ->
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (mode == "default") {
                            FilledTonalButton(
                                    onClick = {
                                        val currentUri =
                                                imageUris
                                                        .getOrNull(pagerState.currentPage)
                                                        ?.toString()
                                        if (currentUri != null) {
                                            editingBounds =
                                                    orderCorners(
                                                            cropBoundsByUri[currentUri]
                                                                    ?: fullImageBounds()
                                                    )
                                            editingRotationTurns =
                                                    ((rotationTurnsByUri[currentUri]
                                                            ?: 0) % 4 + 4) % 4
                                            isFilterMode = false
                                            isEditMode = true
                                        }
                                    },
                                    shape = RoundedCornerShape(50)
                            ) {
                                Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                )
                                Text("  Crop & Rotate")
                            }

                            FilledTonalButton(
                                    onClick = {
                                        val currentUri =
                                                imageUris
                                                        .getOrNull(pagerState.currentPage)
                                                        ?.toString()
                                        if (currentUri != null) {
                                            editingFilterMode =
                                                    filterModeByUri[currentUri] ?: FILTER_MODE_NONE
                                            isEditMode = false
                                            isFilterMode = true
                                        }
                                    },
                                    shape = RoundedCornerShape(50)
                            ) {
                                Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                )
                                Text("  Filter")
                            }

                            FilledTonalButton(
                                    onClick = {
                                        val page = pagerState.currentPage
                                        val shouldDismiss = imageUris.size <= 1
                                        onRemovePage(page)
                                        if (shouldDismiss) {
                                            onDismiss(0)
                                        }
                                    },
                                    shape = RoundedCornerShape(50)
                            ) {
                                Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                )
                                Text("  Delete")
                            }
                        } else if (mode == "crop") {
                            FilledTonalButton(
                                    onClick = { editingBounds = fullImageBounds() },
                                    shape = RoundedCornerShape(50)
                            ) {
                                Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                )
                                Text("  No Crop")
                            }

                            FilledTonalButton(
                                    onClick = {
                                        editingBounds =
                                                orderCorners(rotateBoundsClockwise(editingBounds))
                                        editingRotationTurns = (editingRotationTurns + 1) % 4
                                    },
                                    shape = RoundedCornerShape(50)
                            ) {
                                Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                )
                                Text("  Rotate")
                            }

                            FilledTonalButton(
                                    onClick = {
                                        val currentUri =
                                                imageUris
                                                        .getOrNull(pagerState.currentPage)
                                                        ?.toString()
                                        if (currentUri != null) {
                                            onUpdateCropBounds(
                                                    currentUri,
                                                    orderCorners(editingBounds)
                                            )
                                            onUpdateRotationTurns(currentUri, editingRotationTurns)
                                            isEditMode = false
                                        }
                                    },
                                    shape = RoundedCornerShape(50)
                            ) {
                                Icon(
                                        Icons.Default.Done,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                )
                                Text("  Done")
                            }
                        } else {
                            FilledTonalButton(
                                    onClick = { editingFilterMode = FILTER_MODE_NONE },
                                    shape = RoundedCornerShape(50)
                            ) {
                                Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                )
                                Text("  No Filter")
                            }

                            FilterThumbnailButton(
                                    preview = bwPreview,
                                    label = "B&W",
                                    selected = editingFilterMode == FILTER_MODE_BW,
                                    onClick = { editingFilterMode = FILTER_MODE_BW }
                            )

                            FilterThumbnailButton(
                                    preview = sepiaPreview,
                                    label = "Sepia",
                                    selected = editingFilterMode == FILTER_MODE_SEPIA,
                                    onClick = { editingFilterMode = FILTER_MODE_SEPIA }
                            )

                            FilledTonalButton(
                                    onClick = {
                                        val currentUri =
                                                imageUris
                                                        .getOrNull(pagerState.currentPage)
                                                        ?.toString()
                                        if (currentUri != null) {
                                            onUpdateFilterMode(currentUri, editingFilterMode)
                                            isFilterMode = false
                                        }
                                    },
                                    shape = RoundedCornerShape(50)
                            ) {
                                Icon(
                                        Icons.Default.Done,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                )
                                Text("  Done")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterThumbnailButton(
        preview: ImageBitmap?,
        label: String,
        selected: Boolean,
        onClick: () -> Unit
) {
    FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(18.dp)) {
        if (preview != null) {
            Image(
                    bitmap = preview,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
            )
            Text(if (selected) "  $label ✓" else "  $label")
        } else {
            Text(if (selected) "$label ✓" else label)
        }
    }
}

private data class FittedRect(
        val width: Float,
        val height: Float,
        val offsetX: Float,
        val offsetY: Float
)

private fun computeFittedRect(
        containerWidth: Float,
        containerHeight: Float,
        imageWidth: Float,
        imageHeight: Float
): FittedRect {
    if (containerWidth <= 0f || containerHeight <= 0f || imageWidth <= 0f || imageHeight <= 0f) {
        return FittedRect(0f, 0f, 0f, 0f)
    }
    val scale = minOf(containerWidth / imageWidth, containerHeight / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    return FittedRect(
            width = width,
            height = height,
            offsetX = (containerWidth - width) / 2f,
            offsetY = (containerHeight - height) / 2f
    )
}

private fun rotateBoundsClockwise(points: List<PointF>): List<PointF> {
    if (points.size != 4) return points
    return points.map { point -> PointF(1f - point.y, point.x) }
}

private const val FILTER_MODE_NONE = 0
private const val FILTER_MODE_BW = 1
private const val FILTER_MODE_SEPIA = 2

private fun rotateBitmapQuarterTurns(bitmap: Bitmap, turns: Int): Bitmap {
    val normalized = ((turns % 4) + 4) % 4
    if (normalized == 0) return bitmap
    val matrix = Matrix().apply { postRotate(90f * normalized) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun applyBitmapFilter(bitmap: Bitmap, mode: Int): Bitmap {
    if (mode == FILTER_MODE_NONE) return bitmap
    val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(output)
    val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter =
                        when (mode) {
                            FILTER_MODE_BW ->
                                    ColorMatrixColorFilter(
                                            ColorMatrix().apply { setSaturation(0f) }
                                    )
                            FILTER_MODE_SEPIA ->
                                    ColorMatrixColorFilter(
                                            ColorMatrix(
                                                    floatArrayOf(
                                                            0.393f,
                                                            0.769f,
                                                            0.189f,
                                                            0f,
                                                            0f,
                                                            0.349f,
                                                            0.686f,
                                                            0.168f,
                                                            0f,
                                                            0f,
                                                            0.272f,
                                                            0.534f,
                                                            0.131f,
                                                            0f,
                                                            0f,
                                                            0f,
                                                            0f,
                                                            0f,
                                                            1f,
                                                            0f
                                                    )
                                            )
                                    )
                            else -> null
                        }
            }
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return output
}
