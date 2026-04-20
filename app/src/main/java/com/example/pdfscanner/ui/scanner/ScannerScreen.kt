package com.example.pdfscanner.ui.scanner

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import com.example.pdfscanner.ui.scanner.components.CameraPreview
import com.example.pdfscanner.ui.scanner.components.GalleryButton
import com.example.pdfscanner.ui.scanner.components.ThumbnailStrip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScannerScreen(
        viewModel: ScannerViewModel,
        onOpenEditor: (Int) -> Unit,
        onExitScanner: () -> Unit,
        scrollToIndexHint: Int? = null,
        onScrollHintConsumed: () -> Unit = {},
        sharedTransitionScope: SharedTransitionScope? = null,
        animatedVisibilityScope: AnimatedVisibilityScope? = null,
        sharedElementKeyForUri: (Uri) -> String = { uri -> "page-$uri" }
) {
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    val pages = viewModel.pages
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var captureRequestKey by remember { mutableStateOf(0L) }
    var openLastRequestToken by remember { mutableStateOf(0L) }
    var scannerErrorMessage by remember { mutableStateOf<String?>(null) }
    var isImportBusy by remember { mutableStateOf(false) }
    var isCameraBusy by remember { mutableStateOf(false) }
    var isAutoEdgeDetectionEnabled by remember { mutableStateOf(true) }
    val isScreenBusy = viewModel.isSavingPdf || isImportBusy || isCameraBusy

    val visibleErrorMessage = scannerErrorMessage ?: viewModel.saveErrorMessage

    fun handleExitAttempt() {
        if (isScreenBusy) return
        if (pages.isEmpty()) {
            onExitScanner()
        } else {
            showExitConfirmation = true
        }
    }

    BackHandler {
        if (showExitConfirmation) {
            showExitConfirmation = false
        } else {
            handleExitAttempt()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                isAutoEdgeDetectionEnabled = isAutoEdgeDetectionEnabled,
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

            AutoEdgeDetectionButton(
                isEnabled = isAutoEdgeDetectionEnabled,
                enabled = !isScreenBusy,
                onToggle = { isAutoEdgeDetectionEnabled = !isAutoEdgeDetectionEnabled }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                ThumbnailStrip(
                    pages = pages,
                    onOpenEditor = onOpenEditor,
                    onDelete = { index -> pendingDeleteIndex = index },
                    onReorder = { from, to -> viewModel.reorderPages(from, to) },
                    scrollToIndexHint = scrollToIndexHint,
                    onScrollHintConsumed = onScrollHintConsumed,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedElementKeyForUri = sharedElementKeyForUri,
                    openAfterScrollRequestToken = openLastRequestToken,
                    openAfterScrollIndex = pages.lastIndex
                )
            }

            OpenLastImageButton(
                onOpenLast = { openLastRequestToken++ },
                enabled = !isScreenBusy && pages.isNotEmpty(),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(56.dp)
                    .padding(vertical = 8.dp)
            )
        }
    } // Column (main background container)

    // Overlays for the whole screen
    if (isScreenBusy) {
        Box(
                modifier = Modifier.fillMaxSize().background(
                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)
                ),
                contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
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

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Discard current scan?") },
            text = { Text("You have captured images. Exit scanner and discard them?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmation = false
                        viewModel.clearPages()
                        onExitScanner()
                    }
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AutoEdgeDetectionButton(
    isEnabled: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isEnabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isEnabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(enabled = enabled) { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isEnabled) "Auto" else "Off",
            color = textColor,
            fontSize = 11.sp
        )
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
fun OpenLastImageButton(
    onOpenLast: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (enabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val iconColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(enabled = enabled) { onOpenLast() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Open last image",
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
    }
}
