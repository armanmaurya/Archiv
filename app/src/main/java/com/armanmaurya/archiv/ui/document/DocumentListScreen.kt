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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.armanmaurya.archiv.ui.document.components.DocumentItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    viewModel: DocumentViewModel,
    onOpenScanner: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val documents = viewModel.documents
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

    LaunchedEffect(Unit) {
        viewModel.refreshDocuments()
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
                text = { Text("Scan") }
            )
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Archiv") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
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

                documents.isEmpty() -> {
                    Text(
                        text = "No scans yet. Scan now?",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(documents, key = { document -> document.id }) { document ->
                            DocumentItem(
                                document = document,
                                actionEnabled = !viewModel.isLoading,
                                onOpen = {
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
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDeleteDocumentId?.let { documentId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteDocumentId = null },
            title = { Text("Delete document?") },
            text = { Text("This removes the PDF from My Scans.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDocument(documentId)
                        pendingDeleteDocumentId = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteDocumentId = null }) {
                    Text("Cancel")
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

