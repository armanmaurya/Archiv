package com.example.pdfscanner.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import com.example.pdfscanner.ui.scanner.ScannerViewModel
import com.example.pdfscanner.ui.scanner.components.EditorPage

@Composable
fun EditorScreen (
    viewModel: ScannerViewModel,
    initialPage: Int,
    onBack: () -> Unit,
) {
    val pages = viewModel.pages;
    if (pages.isEmpty()) {
        onBack()
        return
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = {pages.size})

    BackHandler {
        onBack()
    }
    Box {
        HorizontalPager(state = pagerState) { page ->
            val pageState = pages[page]

            EditorPage(
                pageState = pageState,
                onCropChange = { bounds ->
                    viewModel.updateCrop(pageState.uri, bounds)
                },
                onRotate = { rotation ->
                    viewModel.updateRotation(pageState.uri, rotation)
                },
                onFilterChange = { filter ->
                    viewModel.updateFilter(pageState.uri, filter)
                }
            )
        }
    }

}