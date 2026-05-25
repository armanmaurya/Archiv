package com.armanmaurya.archiv.ui.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BrightnessSlider(
    manualBrightness: Float,
    isAutoBrightness: Boolean,
    onBrightnessChange: (Float) -> Unit,
    onToggleAuto: () -> Unit,
    onInteractionFinished: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleAuto) {
            Icon(
                imageVector = if (isAutoBrightness) Icons.Default.BrightnessAuto else Icons.Default.WbSunny,
                contentDescription = "Brightness mode",
                tint = if (isAutoBrightness) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = manualBrightness,
            onValueChange = onBrightnessChange,
            onValueChangeFinished = onInteractionFinished,
            enabled = true,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
            colors = if (isAutoBrightness) {
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    activeTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            } else {
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        )
    }
}
