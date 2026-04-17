package com.example.pdfscanner.ui.scanner.components

import android.graphics.PointF
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.pdfscanner.ui.scanner.PageState

@Composable
fun EditorPage(
    pageState: PageState,

    onCropChange: (List<PointF>) -> Unit,
    onRotate: (Int) -> Unit,
    onFilterChange: (Int) -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }
    var isFilterMode by remember { mutableStateOf(false) }

    // Temporary editing state (VERY IMPORTANT)
    var tempBounds by remember { mutableStateOf(pageState.cropBounds) }
    var tempRotation by remember { mutableStateOf(pageState.rotation) }
    var tempFilter by remember { mutableStateOf(pageState.filter) }

    Box {
        // 🔹 Show image
        ImageRenderer(
            uri = pageState.uri,
            bounds = if (isEditMode) tempBounds else pageState.cropBounds,
            rotation = if (isEditMode) tempRotation else pageState.rotation,
            filter = if (isFilterMode) tempFilter else pageState.filter
        )

        // 🔹 Crop UI
        if (isEditMode) {
            CropOverlay(
                bounds = tempBounds,
                onChange = { tempBounds = it }
            )
        }

        // 🔹 Bottom controls
        EditorControls(
            isEditMode = isEditMode,
            isFilterMode = isFilterMode,

            onStartEdit = {
                tempBounds = pageState.cropBounds
                tempRotation = pageState.rotation
                isEditMode = true
            },

            onStartFilter = {
                tempFilter = pageState.filter
                isFilterMode = true
            },

            onApplyCrop = {
                onCropChange(tempBounds)
                onRotate(tempRotation)
                isEditMode = false
            },

            onApplyFilter = {
                onFilterChange(tempFilter)
                isFilterMode = false
            }
        )
    }
}