package com.armanmaurya.archiv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.material.color.DynamicColors
import com.armanmaurya.archiv.data.repository.SettingsRepository
import com.armanmaurya.archiv.data.repository.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.lifecycle.viewmodel.compose.viewModel
import com.armanmaurya.archiv.core.theme.PDFScannerTheme
import com.armanmaurya.archiv.navigation.ArchivNavHost
import com.armanmaurya.archiv.ui.settings.SettingsViewModel

class MainActivity : AppCompatActivity() {

    private var sharedIntent by mutableStateOf<SharedIntent>(SharedIntent.None)

    override fun onCreate(savedInstanceState: Bundle?) {
        val settingsRepository = SettingsRepository(dataStore)
        val isDynamic = runBlocking { settingsRepository.dynamicTheme.first() }
        if (isDynamic) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(this)
            )
            val settingsState by settingsViewModel.uiState.collectAsState()

            PDFScannerTheme(
                theme = settingsState.appTheme,
                dynamicColor = settingsState.dynamicTheme,
                pureBlack = settingsState.pureBlack
            ) {
                ArchivNavHost(
                    sharedIntent = sharedIntent,
                    onSharedIntentProcessed = { sharedIntent = SharedIntent.None },
                    onExit = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                sharedIntent = SharedIntent.Viewer(uri)
            }
            Intent.ACTION_SEND -> {
                val type = intent.type ?: return
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
                
                sharedIntent = when {
                    type.startsWith("application/pdf") -> SharedIntent.Pdfs(listOf(uri))
                    type.startsWith("image/") -> SharedIntent.Images(listOf(uri))
                    else -> SharedIntent.None
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val type = intent.type ?: return
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: return
                
                sharedIntent = when {
                    type.startsWith("application/pdf") -> SharedIntent.Pdfs(uris)
                    type.startsWith("image/") -> SharedIntent.Images(uris)
                    else -> SharedIntent.None
                }
            }
        }
    }
}
