package com.example.pdfscanner.ui.scanner

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ScannerPdfStorage(context: Context) {
    private val appContext = context.applicationContext

    fun savePdf(pdfBytes: ByteArray): Uri {
        if (pdfBytes.isEmpty()) {
            throw IOException("Generated PDF is empty.")
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(pdfBytes)
        } else {
            saveToExternalAppDirectory(pdfBytes)
        }
    }

    private fun saveToMediaStore(pdfBytes: ByteArray): Uri {
        val resolver = appContext.contentResolver
        val fileName = "scan_${System.currentTimeMillis()}.pdf"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + File.separator + "PDFScanner"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val destinationUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to create PDF in Downloads.")

        try {
            resolver.openOutputStream(destinationUri, "w")?.use { outputStream ->
                outputStream.write(pdfBytes)
                outputStream.flush()
            } ?: throw IOException("Unable to open destination output stream.")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(destinationUri, values, null, null)
        } catch (error: IOException) {
            resolver.delete(destinationUri, null, null)
            throw error
        } catch (error: SecurityException) {
            resolver.delete(destinationUri, null, null)
            throw error
        }

        return destinationUri
    }

    private fun saveToExternalAppDirectory(pdfBytes: ByteArray): Uri {
        val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IOException("External downloads directory is unavailable.")
        val outputDir = File(downloadsDir, "PDFScanner")
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IOException("Unable to create output directory.")
        }

        val outputFile = File(outputDir, "scan_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { outputStream ->
            outputStream.write(pdfBytes)
            outputStream.flush()
        }

        if (outputFile.length() == 0L) {
            outputFile.delete()
            throw IOException("Saved PDF is empty.")
        }

        return outputFile.toUri()
    }
}
