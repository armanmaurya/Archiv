package com.armanmaurya.archiv.ui.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderPdfScreen(
    viewModel: PdfToolsViewModel,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var outputName by remember { mutableStateOf("") }
    var showSaveSheet by remember { mutableStateOf(false) }
    val sourceUri = viewModel.selectedReorderUri

    LaunchedEffect(sourceUri) {
        if (sourceUri != null) {
            outputName = buildSuggestedReorderName(context, sourceUri)
        }
    }

    LaunchedEffect(viewModel.errorMessage) {
        val message = viewModel.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeErrorMessage()
    }

    LaunchedEffect(viewModel.infoMessage) {
        val message = viewModel.infoMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeInfoMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Reorder Pages") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showSaveSheet = true },
                        enabled = sourceUri != null &&
                            viewModel.reorderPageOrder.isNotEmpty() &&
                            !viewModel.isProcessing
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            if (viewModel.reorderPageOrder.isNotEmpty()) {
                val lazyGridState = rememberLazyGridState()
                val reorderableLazyGridState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
                    viewModel.moveReorderPage(from.index, to.index)
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    modifier = Modifier.fillMaxSize(),
                    state = lazyGridState,
                    horizontalArrangement = Arrangement.spacedBy(GRID_SPACING_DP),
                    verticalArrangement = Arrangement.spacedBy(GRID_SPACING_DP)
                ) {
                    items(
                        items = viewModel.reorderPageOrder,
                        key = { it } // The page index string/int is unique initially, but since we reorder ints we must rely on 'it' as the unique identifier for that page
                    ) { pageIndex ->
                        ReorderableItem(reorderableLazyGridState, key = pageIndex) { isDragging ->
                            val pageBitmap = rememberPdfPageBitmap(
                                context = context,
                                sourceUri = sourceUri,
                                pageIndex = pageIndex
                            )

                            val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)

                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(A4_PAGE_RATIO)
                                    .longPressDraggableHandle(),
                                elevation = androidx.compose.material3.CardDefaults.outlinedCardElevation(defaultElevation = elevation)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.White)
                                ) {
                                    if (pageBitmap != null) {
                                        Image(
                                            bitmap = pageBitmap.asImageBitmap(),
                                            contentDescription = "Page ${pageIndex + 1}",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Page ${pageIndex + 1}")
                                        }
                                    }

                                    Text(
                                        text = "Page ${pageIndex + 1}",
                                        modifier = Modifier.padding(8.dp),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    if (showSaveSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSaveSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Save Reordered PDF",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Output file name") },
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showSaveSheet = false }) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            viewModel.saveReordered(outputName)
                            showSaveSheet = false
                        },
                        enabled = outputName.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberPdfPageBitmap(
    context: Context,
    sourceUri: Uri?,
    pageIndex: Int
): Bitmap? {
    val bitmapState = produceState<Bitmap?>(initialValue = null, sourceUri, pageIndex) {
        value = if (sourceUri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                renderPdfPageThumbnail(context, sourceUri, pageIndex)
            }
        }
    }
    return bitmapState.value
}

private fun renderPdfPageThumbnail(context: Context, sourceUri: Uri, pageIndex: Int): Bitmap? {
    val fileDescriptor = try {
        context.contentResolver.openFileDescriptor(sourceUri, "r")
    } catch (_: Exception) {
        null
    } ?: return null

    return fileDescriptor.use { descriptor ->
        try {
            val renderer = PdfRenderer(descriptor)
            renderer.use { pdf ->
                if (pageIndex !in 0 until pdf.pageCount) return null
                val page = pdf.openPage(pageIndex)
                page.use {
                    val targetWidth = 700
                    val aspect = page.height.toFloat() / page.width.toFloat()
                    val targetHeight = (targetWidth * aspect).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            val name = cursor.getString(nameIndex)
            if (!name.isNullOrBlank()) {
                return name
            }
        }
    }
    return uri.lastPathSegment ?: uri.toString()
}

private fun buildSuggestedReorderName(context: Context, uri: Uri): String {
    val displayName = resolveDisplayName(context, uri)
    val baseName = if (displayName.endsWith(".pdf", ignoreCase = true)) {
        displayName.dropLast(4)
    } else {
        displayName
    }
    return "${baseName}_reorder"
}

private const val GRID_COLUMNS = 2
private const val A4_PAGE_RATIO = 1f / 1.4142f
private val GRID_SPACING_DP = 12.dp
