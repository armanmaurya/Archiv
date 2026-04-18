package com.example.pdfscanner.ui.scanner

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.graphics.PointF
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdfscanner.bitmap.decodeSampledBitmap
import com.example.pdfscanner.bitmap.FilterMode
import com.example.pdfscanner.bitmap.fullImageBounds
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PageState(
    val uri: Uri,
    val cropBounds: List<PointF>,
    val rotation: Int,
    val filter: Int
)

enum class Mode {
    DEFAULT,
    CROP,
    FILTER
}

class ScannerViewModel : ViewModel() {

    var pages by mutableStateOf<List<PageState>>(emptyList())
        private set
    var isSavingPdf by mutableStateOf(false)
        private set
    var saveErrorMessage by mutableStateOf<String?>(null)
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
            filter = FilterMode.NONE
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

    fun dismissSaveError() {
        saveErrorMessage = null
    }

    fun savePagesAsPdf(context: Context, storage: ScannerPdfStorage) {
        val pageUris = pages.map { it.uri }
        if (pageUris.isEmpty()) {
            saveErrorMessage = "Capture at least one page before saving."
            return
        }
        if (isSavingPdf) return

        isSavingPdf = true
        saveErrorMessage = null
        viewModelScope.launch {
            try {
                val pdfBytes = withContext(Dispatchers.IO) {
                    val pdfDocument = PdfDocument()
                    try {
                        pageUris.forEachIndexed { index, imageUri ->
                            val bitmap = decodeSampledBitmap(context, imageUri)
                                ?: throw IOException("Unable to decode captured page: $imageUri")
                            val page = pdfDocument.startPage(
                                PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                            )
                            try {
                                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                            } finally {
                                pdfDocument.finishPage(page)
                                bitmap.recycle()
                            }
                        }
                        val outputStream = ByteArrayOutputStream()
                        pdfDocument.writeTo(outputStream)
                        val bytes = outputStream.toByteArray()
                        if (bytes.isEmpty()) {
                            throw IOException("Generated PDF is empty.")
                        }
                        bytes
                    } finally {
                        pdfDocument.close()
                    }
                }
                withContext(Dispatchers.IO) {
                    storage.savePdf(pdfBytes)
                }
                clearPages()
            } catch (error: IOException) {
                saveErrorMessage = error.message ?: "Failed to save PDF."
            } catch (error: SecurityException) {
                saveErrorMessage = error.message ?: "Permission denied while saving PDF."
            } finally {
                isSavingPdf = false
            }
        }
    }
}
