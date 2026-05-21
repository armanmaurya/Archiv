package com.armanmaurya.archiv.ui.document.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.armanmaurya.archiv.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameSheet(
    initialName: String,
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    var renameInput by remember { mutableStateOf(initialName) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.document_rename_title),
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = renameInput,
                onValueChange = { renameInput = it },
                label = { Text(stringResource(R.string.document_rename_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(12.dp)
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.document_rename_cancel))
                }
                TextButton(onClick = {
                    if (renameInput.isNotBlank()) {
                        onRename(renameInput)
                    }
                }) {
                    Text(stringResource(R.string.document_rename_save))
                }
            }
        }
    }
}
