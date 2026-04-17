package com.example.pdfscanner.ui.scanner.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun EditorControls(
    isEditMode: Boolean,
    isFilterMode: Boolean,

    onStartEdit: () -> Unit,
    onStartFilter: () -> Unit,
    onApplyCrop: () -> Unit,
    onApplyFilter: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(0.6f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        if (!isEditMode && !isFilterMode) {
            Button(onClick = onStartEdit) {
                Text("Crop")
            }
            Button(onClick = onStartFilter) {
                Text("Filter")
            }
        }

        if (isEditMode) {
            Button(onClick = onApplyCrop) {
                Text("Done")
            }
        }

        if (isFilterMode) {
            Button(onClick = onApplyFilter) {
                Text("Apply")
            }
        }
    }
}