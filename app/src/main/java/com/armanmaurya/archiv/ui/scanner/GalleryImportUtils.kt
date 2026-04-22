package com.armanmaurya.archiv.ui.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream

internal fun copyUriToCache(context: Context, uri: Uri): Uri? {
    return try {
        val cacheDirectory = File(context.cacheDir, "captured_pages").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
        val outputFile = File(
            cacheDirectory,
            "gallery_${System.currentTimeMillis()}_${uri.lastPathSegment?.takeLast(20)?.replace("/", "_")}.jpg"
        )
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outputFile).use { output -> input.copyTo(output) }
        } ?: run {
            Log.e("PickImages", "Could not open input stream for $uri")
            return null
        }
        if (outputFile.length() == 0L) {
            outputFile.delete()
            Log.e("PickImages", "Copied file is empty for $uri")
            return null
        }
        outputFile.toUri()
    } catch (e: Exception) {
        Log.e("PickImages", "Failed to copy URI $uri: ${e.message}", e)
        null
    }
}
