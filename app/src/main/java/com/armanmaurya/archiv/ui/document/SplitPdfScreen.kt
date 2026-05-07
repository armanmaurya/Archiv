package com.armanmaurya.archiv.ui.document

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPdfScreen(
    viewModel: PdfToolsViewModel,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var outputName by remember { mutableStateOf("") }
    val splitPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.selectSplitUri(uri)
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
                title = { Text("Split PDF") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
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
            TextButton(
                onClick = { splitPickerLauncher.launch(arrayOf("application/pdf")) }
            ) {
                Text("Pick source PDF")
            }
            Text(viewModel.selectedSplitUri?.lastPathSegment ?: "No source selected")

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = viewModel.splitMode == SplitMode.ALL_PAGES,
                    onClick = { viewModel.updateSplitMode(SplitMode.ALL_PAGES) }
                )
                Text("Split all pages")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = viewModel.splitMode == SplitMode.SELECT_PAGES,
                    onClick = { viewModel.updateSplitMode(SplitMode.SELECT_PAGES) }
                )
                Text("Extract selected pages")
            }

            if (viewModel.splitMode == SplitMode.SELECT_PAGES && viewModel.splitPageCount > 0) {
                Text("Select pages (${viewModel.splitPageCount})")
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items((0 until viewModel.splitPageCount).toList()) { pageIndex ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = viewModel.selectedSplitPages.contains(pageIndex),
                                onCheckedChange = { viewModel.toggleSplitPage(pageIndex) }
                            )
                            Text("Page ${pageIndex + 1}")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = outputName,
                onValueChange = { outputName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Output name (optional)") },
                singleLine = true
            )
            Button(
                onClick = { viewModel.splitSelected(outputName) },
                enabled = viewModel.selectedSplitUri != null && !viewModel.isProcessing
            ) {
                Text("Run Split")
            }
        }
    }
}
