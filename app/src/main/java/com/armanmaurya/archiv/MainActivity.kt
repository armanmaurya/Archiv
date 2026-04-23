package com.armanmaurya.archiv

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.armanmaurya.archiv.core.theme.PDFScannerTheme
import com.armanmaurya.archiv.navigation.AppNavHost
import com.armanmaurya.archiv.ui.settings.SettingsViewModel
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                AppNavHost()
            }
        }
    }
}
