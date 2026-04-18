package com.example.pdfscanner.ui.scanner.components

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import com.example.pdfscanner.decodeScaledBitmap
import com.example.pdfscanner.image.BitmapMemoryCache
import com.example.pdfscanner.image.applyBitmapFilter
import com.example.pdfscanner.image.rotateBitmapQuarterTurns
import com.example.pdfscanner.image.warpBitmapWithQuad
import com.example.pdfscanner.ui.scanner.Mode
import com.example.pdfscanner.ui.scanner.PageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
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
        BitmapMemoryCache.getPreview(cacheKey)
    }
    val initialTransitionBitmap = remember(uri, pageState.cropBounds) {
        val thumbnailCacheKey = "$cacheKey-thumb-${pageState.cropBounds.hashCode()}"
        BitmapMemoryCache.getThumbnail(thumbnailCacheKey)
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

    LaunchedEffect(uri) {
        BitmapMemoryCache.getPreview(cacheKey)?.let { baseBitmap = it }

        val bitmap = withContext(Dispatchers.IO) { decodeScaledBitmap(context, uri, 2200) }
        if (bitmap != null) {
            BitmapMemoryCache.putPreview(cacheKey, bitmap)
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
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
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
                            state = rememberSharedContentState(sharedElementKeyForUri(uri)),
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

            if (isCropMode && isCurrentPage && baseBitmap != null) {
                val overlaySourceBitmap = baseBitmap!!
                val isOddQuarterTurn = (effectiveRotationTurns and 1) == 1
                val overlayImageWidth =
                    if (isOddQuarterTurn) overlaySourceBitmap.height else overlaySourceBitmap.width
                val overlayImageHeight =
                    if (isOddQuarterTurn) overlaySourceBitmap.width else overlaySourceBitmap.height
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
