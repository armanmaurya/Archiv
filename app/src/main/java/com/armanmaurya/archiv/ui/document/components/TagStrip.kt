package com.armanmaurya.archiv.ui.document.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.armanmaurya.archiv.domain.model.Document


@Composable
fun TagStrip(
    modifier: Modifier = Modifier,
    availableTags: List<String>,
    selectedTags: List<String>,
    documents: List<Document>,
    onToggleTag: (String) -> Unit
) {
    if (availableTags.isEmpty()) return

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(availableTags) { tag ->
            val count = documents.count { it.tags.contains(tag) }
            val selected = selectedTags.contains(tag)
            FilterChip(
                selected = selected,
                onClick = { onToggleTag(tag) },
                label = { Text("$tag ($count)") },
                shape = CircleShape
            )
        }
    }
}
