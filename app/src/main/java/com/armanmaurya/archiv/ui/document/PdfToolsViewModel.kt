package com.armanmaurya.archiv.ui.document

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.armanmaurya.archiv.data.document.Document
import com.armanmaurya.archiv.data.document.PdfToolsRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SplitMode {
    ALL_PAGES,
    SELECT_PAGES
}

class PdfToolsViewModel(
    private val repository: PdfToolsRepository
) : ViewModel() {

    var documents by mutableStateOf<List<Document>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isProcessing by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var infoMessage by mutableStateOf<String?>(null)
        private set

    var selectedMergeUris by mutableStateOf<List<Uri>>(emptyList())
        private set

    var selectedSplitUri by mutableStateOf<Uri?>(null)
        private set
    var splitMode by mutableStateOf(SplitMode.ALL_PAGES)
        private set
    var splitPageCount by mutableStateOf(0)
        private set
    var selectedSplitPages by mutableStateOf<Set<Int>>(emptySet())
        private set

    var selectedReorderUri by mutableStateOf<Uri?>(null)
        private set
    var reorderPageOrder by mutableStateOf<List<Int>>(emptyList())
        private set

    init {
        refreshDocuments()
    }

    fun refreshDocuments() {
        viewModelScope.launch {
            isLoading = true
            try {
                documents = withContext(Dispatchers.IO) {
                    repository.listDocuments()
                }
            } catch (error: IOException) {
                errorMessage = error.message ?: "Unable to load documents."
            } finally {
                isLoading = false
            }
        }
    }

    fun clearMergeSelection() {
        selectedMergeUris = emptyList()
    }

    fun setMergeUris(uris: List<Uri>) {
        selectedMergeUris = uris.distinct()
        if (selectedMergeUris.size < 2) {
            errorMessage = "Select at least two PDFs to merge."
        }
    }

    fun mergeSelected(outputName: String) {
        val uriSelections = selectedMergeUris
        if (uriSelections.size < 2) {
            errorMessage = "Select at least two PDFs to merge."
            return
        }
        if (isProcessing) return

        isProcessing = true
        viewModelScope.launch {
            try {
                val merged = withContext(Dispatchers.IO) {
                    repository.mergeFromUris(uriSelections, outputName)
                }
                infoMessage = "Merged PDF saved: ${merged.name}"
                clearMergeSelection()
                refreshDocuments()
            } catch (error: IOException) {
                errorMessage = error.message ?: "Unable to merge PDFs."
            } catch (error: SecurityException) {
                errorMessage = error.message ?: "Permission denied while merging PDFs."
            } finally {
                isProcessing = false
            }
        }
    }

    fun selectSplitUri(uri: Uri) {
        selectedSplitUri = uri
        selectedSplitPages = emptySet()
        loadSplitPageCount(uri)
    }

    fun updateSplitMode(mode: SplitMode) {
        splitMode = mode
    }

    fun toggleSplitPage(pageIndex: Int) {
        if (pageIndex !in 0 until splitPageCount) return
        selectedSplitPages = selectedSplitPages.toMutableSet().apply {
            if (!add(pageIndex)) {
                remove(pageIndex)
            }
        }
    }

    fun splitSelected(outputName: String) {
        val sourceUri = selectedSplitUri
        if (sourceUri == null) {
            errorMessage = "Select a source PDF."
            return
        }
        if (isProcessing) return

        isProcessing = true
        viewModelScope.launch {
            try {
                when (splitMode) {
                    SplitMode.ALL_PAGES -> {
                        val baseName = outputName.ifBlank { "Split_PDF" }
                        val files = withContext(Dispatchers.IO) {
                            repository.splitAllPages(sourceUri, baseName)
                        }
                        infoMessage = "Created ${files.size} split PDFs."
                    }

                    SplitMode.SELECT_PAGES -> {
                        if (selectedSplitPages.isEmpty()) {
                            errorMessage = "Select at least one page."
                            isProcessing = false
                            return@launch
                        }
                        val extracted = withContext(Dispatchers.IO) {
                            repository.extractPages(
                                sourceUri = sourceUri,
                                pageIndices = selectedSplitPages.toList().sorted(),
                                outputName = outputName
                            )
                        }
                        infoMessage = "Extracted pages to ${extracted.name}"
                    }
                }
                refreshDocuments()
            } catch (error: IOException) {
                errorMessage = error.message ?: "Unable to split PDF."
            } catch (error: SecurityException) {
                errorMessage = error.message ?: "Permission denied while splitting PDF."
            } finally {
                isProcessing = false
            }
        }
    }

    fun selectReorderUri(uri: Uri) {
        selectedReorderUri = uri
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    repository.getPageCount(uri)
                }
                reorderPageOrder = (0 until count).toList()
            } catch (error: IOException) {
                errorMessage = error.message ?: "Unable to load page order."
                reorderPageOrder = emptyList()
            }
        }
    }

    fun movePageUp(position: Int) {
        if (position <= 0 || position >= reorderPageOrder.size) return
        val list = reorderPageOrder.toMutableList()
        val current = list[position]
        list[position] = list[position - 1]
        list[position - 1] = current
        reorderPageOrder = list
    }

    fun movePageDown(position: Int) {
        if (position < 0 || position >= reorderPageOrder.lastIndex) return
        val list = reorderPageOrder.toMutableList()
        val current = list[position]
        list[position] = list[position + 1]
        list[position + 1] = current
        reorderPageOrder = list
    }

    fun moveReorderPage(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in reorderPageOrder.indices || toIndex !in reorderPageOrder.indices) return
        if (fromIndex == toIndex) return
        val list = reorderPageOrder.toMutableList()
        val page = list.removeAt(fromIndex)
        list.add(toIndex, page)
        reorderPageOrder = list
    }

    fun saveReordered(outputName: String) {
        val sourceUri = selectedReorderUri
        if (sourceUri == null) {
            errorMessage = "Select a source PDF."
            return
        }
        if (reorderPageOrder.isEmpty()) {
            errorMessage = "No pages available to reorder."
            return
        }
        if (isProcessing) return

        isProcessing = true
        viewModelScope.launch {
            try {
                val reordered = withContext(Dispatchers.IO) {
                    repository.reorderPages(sourceUri, reorderPageOrder, outputName)
                }
                infoMessage = "Reordered PDF saved: ${reordered.name}"
                refreshDocuments()
            } catch (error: IOException) {
                errorMessage = error.message ?: "Unable to reorder PDF."
            } catch (error: SecurityException) {
                errorMessage = error.message ?: "Permission denied while reordering PDF."
            } finally {
                isProcessing = false
            }
        }
    }

    fun consumeErrorMessage() {
        errorMessage = null
    }

    fun consumeInfoMessage() {
        infoMessage = null
    }

    private fun loadSplitPageCount(sourceUri: Uri) {
        viewModelScope.launch {
            try {
                splitPageCount = withContext(Dispatchers.IO) {
                    repository.getPageCount(sourceUri)
                }
            } catch (error: IOException) {
                splitPageCount = 0
                errorMessage = error.message ?: "Unable to read PDF pages."
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PdfToolsViewModel(
                    repository = PdfToolsRepository(context.applicationContext)
                )
            }
        }
    }
}
