package com.armanmaurya.archiv.ui.scanner

import android.content.Context
import com.armanmaurya.archiv.data.document.PdfStorage
import java.io.File

class ScannerPdfStorage(context: Context) {
    private val pdfStorage = PdfStorage(context.applicationContext)

    fun savePdf(pdfBytes: ByteArray): File {
        return pdfStorage.savePdfToAppStorage(pdfBytes)
    }
}
