package com.armanmaurya.archiv.ui.document

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.armanmaurya.archiv.R
import com.armanmaurya.archiv.domain.model.Document
import com.armanmaurya.archiv.ui.document.components.ArchivSearchBar
import com.armanmaurya.archiv.ui.document.components.TagStrip
import com.armanmaurya.archiv.ui.document.components.DocumentItem

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun DocumentListScreen(
    viewModel: DocumentViewModel,
    onOpenScanner: () -> Unit,
    onOpenSettings: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val searchQuery by viewModel.searchQueryState.collectAsState()
    val isSearchExpanded by viewModel.isSearchExpandedState.collectAsState()
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsState()
    val sortOption by viewModel.sortOptionState.collectAsState()
    val isGridView by viewModel.isDocumentListGridView.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val selectedTags by viewModel.selectedTagsState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeleteDocumentId by remember { mutableStateOf<String?>(null) }
    var pendingExportDocumentId by remember { mutableStateOf<String?>(null) }
    var pendingTagDocument by remember { mutableStateOf<Document?>(null) }
    var pendingRenameDocument by remember { mutableStateOf<Document?>(null) }
    var tagInput by remember { mutableStateOf("") }
    var renameInput by remember { mutableStateOf("") }

    val exportPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val documentId = pendingExportDocumentId
        pendingExportDocumentId = null
        if (granted && documentId != null) {
            viewModel.exportDocument(documentId)
        } else if (documentId != null) {
            viewModel.onExportPermissionDenied()
        }
    }

    val importPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importDocuments(uris)
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
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val sharedFabModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        state = rememberSharedContentState("scan_button"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            } else {
                Modifier
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                SmallFloatingActionButton(
                    onClick = { importPdfLauncher.launch(arrayOf("application/pdf")) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PictureAsPdf,
                        contentDescription = stringResource(R.string.document_list_fab_import_pdf)
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = onOpenScanner,
                    modifier = sharedFabModifier,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.document_list_fab_scan),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        },
        topBar = {
            WindowInsets(0)
            ArchivSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                isExpanded = isSearchExpanded,
                onExpandedChange = { viewModel.setSearchExpanded(it) },
                isGridView = isGridView,
                onViewChange = {
                    viewModel.setDocumentListGridView(!isGridView!!)
                },
                onOpenSettings = onOpenSettings,
                sortOption = sortOption,
                onSortChange = { viewModel.setSortOption(it) },
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxWidth().padding(innerPadding)) {
            TagStrip(
                modifier = Modifier
                    .fillMaxWidth(),
                availableTags = availableTags,
                selectedTags = selectedTags,
                documents = documents,
                onToggleTag = { viewModel.toggleTagFilter(it) }
            )
            when {
            viewModel.isLoading && documents.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            isGridView == null -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (documents.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "No scans yet. Scan now?",
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        AnimatedContent(
                            targetState = isGridView == true,
                            transitionSpec = {
                                (slideInVertically(initialOffsetY = { -it }) + fadeIn()).togetherWith(
                                    slideOutVertically(targetOffsetY = { it }) + fadeOut()
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "viewModeTransition"
                        ) { gridView ->
                            if (!gridView) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        horizontal = 12.dp,
                                        vertical = 8.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        documents,
                                        key = { document -> document.id }
                                    ) { document ->
                                        DocumentItem(
                                            document = document,
                                            actionEnabled = !viewModel.isLoading,
                                            compact = false,
                                            onOpen = {
                                                viewModel.onDocumentOpened(document.id)
                                                val openIntent =
                                                    viewModel.createOpenIntent(document.id)
                                                if (openIntent != null) {
                                                    try {
                                                        context.startActivity(
                                                            Intent.createChooser(
                                                                openIntent,
                                                                "Open scan"
                                                            )
                                                        )
                                                    } catch (_: ActivityNotFoundException) {
                                                        viewModel.onOpenAppUnavailable()
                                                    }
                                                }
                                            },
                                            onShare = {
                                                val shareIntent =
                                                    viewModel.createShareIntent(document.id)
                                                if (shareIntent != null) {
                                                    context.startActivity(
                                                        Intent.createChooser(
                                                            shareIntent,
                                                            "Share scan"
                                                        )
                                                    )
                                                }
                                            },
                                            onExport = {
                                                if (requiresLegacyWritePermission() &&
                                                    !hasLegacyWritePermission(context)
                                                ) {
                                                    pendingExportDocumentId = document.id
                                                    exportPermissionLauncher.launch(
                                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                    )
                                                } else {
                                                    viewModel.exportDocument(document.id)
                                                }
                                            },
                                            onDelete = { pendingDeleteDocumentId = document.id },
                                            onEditTags = {
                                                pendingTagDocument = document
                                                tagInput = document.tags.joinToString(", ")
                                            },
                                            onRename = {
                                                pendingRenameDocument = document
                                                renameInput = if (document.fileName.endsWith(".pdf", true)) {
                                                    document.fileName.dropLast(4)
                                                } else document.fileName
                                            },
                                            onTagClick = { tag -> viewModel.toggleTagFilter(tag) },
                                            selectedTags = selectedTags,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .animateItem()
                                        )
                                    }
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 180.dp),
                                    modifier = Modifier
                                        .fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(
                                        count = documents.size,
                                        key = { index -> documents[index].id }
                                    ) { index ->
                                        val document = documents[index]
                                        DocumentItem(
                                            document = document,
                                            actionEnabled = !viewModel.isLoading,
                                            compact = true,
                                            onOpen = {
                                                viewModel.onDocumentOpened(document.id)
                                                val openIntent =
                                                    viewModel.createOpenIntent(document.id)
                                                if (openIntent != null) {
                                                    try {
                                                        context.startActivity(
                                                            Intent.createChooser(
                                                                openIntent,
                                                                "Open scan"
                                                            )
                                                        )
                                                    } catch (_: ActivityNotFoundException) {
                                                        viewModel.onOpenAppUnavailable()
                                                    }
                                                }
                                            },
                                            onShare = {
                                                val shareIntent =
                                                    viewModel.createShareIntent(document.id)
                                                if (shareIntent != null) {
                                                    context.startActivity(
                                                        Intent.createChooser(
                                                            shareIntent,
                                                            "Share scan"
                                                        )
                                                    )
                                                }
                                            },
                                            onExport = {
                                                if (requiresLegacyWritePermission() &&
                                                    !hasLegacyWritePermission(context)
                                                ) {
                                                    pendingExportDocumentId = document.id
                                                    exportPermissionLauncher.launch(
                                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                    )
                                                } else {
                                                    viewModel.exportDocument(document.id)
                                                }
                                            },
                                            onDelete = { pendingDeleteDocumentId = document.id },
                                            onEditTags = {
                                                pendingTagDocument = document
                                                tagInput = document.tags.joinToString(", ")
                                            },
                                            onRename = {
                                                pendingRenameDocument = document
                                                renameInput = if (document.fileName.endsWith(".pdf", true)) {
                                                    document.fileName.dropLast(4)
                                                } else document.fileName
                                            },
                                            onTagClick = { tag -> viewModel.toggleTagFilter(tag) },
                                            selectedTags = selectedTags,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .animateItem()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    pendingDeleteDocumentId?.let { documentId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteDocumentId = null },
            title = { Text(stringResource(R.string.document_delete_title)) },
            text = { Text(stringResource(R.string.document_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDocument(documentId)
                        pendingDeleteDocumentId = null
                    }
                ) {
                    Text(stringResource(R.string.document_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteDocumentId = null }) {
                    Text(stringResource(R.string.document_delete_cancel))
                }
            }
        )
    }

    pendingTagDocument?.let { document ->
        AlertDialog(
            onDismissRequest = { pendingTagDocument = null },
            title = { Text(stringResource(R.string.document_tags_title)) },
            text = {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text(stringResource(R.string.document_tags_label)) },
                    placeholder = { Text(stringResource(R.string.document_tags_placeholder)) },
                    singleLine = false
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateDocumentTags(document.id, tagInput)
                        pendingTagDocument = null
                    }
                ) {
                    Text(stringResource(R.string.document_tags_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTagDocument = null }) {
                    Text(stringResource(R.string.document_tags_cancel))
                }
            }
        )
    }

    if (pendingRenameDocument != null) {
        val document = pendingRenameDocument!!
        val sheetState = androidx.compose.material3.rememberModalBottomSheetState()
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { pendingRenameDocument = null },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.document_rename_title), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text(stringResource(R.string.document_rename_label)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    TextButton(onClick = { pendingRenameDocument = null }) {
                        Text(stringResource(R.string.document_rename_cancel))
                    }
            TextButton(onClick = {
                        val desired = renameInput
                        pendingRenameDocument = null
                        if (desired.isNotBlank()) {
                            viewModel.renameDocument(document.id, desired)
                        }
                    }) {
                        Text(stringResource(R.string.document_rename_save))
                    }
                }
            }
        }
    }
}

private fun requiresLegacyWritePermission(): Boolean {
    return Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.P
}

private fun hasLegacyWritePermission(context: android.content.Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
}

