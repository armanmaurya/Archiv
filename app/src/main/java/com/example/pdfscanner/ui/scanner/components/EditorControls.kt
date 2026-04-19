package com.example.pdfscanner.ui.scanner.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.pdfscanner.bitmap.FilterMode

enum class EditorControlMode {
    Default,
    Crop,
    Filter
}

@Composable
fun EditorControls(
    visible: Boolean,
    mode: EditorControlMode,
    selectedFilter: Int,
    nonePreview: ImageBitmap?,
    bwPreview: ImageBitmap?,
    sepiaPreview: ImageBitmap?,
    onStartEdit: () -> Unit,
    onStartFilter: () -> Unit,
    onResetCrop: () -> Unit,
    onRotate: () -> Unit,
    onApplyCrop: () -> Unit,
    onClearFilter: () -> Unit,
    onSelectBw: () -> Unit,
    onSelectSepia: () -> Unit,
    onApplyFilter: () -> Unit,
    bottomContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AnimatedContent(targetState = mode, label = "editor-controls-mode") { currentMode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (currentMode) {
                            EditorControlMode.Default -> {
                                FilledTonalButton(onClick = onStartEdit, shape = RoundedCornerShape(50)) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("  Crop & Rotate")
                                }

                            FilledTonalButton(onClick = onStartFilter, shape = RoundedCornerShape(50)) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  Filter")
                            }
                        }

                            EditorControlMode.Crop -> {
                                FilledTonalButton(onClick = onResetCrop, shape = RoundedCornerShape(50)) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("  No Crop")
                                }

                                FilledTonalButton(onClick = onRotate, shape = RoundedCornerShape(50)) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("  Rotate")
                                }
                            }

                            EditorControlMode.Filter -> {
                                NoFilterThumbnailButton(
                                    preview = nonePreview,
                                    selected = selectedFilter == FilterMode.NONE,
                                    onClick = onClearFilter
                                )

                                FilterThumbnailButton(
                                    preview = bwPreview,
                                    label = "B&W",
                                    selected = selectedFilter == FilterMode.BW,
                                    onClick = onSelectBw
                                )

                                FilterThumbnailButton(
                                    preview = sepiaPreview,
                                    label = "Sepia",
                                    selected = selectedFilter == FilterMode.SEPIA,
                                    onClick = onSelectSepia
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = mode == EditorControlMode.Default && bottomContent != null,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    bottomContent?.invoke()
                }
            }
        }
    }
}

@Composable
fun EditorControls(
    isEditMode: Boolean,
    isFilterMode: Boolean,
    onStartEdit: () -> Unit,
    onStartFilter: () -> Unit,
    onApplyCrop: () -> Unit,
    onApplyFilter: () -> Unit
) {
    EditorControls(
        visible = true,
        mode = when {
            isEditMode -> EditorControlMode.Crop
            isFilterMode -> EditorControlMode.Filter
            else -> EditorControlMode.Default
        },
        selectedFilter = FilterMode.BW,
        nonePreview = null,
        bwPreview = null,
        sepiaPreview = null,
        onStartEdit = onStartEdit,
        onStartFilter = onStartFilter,
        onResetCrop = {},
        onRotate = {},
        onApplyCrop = onApplyCrop,
        onClearFilter = {},
        onSelectBw = {},
        onSelectSepia = {},
        onApplyFilter = onApplyFilter
    )
}

@Composable
private fun NoFilterThumbnailButton(
    preview: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterOptionColumn(
        preview = preview,
        label = "None",
        selected = selected,
        contentDescription = "No Filter",
        onClick = onClick
    )
}

@Composable
private fun FilterThumbnailButton(
    preview: ImageBitmap?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterOptionColumn(
        preview = preview,
        label = label,
        selected = selected,
        contentDescription = label,
        onClick = onClick
    )
}

@Composable
private fun FilterOptionColumn(
    preview: ImageBitmap?,
    label: String,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    val labelColor =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledTonalButton(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.size(FILTER_THUMBNAIL_SIZE),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor =
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                contentColor =
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private val FILTER_THUMBNAIL_SIZE = 64.dp
