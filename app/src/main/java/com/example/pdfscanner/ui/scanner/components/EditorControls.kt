package com.example.pdfscanner.ui.scanner.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
    bwPreview: ImageBitmap?,
    sepiaPreview: ImageBitmap?,
    onStartEdit: () -> Unit,
    onStartFilter: () -> Unit,
    onDelete: () -> Unit,
    onResetCrop: () -> Unit,
    onRotate: () -> Unit,
    onApplyCrop: () -> Unit,
    onClearFilter: () -> Unit,
    onSelectBw: () -> Unit,
    onSelectSepia: () -> Unit,
    onApplyFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
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

                            FilledTonalButton(onClick = onDelete, shape = RoundedCornerShape(50)) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  Delete")
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

                            FilledTonalButton(onClick = onApplyCrop, shape = RoundedCornerShape(50)) {
                                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  Done")
                            }
                        }

                        EditorControlMode.Filter -> {
                            FilledTonalButton(onClick = onClearFilter, shape = RoundedCornerShape(50)) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  No Filter")
                            }

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

                            FilledTonalButton(onClick = onApplyFilter, shape = RoundedCornerShape(50)) {
                                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
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
        bwPreview = null,
        sepiaPreview = null,
        onStartEdit = onStartEdit,
        onStartFilter = onStartFilter,
        onDelete = {},
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
