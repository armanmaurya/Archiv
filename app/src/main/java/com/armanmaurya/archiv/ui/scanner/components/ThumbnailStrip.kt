package com.armanmaurya.archiv.ui.scanner.components

import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.armanmaurya.archiv.bitmap.BitmapCache
import com.armanmaurya.archiv.bitmap.decodeSampledBitmap
import com.armanmaurya.archiv.bitmap.warpBitmapWithQuad
import com.armanmaurya.archiv.ui.scanner.PageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ThumbnailStrip(
    pages: List<PageState>,
    onOpenEditor: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onReorder: (Int, Int) -> Unit,
    scrollToIndexHint: Int?,
    onScrollHintConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    enableReorder: Boolean = true,
    enabled: Boolean = true,
    autoScrollToLastOnSizeChange: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedElementKeyForUri: (Uri) -> String = { uri -> "page-$uri" },
    openAfterScrollRequestToken: Long = 0L,
    openAfterScrollIndex: Int = -1
) {
    val lazyListState = rememberLazyListState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var isSettling by remember { mutableStateOf(false) }

    LaunchedEffect(scrollToIndexHint, pages.size) {
        val target = scrollToIndexHint
        if (target != null && target in pages.indices) {
            lazyListState.scrollToItem(target)
            onScrollHintConsumed()
        }
    }

    LaunchedEffect(pages.size) {
        if (autoScrollToLastOnSizeChange && pages.isNotEmpty()) {
            lazyListState.animateScrollToItem(pages.size - 1)
        }
    }

    LaunchedEffect(selectedIndex, pages.size) {
        if (selectedIndex != null && selectedIndex in pages.indices) {
            lazyListState.animateScrollToItem(selectedIndex)
        }
    }

    LaunchedEffect(openAfterScrollRequestToken, openAfterScrollIndex, pages.size) {
        if (openAfterScrollRequestToken > 0L && openAfterScrollIndex in pages.indices) {
            lazyListState.animateScrollToItem(openAfterScrollIndex)
            onOpenEditor(openAfterScrollIndex)
        }
    }

    LaunchedEffect(isSettling) {
        if (isSettling) {
            animate(
                initialValue = dragOffsetX,
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) { value, _ -> dragOffsetX = value }
            draggingIndex = null
            isSettling = false
        }
    }

    LazyRow(
        state = lazyListState,
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(
                if (enableReorder) {
                    Modifier.pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                isSettling = false
                                val item = lazyListState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { info ->
                                        offset.x >= info.offset &&
                                            offset.x <= info.offset + info.size
                                    }
                                draggingIndex = item?.index
                                dragOffsetX = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX += dragAmount.x
                                val currentIndex = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                                val draggingItemInfo = visibleItems.firstOrNull { it.index == currentIndex }
                                    ?: return@detectDragGesturesAfterLongPress
                                val draggingCenter = draggingItemInfo.offset + draggingItemInfo.size / 2 + dragOffsetX.toInt()
                                val target = visibleItems.firstOrNull { info ->
                                    info.index != currentIndex &&
                                        draggingCenter >= info.offset &&
                                        draggingCenter <= info.offset + info.size
                                }
                                if (target != null) {
                                    dragOffsetX += (draggingItemInfo.offset - target.offset).toFloat()
                                    onReorder(currentIndex, target.index)
                                    draggingIndex = target.index
                                }
                            },
                            onDragEnd = { isSettling = true },
                            onDragCancel = { isSettling = true }
                        )
                    }
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(pages, key = { it.uri.toString() }) { pageState ->
            val pageIndex = pages.indexOf(pageState)
            val isDragging = draggingIndex == pageIndex
            val isSelected = selectedIndex == pageIndex
            Box(
                modifier = Modifier.then(
                    if (!isDragging) Modifier.animateItem() else Modifier
                )
            ) {
                ThumbnailItem(
                    pageState = pageState,
                    pageIndex = pageIndex,
                    isDragging = isDragging,
                    dragOffsetX = dragOffsetX,
                    isSelected = isSelected,
                    enabled = enabled,
                    onOpenEditor = onOpenEditor,
                    onDelete = onDelete,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedElementKeyForUri = sharedElementKeyForUri
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ThumbnailItem(
    pageState: PageState,
    pageIndex: Int,
    isDragging: Boolean,
    dragOffsetX: Float,
    isSelected: Boolean,
    enabled: Boolean,
    onOpenEditor: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedElementKeyForUri: (Uri) -> String = { uri -> "page-$uri" }
) {
    val uri = pageState.uri
    val bounds = pageState.cropBounds
    var imageBitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    var hasError by remember(uri) { mutableStateOf(false) }
    val context = LocalContext.current
    val cacheKey = uri.toString()
    val thumbnailCacheKey = "$cacheKey-thumb-${bounds.hashCode()}"

    LaunchedEffect(uri, bounds) {
        BitmapCache.getThumbnail(thumbnailCacheKey)?.let {
            imageBitmap = it.asImageBitmap()
            return@LaunchedEffect
        }
        BitmapCache.getPreview(cacheKey)?.let {
            val warped = warpBitmapWithQuad(it, bounds)
            val preview = warped ?: it
            BitmapCache.putThumbnail(thumbnailCacheKey, preview)
            imageBitmap = preview.asImageBitmap()
            return@LaunchedEffect
        }

        val bitmap = withContext(Dispatchers.IO) {
            try {
                Log.d("ThumbnailLoading", "Attempting to load URI: $uri")
                Log.d("ThumbnailLoading", "URI scheme: ${uri.scheme}, path: ${uri.path}")

                var decodedBitmap = decodeSampledBitmap(context, uri, 400)

                if (decodedBitmap == null && uri.scheme == "file") {
                    val filePath = uri.path
                    if (filePath != null) {
                        Log.d("ThumbnailLoading", "Trying direct file path: $filePath")
                        val file = java.io.File(filePath)
                        if (file.exists()) {
                            Log.d("ThumbnailLoading", "File exists: true, size: ${file.length()} bytes")
                            decodedBitmap = android.graphics.BitmapFactory.decodeFile(filePath)
                        } else {
                            Log.e("ThumbnailLoading", "File does not exist: $filePath")
                        }
                    }
                }

                if (decodedBitmap != null) {
                    Log.d("ThumbnailLoading", "Successfully decoded bitmap: ${decodedBitmap.width}x${decodedBitmap.height}")
                }
                decodedBitmap
            } catch (e: Exception) {
                Log.e("ThumbnailLoading", "Exception loading URI $uri: ${e.message}", e)
                null
            }
        }

        if (bitmap != null) {
            val warped = warpBitmapWithQuad(bitmap, bounds)
            val preview = warped ?: bitmap
            BitmapCache.putThumbnail(thumbnailCacheKey, preview)
            imageBitmap = preview.asImageBitmap()
        } else {
            Log.e("ThumbnailLoading", "Failed to decode bitmap for URI: $uri")
            hasError = true
        }
    }

    Box(
        modifier = Modifier
            .zIndex(if (isDragging) 1f else 0f)
            .height(80.dp)
            .width(60.dp)
            .graphicsLayer {
                translationX = if (isDragging) dragOffsetX else 0f
                scaleX = if (isDragging) 1.08f else 1f
                scaleY = if (isDragging) 1.08f else 1f
                alpha = if (isDragging) 0.85f else 1f
                shadowElevation = if (isDragging) 8f else 0f
            }
            .border(
                width = 2.dp,
                color = when {
                    isDragging -> Color.White
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .clickable(enabled = enabled && !hasError && !isDragging) {
                if (pageIndex >= 0) {
                    onOpenEditor(pageIndex)
                }
            }
    ) {
        if (hasError) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Error loading",
                tint = Color.Red,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            imageBitmap?.let {
                val sharedImageModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
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
                    bitmap = it,
                    contentDescription = "Captured Page",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(sharedImageModifier)
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable {
                    if (pageIndex >= 0) onDelete(pageIndex)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove page",
                tint = Color.White,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}
