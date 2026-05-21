package com.armanmaurya.archiv.ui.document

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.armanmaurya.archiv.data.repository.DocumentRepository
import com.armanmaurya.archiv.data.repository.DocumentSort
import com.armanmaurya.archiv.data.repository.SettingsRepository
import com.armanmaurya.archiv.data.repository.dataStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DocumentViewModel(
    private val repository: DocumentRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var infoMessage by mutableStateOf<String?>(null)
        private set

    val isDocumentListGridView = settingsRepository.documentListGridView
        .map<Boolean, Boolean?> { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    // Search state
    private val searchQuery = MutableStateFlow("")
    val searchQueryState: StateFlow<String> = searchQuery.asStateFlow()

    // Search bar expanded state
    private val isSearchExpanded = MutableStateFlow(false)
    val isSearchExpandedState: StateFlow<Boolean> = isSearchExpanded.asStateFlow()

    val sortOptionState: StateFlow<DocumentSort> = settingsRepository.documentSort
        .map {
            try {
                DocumentSort.valueOf(it)
            } catch (_: Exception) {
                DocumentSort.NAME_ASC
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DocumentSort.NAME_ASC
        )

    private val selectedTags = MutableStateFlow<List<String>>(emptyList())
    val selectedTagsState: StateFlow<List<String>> = selectedTags.asStateFlow()

    val availableTags = repository.observeTagNames()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val documents = searchQuery
        .combine(sortOptionState) { query, sort -> query to sort }
        .combine(selectedTags) { (query, sort), tags -> Triple(query, sort, tags) }
        .flatMapLatest { (query, sort, tags) -> repository.observeDocuments(query, sort, tags) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun createShareIntent(documentId: String): Intent? {
        return try {
            val shareUri = repository.getShareUri(documentId)
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (error: IllegalArgumentException) {
            errorMessage = error.message ?: "Unable to share this document."
            null
        } catch (error: IOException) {
            errorMessage = error.message ?: "Unable to share this document."
            null
        }
    }

    fun createOpenIntent(documentId: String): Intent? {
        return try {
            val shareUri = repository.getShareUri(documentId)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(shareUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (error: IllegalArgumentException) {
            errorMessage = error.message ?: "Unable to open this document."
            null
        } catch (error: IOException) {
            errorMessage = error.message ?: "Unable to open this document."
            null
        }
    }

    fun onDocumentOpened(documentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateLastOpened(documentId)
        }
    }

    fun exportDocument(documentId: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    repository.exportDocument(documentId)
                }
                infoMessage = "Exported to Downloads."
            } catch (error: SecurityException) {
                errorMessage = error.message ?: "Storage permission is required to export."
            } catch (error: IOException) {
                errorMessage = error.message ?: "Unable to export document."
            } finally {
                isLoading = false
            }
        }
    }

    fun importDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            isLoading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.importPdfDocuments(uris)
                }
                when {
                    result.imported == 0 && result.failed > 0 -> {
                        errorMessage = "Unable to import selected PDFs."
                    }

                    result.failed > 0 -> {
                        infoMessage = "Imported ${'$'}{result.imported} PDFs, failed ${'$'}{result.failed}."
                    }

                    else -> {
                        infoMessage = "Imported ${'$'}{result.imported} PDFs."
                    }
                }
            } catch (error: SecurityException) {
                errorMessage = error.message ?: "Storage permission is required to import."
            } catch (error: IOException) {
                errorMessage = error.message ?: "Unable to import selected PDFs."
            } finally {
                isLoading = false
            }
        }
    }

    fun deleteDocument(documentId: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    repository.deleteDocument(documentId)
                }
                infoMessage = "Document deleted."
            } catch (error: IOException) {
                errorMessage = error.message ?: "Unable to delete document."
            } finally {
                isLoading = false
            }
        }
    }

    fun updateDocumentTags(documentId: String, tags: List<String>) {
        viewModelScope.launch {
            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    repository.updateDocumentTags(documentId, tags)
                }
                infoMessage = "Tags updated."
            } catch (error: IOException) {
                errorMessage = error.message ?: "Unable to update tags."
            } finally {
                isLoading = false
            }
        }
    }

    fun renameDocument(documentId: String, desiredName: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    repository.renameDocument(documentId, desiredName)
                }
                infoMessage = "Renamed document."
            } catch (error: IOException) {
                errorMessage = error.message ?: "Unable to rename document."
            } finally {
                isLoading = false
            }
        }
    }

    fun onExportPermissionDenied() {
        errorMessage = "Storage permission is required to export on this Android version."
    }

    fun onOpenAppUnavailable() {
        errorMessage = "No PDF app found to open this document."
    }

    fun setSearchExpanded(expanded: Boolean) {
        isSearchExpanded.value = expanded
    }

    fun consumeErrorMessage() {
        errorMessage = null
    }

    fun consumeInfoMessage() {
        infoMessage = null
    }

    fun setDocumentListGridView(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDocumentListGridView(enabled)
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSortOption(sort: DocumentSort) {
        viewModelScope.launch {
            settingsRepository.setDocumentSort(sort.name)
        }
    }

    fun toggleTagFilter(tag: String) {
        val current = selectedTags.value.toMutableList()
        if (current.contains(tag)) {
            current.remove(tag)
        } else {
            current.add(tag)
        }
        selectedTags.value = current
    }

    fun clearTagFilters() {
        selectedTags.value = emptyList()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                DocumentViewModel(
                    repository = DocumentRepository(appContext),
                    settingsRepository = SettingsRepository(appContext.dataStore)
                )
            }
        }
    }
}

