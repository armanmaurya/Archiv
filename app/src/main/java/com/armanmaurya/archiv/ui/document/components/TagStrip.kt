package com.armanmaurya.archiv.ui.document.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.armanmaurya.archiv.domain.model.Document


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagStrip(
    modifier: Modifier = Modifier,
    availableTags: List<String>,
    selectedTags: List<String>,
    documents: List<Document>,
    onToggleTag: (String) -> Unit
) {
    if (availableTags.isEmpty()) return

    val visibleTags = remember(availableTags, documents, selectedTags) {
        availableTags.map { tag ->
            tag to documents.count { it.tags.contains(tag) }
        }.filter { (tag, count) ->
            count > 0 || selectedTags.contains(tag)
        }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(visibleTags, key = { it.first }) { (tag, count) ->
            val selected = selectedTags.contains(tag)
            FilterChip(
                modifier = Modifier.animateItem(),
                selected = selected,
                onClick = { onToggleTag(tag) },
                label = { Text("$tag ($count)") },
                shape = CircleShape
            )
        }
    }
}
