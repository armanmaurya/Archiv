package com.example.pdfscanner

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.pdfscanner.core.theme.PDFScannerTheme
import com.example.pdfscanner.navigation.AppNavHost
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (OpenCVLoader.initDebug()) {
            Log.d("SCANNER", "OpenCV loaded successfully!")
        } else {
            Log.e("SCANNER", "OpenCV load failed.")
        }

        enableEdgeToEdge()
        setContent {
            PDFScannerTheme {
                AppNavHost()
            }
        }
    }
}
