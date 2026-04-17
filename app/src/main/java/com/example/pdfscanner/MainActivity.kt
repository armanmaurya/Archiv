package com.example.pdfscanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.util.Log
import com.example.pdfscanner.core.theme.PDFScannerTheme
import com.example.pdfscanner.navigation.AppNavHost
import kotlin.math.max
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

fun decodeScaledBitmap(context: android.content.Context, uri: Uri, maxDimension: Int = 2000): Bitmap? {
    try {
        // Handle file:// URIs directly (e.g., from cache directory)
        if (uri.scheme == "file") {
            val filePath = uri.path
            if (filePath != null) {
                val file = java.io.File(filePath)
                if (file.exists()) {
                    Log.d("DecodeScaledBitmap", "Decoding file directly: $filePath")
                    return decodeBitmapFromFile(filePath, maxDimension)
                }
            }
        }
        
        // Otherwise use ContentResolver
        Log.d("DecodeScaledBitmap", "Decoding via ContentResolver: $uri")
        return com.example.pdfscanner.decodeScaledBitmapViaContentResolver(context, uri, maxDimension)
    } catch (e: Exception) {
        Log.e("DecodeScaledBitmap", "Error decoding bitmap: ${e.message}", e)
        return null
    }
}

private fun decodeBitmapFromFile(filePath: String, maxDimension: Int = 2000): Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(filePath, bounds)

    val largestDimension = maxOf(bounds.outWidth, bounds.outHeight)
    val sampleSize = if (largestDimension <= maxDimension) {
        1
    } else {
        maxOf(1, largestDimension / maxDimension)
    }

    val decodeOptions = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return android.graphics.BitmapFactory.decodeFile(filePath, decodeOptions)
}

fun decodeScaledBitmapViaContentResolver(context: android.content.Context, uri: Uri, maxDimension: Int = 2000): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: return null

    val largestDimension = max(bounds.outWidth, bounds.outHeight)
    val sampleSize = if (largestDimension <= maxDimension) {
        1
    } else {
        max(1, largestDimension / maxDimension)

    }

    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
}
