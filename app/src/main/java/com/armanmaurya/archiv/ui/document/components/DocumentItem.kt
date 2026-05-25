package com.armanmaurya.archiv.ui.document.components

import android.graphics.Bitmap
import android.graphics.fonts.Font
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.ui.res.stringResource
import com.armanmaurya.archiv.R
import com.armanmaurya.archiv.domain.model.Document
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentItem(
    document: Document,
    actionEnabled: Boolean,
    compact: Boolean = false,
    onOpen: () -> Unit,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onEditTags: () -> Unit,
    onRename: () -> Unit = {},
    onTagClick: (String) -> Unit = {},
    selectedTags: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(dampingRatio = 0.8f))
            .combinedClickable(
                enabled = actionEnabled,
                onClick = onOpen,
                onLongClick = onOpenWith
            ),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (compact) {
            Column(modifier = Modifier.fillMaxWidth()) {
                PdfThumbnail(
                    document = document,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(255.dp)  // A4 ratio (1:√2) relative to 180 dp column width
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = formatDisplayName(document.fileName),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = formatFileSize(document.fileSizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTimestamp(document.modifiedAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TagRow(
                        tags = document.tags,
                        maxVisible = 2,
                        onTagClick = onTagClick,
                        selectedTags = selectedTags,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconButton(onClick = onShare, enabled = actionEnabled) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Share",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onExport, enabled = actionEnabled) {
                            Icon(
                                Icons.Filled.GetApp,
                                contentDescription = "Export",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDelete, enabled = actionEnabled) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TagOverflowMenu(
                            onOpenWith = onOpenWith,
                            onEditTags = onEditTags,
                            onRename = onRename,
                            enabled = actionEnabled
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                PdfThumbnail(
                    document = document,
                    modifier = Modifier
                        .width(100.dp)
                        .height(110.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        text = formatDisplayName(document.fileName),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "${formatFileSize(document.fileSizeBytes)} • ${formatTimestamp(document.modifiedAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TagRow(
                        tags = document.tags,
                        maxVisible = 3,
                        onTagClick = onTagClick,
                        selectedTags = selectedTags,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconButton(onClick = onShare, enabled = actionEnabled) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Share",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onExport, enabled = actionEnabled) {
                            Icon(
                                Icons.Filled.GetApp,
                                contentDescription = "Export",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDelete, enabled = actionEnabled) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TagOverflowMenu(
                            onOpenWith = onOpenWith,
                            onEditTags = onEditTags,
                            onRename = onRename,
                            enabled = actionEnabled
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagRow(
    tags: List<String>,
    maxVisible: Int,
    onTagClick: (String) -> Unit = {},
    selectedTags: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return
    val visibleTags = tags.take(maxVisible)
    val hiddenCount = tags.size - visibleTags.size
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        visibleTags.forEach { tag ->
            TagChip(
                label = tag,
                onClick = { onTagClick(tag) },
                isSelected = tag in selectedTags
            )
        }
        if (hiddenCount > 0) {
            TagChip(label = "+$hiddenCount")
        }
    }
}

@Composable
private fun TagChip(
    label: String,
    onClick: () -> Unit = {},
    isSelected: Boolean = false
) {
    if (isSelected) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .clickable(onClick = onClick)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TagOverflowMenu(
    onOpenWith: () -> Unit,
    onEditTags: () -> Unit,
    onRename: () -> Unit = {},
    enabled: Boolean
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { menuExpanded = true },
            enabled = enabled
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.document_tags_more_actions),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Open with") },
                onClick = {
                    menuExpanded = false
                    onOpenWith()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.document_tags_title)) },
                onClick = {
                    menuExpanded = false
                    onEditTags()
                }
            )
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    menuExpanded = false
                    onRename()
                }
            )
        }
    }
}

@Composable
private fun PdfThumbnail(
    document: Document,
    modifier: Modifier = Modifier
) {
    var thumbnail by remember(document.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(document.filePath) {
        val bitmap = withContext(Dispatchers.IO) {
            renderPdfFirstPageThumbnail(
                filePath = document.filePath,
                targetWidthPx = THUMBNAIL_WIDTH_PX,
                targetHeightPx = THUMBNAIL_HEIGHT_PX
            )
        }
        thumbnail = bitmap?.asImageBitmap()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        val image = thumbnail
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "PDF thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PDF",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun renderPdfFirstPageThumbnail(
    filePath: String,
    targetWidthPx: Int,
    targetHeightPx: Int
): Bitmap? {
    if (targetWidthPx <= 0 || targetHeightPx <= 0) return null
    val file = File(filePath)
    if (!file.exists() || !file.isFile) return null

    val fileDescriptor = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    } catch (_: IOException) {
        return null
    }

    return try {
        val renderer = PdfRenderer(fileDescriptor)
        try {
            if (renderer.pageCount <= 0) {
                return null
            }
            val page = renderer.openPage(0)
            try {
                val pageWidth = page.width.coerceAtLeast(1)
                val pageHeight = page.height.coerceAtLeast(1)
                val scale = minOf(
                    targetWidthPx.toFloat() / pageWidth.toFloat(),
                    targetHeightPx.toFloat() / pageHeight.toFloat()
                )
                val bitmapWidth = (pageWidth * scale).toInt().coerceAtLeast(1)
                val bitmapHeight = (pageHeight * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(0xFFFFFFFF.toInt())
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        } finally {
            renderer.close()
        }
    } catch (_: IOException) {
        null
    } finally {
        fileDescriptor.close()
    }
}

private fun formatTimestamp(timestampMillis: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(timestampMillis))
}

private fun formatDisplayName(fileName: String): String {
    return if (fileName.endsWith(".pdf", ignoreCase = true)) {
        fileName.dropLast(4)
    } else {
        fileName
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroup = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroup.toDouble())
    return String.format("%.1f %s", value, units[digitGroup])
}

private const val THUMBNAIL_WIDTH_PX = 216
private const val THUMBNAIL_HEIGHT_PX = 306  // A4 ratio: 216 × √2 ≈ 306

