package com.armanmaurya.archiv.data.document

import android.content.Context
import android.net.Uri
import java.io.File

class PdfToolsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val documentRepository = DocumentRepository(appContext)
    private val storage = PdfToolsStorage(appContext)

    fun listDocuments(): List<Document> = documentRepository.listDocuments()

    fun getPageCount(documentId: String): Int = storage.getPageCount(documentId)
    fun getPageCount(sourceUri: Uri): Int = storage.getPageCount(sourceUri)

    fun mergeDocuments(documentIds: List<String>, outputName: String): File {
        return storage.mergeDocuments(documentIds, outputName)
    }

    fun mergeFromUris(sourceUris: List<Uri>, outputName: String): File {
        return storage.mergeFromUris(sourceUris, outputName)
    }

    fun splitAllPages(documentId: String, baseOutputName: String): List<File> {
        return storage.splitAllPages(documentId, baseOutputName)
    }
    fun splitAllPages(sourceUri: Uri, baseOutputName: String): List<File> {
        return storage.splitAllPages(sourceUri, baseOutputName)
    }

    fun extractPages(documentId: String, pageIndices: List<Int>, outputName: String): File {
        return storage.extractPages(documentId, pageIndices, outputName)
    }
    fun extractPages(sourceUri: Uri, pageIndices: List<Int>, outputName: String): File {
        return storage.extractPages(sourceUri, pageIndices, outputName)
    }

    fun reorderPages(documentId: String, pageOrder: List<Int>, outputName: String): File {
        return storage.reorderPages(documentId, pageOrder, outputName)
    }
    fun reorderPages(sourceUri: Uri, pageOrder: List<Int>, outputName: String): File {
        return storage.reorderPages(sourceUri, pageOrder, outputName)
    }
}
