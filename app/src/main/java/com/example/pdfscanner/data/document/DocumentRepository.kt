package com.example.pdfscanner.data.document

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class DocumentRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pdfStorage = PdfStorage(appContext)

    fun listDocuments(): List<Document> =
        pdfStorage.listAppPdfFiles().map { file -> file.toDocument() }

    fun getShareUri(documentId: String): Uri {
        val file = requireDocumentFile(documentId)
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
    }

    fun exportDocument(documentId: String): Uri {
        val file = requireDocumentFile(documentId)
        return pdfStorage.exportToDownloads(file)
    }

    fun deleteDocument(documentId: String) {
        if (!pdfStorage.deleteAppPdfFile(documentId)) {
            throw IOException("Unable to delete the selected document.")
        }
    }

    private fun requireDocumentFile(documentId: String): File {
        return pdfStorage.resolveAppPdfFile(documentId)
            ?: throw FileNotFoundException("Document not found.")
    }

    private fun File.toDocument(): Document {
        return Document(
            id = name,
            fileName = name,
            filePath = absolutePath,
            fileSizeBytes = length(),
            modifiedAtMillis = lastModified()
        )
    }
}

