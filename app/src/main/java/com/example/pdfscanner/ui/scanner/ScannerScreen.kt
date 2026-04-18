package com.example.pdfscanner.ui.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.pdfscanner.ui.scanner.components.CameraPreview
import com.example.pdfscanner.ui.scanner.components.GalleryButton
import com.example.pdfscanner.ui.scanner.components.ThumbnailStrip
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScannerScreen(
        viewModel: ScannerViewModel,
        onOpenEditor: (Int) -> Unit,
        scrollToIndexHint: Int? = null,
        onScrollHintConsumed: () -> Unit = {},
        sharedTransitionScope: SharedTransitionScope? = null,
        animatedVisibilityScope: AnimatedVisibilityScope? = null,
        sharedElementKeyForUri: (Uri) -> String = { uri -> "page-$uri" }
) {
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    val pages = viewModel.pages
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pdfStorage = remember(context) { ScannerPdfStorage(context) }

    var captureRequestKey by remember { mutableStateOf(0L) }
    var scannerErrorMessage by remember { mutableStateOf<String?>(null) }
    var isImportBusy by remember { mutableStateOf(false) }
    var isCameraBusy by remember { mutableStateOf(false) }
    val isScreenBusy = viewModel.isSavingPdf || isImportBusy || isCameraBusy

    val visibleErrorMessage = scannerErrorMessage ?: viewModel.saveErrorMessage

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            CameraPreview(
                captureRequestKey = captureRequestKey,
                errorMessage = visibleErrorMessage,
                onDismissError = {
                    if (scannerErrorMessage != null) {
                        scannerErrorMessage = null
                    } else {
                        viewModel.dismissSaveError()
                    }
                },
                onCapture = { uri, bounds -> viewModel.addPage(uri, bounds) },
                onCameraBusyChange = { isBusy -> isCameraBusy = isBusy },
                onCameraError = { message -> scannerErrorMessage = message },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bottom controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GalleryButton(
                onImagesSelected = { uris ->
                    isImportBusy = true
                    coroutineScope.launch {
                        val copiedUris = withContext(Dispatchers.IO) {
                            uris.mapNotNull { uri -> copyUriToCache(context, uri) }
                        }
                        copiedUris.forEach { uri -> viewModel.addPage(uri) }
                        if (copiedUris.isEmpty()) {
                            scannerErrorMessage = "Unable to import selected images."
                        }
                        isImportBusy = false
                    }
                },
                enabled = !isScreenBusy
            )

            ShutterButton(
                onCapture = { captureRequestKey++ },
                enabled = !isScreenBusy
            )

            SavePdfButton(
                onSave = { viewModel.savePagesAsPdf(context, pdfStorage) },
                enabled = !isScreenBusy && pages.isNotEmpty()
            )
        }

        ThumbnailStrip(
            pages = pages,
            onOpenEditor = onOpenEditor,
            onDelete = { index -> pendingDeleteIndex = index },
            onReorder = { from, to -> viewModel.reorderPages(from, to) },
            scrollToIndexHint = scrollToIndexHint,
            onScrollHintConsumed = onScrollHintConsumed,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedElementKeyForUri = sharedElementKeyForUri
        )
    } // Column (main background container)

    // Overlays for the whole screen
    if (isScreenBusy) {
        Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(color = Color.White) }
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

private fun copyUriToCache(context: Context, uri: Uri): Uri? {
    return try {
        val cacheDirectory = File(context.cacheDir, "captured_pages").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
        val outputFile = File(
                cacheDirectory,
                "gallery_${System.currentTimeMillis()}_${uri.lastPathSegment?.takeLast(20)?.replace("/", "_")}.jpg"
        )
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outputFile).use { output -> input.copyTo(output) }
        } ?: run {
            Log.e("PickImages", "Could not open input stream for $uri")
            return null
        }
        if (outputFile.length() == 0L) {
            outputFile.delete()
            Log.e("PickImages", "Copied file is empty for $uri")
            return null
        }
        outputFile.toUri()
    } catch (e: Exception) {
        Log.e("PickImages", "Failed to copy URI $uri: ${e.message}", e)
        null
    }
}

@Composable
fun ShutterButton(
    onCapture: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(80.dp)
            .border(3.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(enabled = enabled) { onCapture() }
    )
}

@Composable
fun SavePdfButton(
    onSave: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clickable(enabled = enabled) { onSave() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = "Save PDF",
            tint = if (enabled) Color(0xFF67B5E8) else Color.Gray,
            modifier = Modifier.size(40.dp)
        )
    }
}
