package com.armanmaurya.archiv.ui.document

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.armanmaurya.archiv.data.repository.DocumentRepository
import com.armanmaurya.archiv.data.repository.SettingsRepository
import com.armanmaurya.archiv.data.repository.dataStore
import com.armanmaurya.archiv.domain.model.Document
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DocumentViewModel(
    private val repository: DocumentRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    var documents by mutableStateOf<List<Document>>(emptyList())
        private set
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

    fun deleteDocument(documentId: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    repository.deleteDocument(documentId)
                }
                documents = documents.filterNot { document -> document.id == documentId }
                infoMessage = "Document deleted."
            } catch (error: IOException) {
                errorMessage = error.message ?: "Unable to delete document."
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

