package com.armanmaurya.archiv.ui.settings

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
import com.armanmaurya.archiv.data.repository.BackupRepository
import com.armanmaurya.archiv.data.repository.ImportConflictStrategy
import com.armanmaurya.archiv.data.repository.SettingsRepository
import com.armanmaurya.archiv.data.repository.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class BackupExportEvent {
    data class Success(val documentCount: Int) : BackupExportEvent()
    data class Error(val message: String) : BackupExportEvent()
}

sealed class BackupImportEvent {
    data class Success(val imported: Int, val skipped: Int, val failed: Int) : BackupImportEvent()
    data class Error(val message: String) : BackupImportEvent()
}

class BackupViewModel(
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    var isExporting by mutableStateOf(false)
        private set

    var isImporting by mutableStateOf(false)
        private set

    var exportEvent by mutableStateOf<BackupExportEvent?>(null)
        private set

    var importEvent by mutableStateOf<BackupImportEvent?>(null)
        private set

    private var activeJob: Job? = null

    fun cancelOperation() {
        activeJob?.cancel()
        isExporting = false
        isImporting = false
    }

    fun exportBackup(destUri: Uri) {
        activeJob = viewModelScope.launch {
            isExporting = true
            try {
                val count = withContext(Dispatchers.IO) {
                    backupRepository.exportBackup(destUri)
                }
                exportEvent = BackupExportEvent.Success(count)
            } catch (e: IOException) {
                exportEvent = BackupExportEvent.Error(e.message ?: "Export failed.")
            } catch (e: Exception) {
                exportEvent = BackupExportEvent.Error(e.message ?: "Export failed.")
            } finally {
                isExporting = false
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        activeJob = viewModelScope.launch {
            isImporting = true
            try {
                val strategyString = settingsRepository.importConflictStrategy.first()
                val strategy = runCatching {
                    ImportConflictStrategy.valueOf(strategyString)
                }.getOrDefault(ImportConflictStrategy.RENAME)

                val result = withContext(Dispatchers.IO) {
                    backupRepository.importBackup(uri, strategy)
                }
                importEvent = BackupImportEvent.Success(
                    imported = result.imported,
                    skipped = result.skipped,
                    failed = result.failed
                )
            } catch (e: IOException) {
                importEvent = BackupImportEvent.Error(e.message ?: "Import failed.")
            } catch (e: Exception) {
                importEvent = BackupImportEvent.Error(e.message ?: "Import failed.")
            } finally {
                isImporting = false
            }
        }
    }

    fun consumeExportEvent() { exportEvent = null }
    fun consumeImportEvent() { importEvent = null }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                BackupViewModel(
                    backupRepository = BackupRepository(appContext),
                    settingsRepository = SettingsRepository(appContext.dataStore)
                )
            }
        }
    }
}
