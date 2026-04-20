package com.example.pdfscanner.ui.scanner

import android.content.Context
import com.example.pdfscanner.data.document.PdfStorage
import java.io.File

class ScannerPdfStorage(context: Context) {
    private val pdfStorage = PdfStorage(context.applicationContext)

    fun savePdf(pdfBytes: ByteArray): File {
        return pdfStorage.savePdfToAppStorage(pdfBytes)
    }
}
