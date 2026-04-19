package com.example.pdfscanner.ui.scanner.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun GalleryButton(
        onImagesSelected: (List<Uri>) -> Unit,
        enabled: Boolean = true,
        modifier: Modifier = Modifier
) {
    val backgroundColor = if (enabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val iconColor = if (enabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val pickImagesLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
                if (uris.isNotEmpty()) {
                    onImagesSelected(uris)
                }
            }

    Box(
            modifier =
                    modifier.defaultMinSize(minWidth = 56.dp, minHeight = 56.dp).clip(CircleShape).background(backgroundColor).clickable(
                                    enabled = enabled
                            ) { pickImagesLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
    ) {

        Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = "Open Gallery",
                tint = iconColor,
                modifier = Modifier.size(28.dp)
        )
    }
}
