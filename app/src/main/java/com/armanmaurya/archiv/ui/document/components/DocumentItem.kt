package com.armanmaurya.archiv.ui.document.components

import android.graphics.Bitmap
import android.graphics.fonts.Font
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.armanmaurya.archiv.data.document.Document
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DocumentItem(
    document: Document,
    actionEnabled: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = actionEnabled, onClick = onOpen),
        shape = RoundedCornerShape(8.dp)
    ) {
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
                }
            }
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
private const val THUMBNAIL_HEIGHT_PX = 288

