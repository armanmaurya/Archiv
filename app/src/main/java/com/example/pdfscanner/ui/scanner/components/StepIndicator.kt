package com.example.pdfscanner.ui.scanner.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun StepIndicator(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
    chipHeight: Dp = 44.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 24.dp)
            .height(chipHeight)
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$currentPage / $totalPages",
            color = contentColor,
            style = textStyle,
            textAlign = TextAlign.Center
        )
    }
}
