package com.armanmaurya.archiv.ui.scanner

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armanmaurya.archiv.R
import com.armanmaurya.archiv.bitmap.decodeSampledBitmap
import com.armanmaurya.archiv.data.repository.DocumentRepository
import com.armanmaurya.archiv.ml.corners.DocCornerDetector
import com.armanmaurya.archiv.ml.corners.DocCornerTFLiteRunner
import com.armanmaurya.archiv.ui.scanner.components.CameraPreview
import com.armanmaurya.archiv.ui.scanner.components.GalleryButton
import com.armanmaurya.archiv.ui.scanner.components.ThumbnailStrip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScannerScreen(
        viewModel: ScannerViewModel,
        sharedImages: List<Uri> = emptyList(),
        onSharedImagesProcessed: () -> Unit = {},
        onOpenEditor: (Int) -> Unit,
    onExitScanner: () -> Unit,
    onOpenDocumentList: () -> Unit,
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

    val repository = remember(context) { DocumentRepository(context.applicationContext) }

    var captureRequestKey by remember { mutableStateOf(0L) }
    var openLastRequestToken by remember { mutableStateOf(0L) }
    var scannerErrorMessage by remember { mutableStateOf<String?>(null) }
    var isImportBusy by remember { mutableStateOf(false) }
    var isCameraBusy by remember { mutableStateOf(false) }
    var isAutoEdgeDetectionEnabled by remember { mutableStateOf(true) }
    val isScreenBusy = viewModel.isSavingPdf || isImportBusy || isCameraBusy
    val isAutoCaptureEnabled = isAutoEdgeDetectionEnabled && !isScreenBusy

    val visibleErrorMessage = scannerErrorMessage ?: viewModel.saveErrorMessage

    val pendingSavedDocumentId = viewModel.pendingSavedDocumentId
    var navigatedToDocumentListAfterSave by remember { mutableStateOf(false) }

    fun handleImageImport(uris: List<Uri>) {
        if (uris.isEmpty()) return
        isImportBusy = true
        coroutineScope.launch {
            val copiedUris = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> copyUriToCache(context, uri) }
            }

            // Perform edge detection on each imported image if enabled
            if (isAutoEdgeDetectionEnabled && copiedUris.isNotEmpty()) {
                val detector = withContext(Dispatchers.Default) {
                    val runner = DocCornerTFLiteRunner.getInstance(context)
                    DocCornerDetector(runner)
                }

                copiedUris.forEach { uri ->
                    var detectedBounds: List<android.graphics.PointF>? = null
                    withContext(Dispatchers.Default) {
                        decodeSampledBitmap(context, uri)?.let { bitmap ->
                            try {
                                val result = detector.detect(bitmap, context, false)
                                if (result.success && result.cornersOriginalTLTRBRBL != null) {
                                    detectedBounds = result.cornersOriginalTLTRBRBL.map {
                                        android.graphics.PointF(
                                            (it[0] / bitmap.width).toFloat(),
                                            (it[1] / bitmap.height).toFloat()
                                        )
                                    }
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                    viewModel.addPage(uri, detectedBounds)
                }
            } else {
                copiedUris.forEach { uri -> viewModel.addPage(uri) }
            }

            if (copiedUris.isEmpty()) {
                scannerErrorMessage = "Unable to import selected images."
            }
            isImportBusy = false
        }
    }

    LaunchedEffect(sharedImages) {
        if (sharedImages.isNotEmpty()) {
            handleImageImport(sharedImages)
            onSharedImagesProcessed()
        }
    }

    LaunchedEffect(pendingSavedDocumentId) {
        if (pendingSavedDocumentId != null) {
            navigatedToDocumentListAfterSave = true
            viewModel.consumeSavedDocumentEvent()
            onOpenDocumentList()
        }
    }

    // Preload DocCornerDetector model on screen entry to avoid freeze on first frame
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.Default) {
            try {
                DocCornerTFLiteRunner.getInstanceAsync(context).get()
            } catch (e: Exception) {
                // Model preload failed, but processFrame has fallback logic
            }
        }
    }

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

    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native != null && native.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && native.action == android.view.KeyEvent.ACTION_DOWN) {
                    if (pages.isNotEmpty()) {
                        onOpenEditor(pages.lastIndex)
                        return@onKeyEvent true
                    }
                }
                false
            }
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
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
                isAutoCaptureEnabled = isAutoCaptureEnabled,
                onAutoCapture = { captureRequestKey++ },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bottom controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sharedShutterModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("scan_button"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            } else {
                Modifier
            }
            GalleryButton(
                onImagesSelected = { uris -> handleImageImport(uris) },
                enabled = !isScreenBusy
            )

            ShutterButton(
                onCapture = { captureRequestKey++ },
                enabled = !isScreenBusy,
                modifier = sharedShutterModifier
            )

            AutoEdgeDetectionButton(
                isEnabled = isAutoEdgeDetectionEnabled,
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
                        onOpenLast = { viewModel.savePagesAsPdf(context, repository) },
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
                title = { Text(stringResource(R.string.scanner_delete_image_title)) },
                text = { Text(stringResource(R.string.scanner_delete_image_message)) },
                confirmButton = {
                    TextButton(
                            onClick = {
                                if (index in pages.indices) {
                                    viewModel.removePage(index)
                                }
                                pendingDeleteIndex = null
                            }
                    ) { Text(stringResource(R.string.scanner_delete_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteIndex = null }) { Text(stringResource(R.string.scanner_exit_cancel)) }
                }
        )
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text(stringResource(R.string.scanner_exit_title)) },
            text = { Text(stringResource(R.string.scanner_exit_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmation = false
                        viewModel.clearPages()
                        onExitScanner()
                    }
                ) { Text(stringResource(R.string.scanner_exit_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text(stringResource(R.string.scanner_exit_cancel)) }
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
            Icons.Filled.Check,
            contentDescription = "Save",
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
    }
}
