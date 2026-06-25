package com.armanmaurya.archiv.ui.document.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.armanmaurya.archiv.data.repository.DocumentSort

@Composable
fun ArchivSearchBar(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onViewChange: () -> Unit,
    isGridView: Boolean?,
    onOpenSettings: () -> Unit,
    sortOption: DocumentSort,
    onSortChange: (DocumentSort) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isSortMenuExpanded by remember { mutableStateOf(false) }

    val horizontalPadding by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else 8.dp,
        animationSpec = tween(300),
        label = "horizontalPadding"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else 28.dp,
        animationSpec = tween(300),
        label = "cornerRadius"
    )
    val topPadding = 8.dp

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // animate the status bar inset so the pill can smoothly move into it
    val animatedStatusBarHeight by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else statusBarHeight,
        animationSpec = tween(300),
        label = "animatedStatusBarHeight"
    )

    // animate the Row's top padding so it increases smoothly when expanded
    val animatedRowTopPadding by animateDpAsState(
        targetValue = if (isExpanded) topPadding + statusBarHeight  else 0.dp,
        animationSpec = tween(300),
        label = "animatedRowTopPadding"
    )

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh

    // baseline vertical padding that is always applied to the Row
    val defaultRowVerticalPadding = 4.dp

    LaunchedEffect(isExpanded) {
        if (isExpanded) focusRequester.requestFocus()
        else {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    // This Box reserves the full area including status bar,
    // so the pill can animate upward into it
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = animatedStatusBarHeight), // animate into status bar
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                )
                .clip(RoundedCornerShape(cornerRadius)),
            color = surfaceColor,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = animatedRowTopPadding + defaultRowVerticalPadding,
                        bottom = defaultRowVerticalPadding,
                        end = 4.dp,
                        start = 4.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (isExpanded) {
                        onQueryChange("")
                        onExpandedChange(false)
                    } else {
                        onExpandedChange(true)
                    }
                }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.AutoMirrored.Filled.ArrowBack
                        else Icons.Default.Search,
                        contentDescription = if (isExpanded) "Close Search" else "Search"
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search Document",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused && !isExpanded) onExpandedChange(true) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                    )
                }

                if (!isExpanded && isGridView != null) {
                    Box {
                        IconButton(onClick = { isSortMenuExpanded = true }) {
                            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = isSortMenuExpanded,
                            onDismissRequest = { isSortMenuExpanded = false }
                        ) {
                            DocumentSort.values()
                                .forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.displayLabel()) },
                                        onClick = { onSortChange(option); isSortMenuExpanded = false },
                                        enabled = option != sortOption
                                    )
                                }
                        }
                    }
                    IconButton(onClick = onViewChange) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Outlined.ViewList
                            else Icons.Outlined.GridView,
                            contentDescription = null
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            }
        }
    }
}

private fun DocumentSort.displayLabel() = when (this) {
    DocumentSort.RECENT -> "Recent"
    DocumentSort.NAME_ASC -> "Name (A to Z)"
    DocumentSort.SIZE_DESC -> "Size"
}