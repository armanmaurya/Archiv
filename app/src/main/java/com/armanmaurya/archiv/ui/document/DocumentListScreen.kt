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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.room.util.query
import com.armanmaurya.archiv.R
import com.armanmaurya.archiv.data.repository.DocumentSort
import com.armanmaurya.archiv.ui.document.components.ArchivSearchBar
import com.armanmaurya.archiv.ui.document.components.DocumentItem

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    viewModel: DocumentViewModel,
    onOpenScanner: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val searchQuery by viewModel.searchQueryState.collectAsState()
    val isSearchExpanded by viewModel.isSearchExpandedState.collectAsState()
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsState()
    val sortOption by viewModel.sortOptionState.collectAsState()
    val isGridView by viewModel.isDocumentListGridView.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeleteDocumentId by remember { mutableStateOf<String?>(null) }
    var pendingExportDocumentId by remember { mutableStateOf<String?>(null) }

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
            ExtendedFloatingActionButton(
                onClick = onOpenScanner,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null
                    )
                },
                text = { Text(stringResource(R.string.document_list_fab_scan)) }
            )
        },
        topBar = {
            ArchivSearchBar(
                query = searchQuery,
                onQueryChange = {viewModel.setSearchQuery(it)},
                isExpanded = isSearchExpanded,
                onExpandedChange = {viewModel.setSearchExpanded(it)},
                isGridView = isGridView,
                onViewChange = {
                    viewModel.setDocumentListGridView(!isGridView!!)
                },
                onOpenSettings = onOpenSettings
            )
//            CenterAlignedTopAppBar(
//                title = { Text(stringResource(R.string.document_list_title)) },
//                actions = {
//                    if (isGridView != null) {
//                        IconButton(
//                            onClick = {
//                                viewModel.setDocumentListGridView(!isGridView!!)
//                            }
//                        ) {
//                            Icon(
//                                imageVector = if (isGridView == true) {
//                                    Icons.AutoMirrored.Outlined.ViewList
//                                } else {
//                                    Icons.Outlined.GridView
//                                },
//                                contentDescription = if (isGridView == true) {
//                                    stringResource(R.string.document_list_view_list)
//                                } else {
//                                    stringResource(R.string.document_list_view_grid)
//                                }
//                            )
//                        }
//                    }
//                    IconButton(onClick = onOpenSettings) {
//                        Icon(
//                            imageVector = Icons.Outlined.Settings,
//                            contentDescription = "Settings"
//                        )
//                    }
//                }
//            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                viewModel.isLoading && documents.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                isGridView == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
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
                                                    val openIntent = viewModel.createOpenIntent(document.id)
                                                    if (openIntent != null) {
                                                        try {
                                                            context.startActivity(
                                                                Intent.createChooser(openIntent, "Open scan")
                                                            )
                                                        } catch (_: ActivityNotFoundException) {
                                                            viewModel.onOpenAppUnavailable()
                                                        }
                                                    }
                                                },
                                                onShare = {
                                                    val shareIntent = viewModel.createShareIntent(document.id)
                                                    if (shareIntent != null) {
                                                        context.startActivity(
                                                            Intent.createChooser(shareIntent, "Share scan")
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
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .animateItem()
                                            )
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 180.dp),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(12.dp),
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
                                                    val openIntent = viewModel.createOpenIntent(document.id)
                                                    if (openIntent != null) {
                                                        try {
                                                            context.startActivity(
                                                                Intent.createChooser(openIntent, "Open scan")
                                                            )
                                                        } catch (_: ActivityNotFoundException) {
                                                            viewModel.onOpenAppUnavailable()
                                                        }
                                                    }
                                                },
                                                onShare = {
                                                    val shareIntent = viewModel.createShareIntent(document.id)
                                                    if (shareIntent != null) {
                                                        context.startActivity(
                                                            Intent.createChooser(shareIntent, "Share scan")
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



private fun DocumentSort.displayLabel(): String {
    return when (this) {
        DocumentSort.MODIFIED_DESC -> "Recently modified"
        DocumentSort.NAME_ASC -> "Name (A to Z)"
        DocumentSort.LAST_OPENED_DESC -> "Recently opened"
    }
}

