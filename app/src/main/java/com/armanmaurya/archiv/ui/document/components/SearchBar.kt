package com.armanmaurya.archiv.ui.document.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons.AutoMirrored.Filled
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.armanmaurya.archiv.core.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onViewChange: () -> Unit,
    isGridView: Boolean?,
    onOpenSettings: () -> Unit
) {

    val searchBarPadding by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else 8.dp,
        label = "searchPadding"
    )

    SearchBar(
        modifier = Modifier
            .fillMaxWidth()
            .padding(searchBarPadding),
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = {},
                expanded = isExpanded,
                onExpandedChange = onExpandedChange,
                placeholder = {
                    Text(
                        "Search Document"
                    )
                },
                leadingIcon = {
                    Row {
                        IconButton(
                            onClick = {
                                if (isExpanded) {
                                    onQueryChange("")
                                }
                                onExpandedChange(!isExpanded);
                            }
                        ) {
                            val icon = if (isExpanded) Filled.ArrowBack else Icons.Default.Search
                            val contentDescription = if (isExpanded) "Close Search" else "Search"
                            Icon(
                                imageVector = icon,
                                contentDescription = contentDescription
                            )
                        }

                    }
                },
                trailingIcon = {
                    if(!isExpanded && isGridView != null)
                        Row {
                            IconButton(
                                onClick = onViewChange
                            ) {
                                Icon(
                                    imageVector = if (isGridView) {
                                        Icons.AutoMirrored.Outlined.ViewList
                                    } else {
                                        Icons.Outlined.GridView
                                    },
                                    contentDescription = null
                                )
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Settings"
                                )
                            }
                        }
                },

            )
        },
        shape = CircleShape,
        onExpandedChange = onExpandedChange,
        content = {},
        expanded = isExpanded,
    )
}