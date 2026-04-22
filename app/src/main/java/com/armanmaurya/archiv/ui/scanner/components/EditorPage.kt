package com.armanmaurya.archiv.ui.scanner.components

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.armanmaurya.archiv.bitmap.decodeSampledBitmap
import com.armanmaurya.archiv.bitmap.BitmapCache
import com.armanmaurya.archiv.bitmap.applyBitmapFilter
import com.armanmaurya.archiv.bitmap.rotateBitmapQuarterTurns
import com.armanmaurya.archiv.bitmap.warpBitmapWithQuad
import com.armanmaurya.archiv.ui.scanner.Mode
import com.armanmaurya.archiv.ui.scanner.PageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun EditorPage(
    page: Int,
    currentPage: Int,
    pageState: PageState,
    mode: Mode,
    editingBounds: List<PointF>,
    editingRotationTurns: Int,
    editingFilterMode: Int,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedElementKeyForUri: (Uri) -> String,
    onEditingBoundsChange: (List<PointF>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uri = pageState.uri
    val cacheKey = uri.toString()
    val initialPreviewBitmap = remember(uri) {
        BitmapCache.getPreview(cacheKey)
    }
    val initialTransitionBitmap = remember(uri, pageState.cropBounds) {
        val thumbnailCacheKey = "$cacheKey-thumb-${pageState.cropBounds.hashCode()}"
        BitmapCache.getThumbnail(thumbnailCacheKey)
    }
    var baseBitmap by remember(uri) { mutableStateOf<Bitmap?>(initialPreviewBitmap) }
    var displayedBitmap by remember(uri) { mutableStateOf<ImageBitmap?>(initialTransitionBitmap?.asImageBitmap()) }
    val isCurrentPage = page == currentPage
    val isCropMode = mode == Mode.CROP
    val isFilterMode = mode == Mode.FILTER
    val pageBounds = if (isCropMode && isCurrentPage) editingBounds else pageState.cropBounds
    val persistedRotationTurns = ((pageState.rotation % 4) + 4) % 4
    val effectiveRotationTurns =
        if (isCropMode && isCurrentPage) editingRotationTurns else persistedRotationTurns
    val persistedFilterMode = pageState.filter
    val effectiveFilterMode =
        if (isFilterMode && isCurrentPage) editingFilterMode else persistedFilterMode
    val processedBitmapCache = remember(uri) { mutableMapOf<String, Bitmap>() }
    var containerWidth by remember(uri) { mutableStateOf(0) }
    var containerHeight by remember(uri) { mutableStateOf(0) }
    var cropZoom by remember(uri) { mutableStateOf(1f) }
    var cropOffset by remember(uri) { mutableStateOf(Offset.Zero) }
    var viewZoom by remember(uri) { mutableStateOf(1f) }
    var viewOffset by remember(uri) { mutableStateOf(Offset.Zero) }
    val isEditableCropPage = isCropMode && isCurrentPage
    val isZoomableViewPage = !isCropMode && isCurrentPage
    val cropGestureState = rememberTransformableState { zoomChange, panChange, _ ->
        when {
            isEditableCropPage -> {
                val nextZoom = (cropZoom * zoomChange).coerceIn(1f, MAX_CROP_ZOOM)
                val nextOffset = clampOffset(
                    offset = if (nextZoom > 1f) cropOffset + panChange else Offset.Zero,
                    zoom = nextZoom,
                    containerWidth = containerWidth,
                    containerHeight = containerHeight
                )
                cropZoom = nextZoom
                cropOffset = nextOffset
            }

            isZoomableViewPage -> {
                val nextZoom = (viewZoom * zoomChange).coerceIn(1f, MAX_VIEW_ZOOM)
                val nextOffset = clampOffset(
                    offset = if (nextZoom > 1f) viewOffset + panChange else Offset.Zero,
                    zoom = nextZoom,
                    containerWidth = containerWidth,
                    containerHeight = containerHeight
                )
                viewZoom = nextZoom
                viewOffset = nextOffset
            }
        }
    }

    LaunchedEffect(isEditableCropPage) {
        if (!isEditableCropPage) {
            cropZoom = 1f
            cropOffset = Offset.Zero
        }
    }

    LaunchedEffect(
        containerWidth,
        containerHeight,
        cropZoom,
        viewZoom,
        isEditableCropPage,
        isZoomableViewPage
    ) {
        if (isEditableCropPage) {
            cropOffset = clampOffset(
                offset = cropOffset,
                zoom = cropZoom,
                containerWidth = containerWidth,
                containerHeight = containerHeight
            )
        } else if (isZoomableViewPage) {
            viewOffset = clampOffset(
                offset = viewOffset,
                zoom = viewZoom,
                containerWidth = containerWidth,
                containerHeight = containerHeight
            )
        }
    }

    LaunchedEffect(uri) {
        BitmapCache.getPreview(cacheKey)?.let { baseBitmap = it }

        val bitmap = withContext(Dispatchers.IO) { decodeSampledBitmap(context, uri, 2200) }
        if (bitmap != null) {
            BitmapCache.putPreview(cacheKey, bitmap)
            baseBitmap = bitmap
        }
    }

    LaunchedEffect(
        baseBitmap,
        pageBounds,
        mode,
        isCurrentPage,
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

        if (isCropMode && isCurrentPage) {
            displayedBitmap = processed.asImageBitmap()
        } else {
            val warped = withContext(Dispatchers.Default) { warpBitmapWithQuad(processed, pageBounds) }
            displayedBitmap = (warped ?: processed).asImageBitmap()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        displayedBitmap?.let { bitmapToShow ->
            val cropSidePadding = if (isEditableCropPage) CROP_SIDE_PADDING else 0.dp
            val sharedImageModifier =
                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            state = rememberSharedContentState(sharedElementKeyForUri(uri)),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                } else {
                    Modifier
                }
            val cropTransformModifier =
                if (isEditableCropPage || isZoomableViewPage) {
                    Modifier
                        .graphicsLayer {
                            val activeZoom = if (isEditableCropPage) cropZoom else viewZoom
                            val activeOffset = if (isEditableCropPage) cropOffset else viewOffset
                            scaleX = activeZoom
                            scaleY = activeZoom
                            translationX = activeOffset.x
                            translationY = activeOffset.y
                        }
                } else {
                    Modifier
                }
            val cropGestureModifier =
                if (isEditableCropPage || isZoomableViewPage) {
                    Modifier.transformable(
                        state = cropGestureState,
                        canPan = { _ ->
                            if (isEditableCropPage) cropZoom > 1f else viewZoom > 1f
                        }
                    )
                } else {
                    Modifier
                }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = cropSidePadding)
                    .onSizeChanged {
                        containerWidth = it.width
                        containerHeight = it.height
                    }
                    .then(cropGestureModifier)
                    .then(cropTransformModifier),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmapToShow,
                    contentDescription = "Captured image ${page + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().then(sharedImageModifier)
                )
            }

            if (isEditableCropPage && baseBitmap != null) {
                val overlaySourceBitmap = baseBitmap!!
                val isOddQuarterTurn = (effectiveRotationTurns and 1) == 1
                val overlayImageWidth =
                    if (isOddQuarterTurn) overlaySourceBitmap.height else overlaySourceBitmap.width
                val overlayImageHeight =
                    if (isOddQuarterTurn) overlaySourceBitmap.width else overlaySourceBitmap.height
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = cropSidePadding)
                        .then(cropTransformModifier)
                ) {
                    CropOverlay(
                        bounds = editingBounds,
                        imageWidth = overlayImageWidth,
                        imageHeight = overlayImageHeight,
                        containerWidth = containerWidth,
                        containerHeight = containerHeight,
                        onChange = onEditingBoundsChange
                    )
                }
            }
        }
    }
}

private const val MAX_CROP_ZOOM = 4f
private const val MAX_VIEW_ZOOM = 4f
private val CROP_SIDE_PADDING = 20.dp

private fun clampOffset(
    offset: Offset,
    zoom: Float,
    containerWidth: Int,
    containerHeight: Int
): Offset {
    if (zoom <= 1f || containerWidth <= 0 || containerHeight <= 0) {
        return Offset.Zero
    }
    val maxX = containerWidth * (zoom - 1f) / 2f
    val maxY = containerHeight * (zoom - 1f) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}
