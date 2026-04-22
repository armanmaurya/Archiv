package com.armanmaurya.archiv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.armanmaurya.archiv.core.theme.PDFScannerTheme
import com.armanmaurya.archiv.navigation.AppNavHost
import com.armanmaurya.archiv.ui.settings.ThemeManager
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (OpenCVLoader.initDebug()) {
            Log.d("SCANNER", "OpenCV loaded successfully!")
        } else {
            Log.e("SCANNER", "OpenCV load failed.")
        }

        ThemeManager.initTheme(this)

        enableEdgeToEdge()
        setContent {
            PDFScannerTheme {
                AppNavHost()
            }
        }
    }
}
