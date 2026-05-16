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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.armanmaurya.archiv.core.theme.PDFScannerTheme
import com.armanmaurya.archiv.navigation.AppNavHost
import com.armanmaurya.archiv.ui.settings.SettingsViewModel
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {

    private var sharedUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        if (OpenCVLoader.initDebug()) {
            Log.d("SCANNER", "OpenCV loaded successfully!")
        } else {
            Log.e("SCANNER", "OpenCV load failed.")
        }

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
                AppNavHost(
                    sharedUris = sharedUris,
                    onSharedUrisProcessed = { sharedUris = emptyList() }
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
            Intent.ACTION_SEND -> {
                if ("application/pdf" == intent.type) {
                    (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri ->
                        sharedUris = listOf(uri)
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if ("application/pdf" == intent.type) {
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris ->
                        sharedUris = uris
                    }
                }
            }
        }
    }
}
