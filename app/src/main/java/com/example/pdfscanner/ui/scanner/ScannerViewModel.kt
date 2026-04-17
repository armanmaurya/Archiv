package com.example.pdfscanner.ui.scanner

import android.graphics.PointF
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.pdfscanner.image.fullImageBounds

data class PageState(
    val uri: Uri,
    val cropBounds: List<PointF>,
    val rotation: Int,
    val filter: Int
)

const val FILTER_MODE_NONE = 0
const val FILTER_MODE_BW = 1
const val FILTER_MODE_SEPIA = 2

class ScannerViewModel : ViewModel() {

    var pages by mutableStateOf<List<PageState>>(emptyList())
        private set

    fun addPage(
        uri: Uri,
        detectedBounds: List<PointF>? = null
    ) {
        val bounds = if (detectedBounds != null && detectedBounds.size == 4) {
            detectedBounds
        } else {
            fullImageBounds()
        }

        pages = pages + PageState(
            uri = uri,
            cropBounds = bounds,
            rotation = 0,
            filter = FILTER_MODE_NONE
        )
    }

    fun removePage(index: Int) {
        if (index !in pages.indices) return
        pages = pages.toMutableList().also { it.removeAt(index) }
    }

    fun reorderPages(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in pages.indices || toIndex !in pages.indices) return

        val list = pages.toMutableList()
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        pages = list
    }

    fun updateCrop(uri: Uri, cropBounds: List<PointF>) {
        pages = pages.map {
            if (it.uri == uri) it.copy(cropBounds = cropBounds)
            else it
        }
    }

    fun updateRotation(uri: Uri, rotation: Int) {
        pages = pages.map {
            if (it.uri == uri) it.copy(rotation = rotation)
            else it
        }
    }

    fun updateFilter(uri: Uri, filter: Int) {
        pages = pages.map {
            if (it.uri == uri) it.copy(filter = filter)
            else it
        }
    }

    fun clearPages() {
        pages = emptyList()
    }
}
