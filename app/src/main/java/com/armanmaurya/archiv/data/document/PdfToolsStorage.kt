package com.armanmaurya.archiv.data.document

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class PdfToolsStorage(context: Context) {

    private val appContext = context.applicationContext
    private val pdfStorage = PdfStorage(appContext)

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    fun getPageCount(documentId: String): Int {
        val sourceFile = requireSourceFile(documentId)
        PDDocument.load(sourceFile).use { document ->
            return document.numberOfPages
        }
    }

    fun getPageCount(sourceUri: Uri): Int {
        val inputStream = appContext.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("Unable to open selected PDF.")
        inputStream.use { stream ->
            PDDocument.load(stream).use { document ->
                return document.numberOfPages
            }
        }
    }

    fun mergeDocuments(documentIds: List<String>, outputName: String): File {
        if (documentIds.size < 2) {
            throw IOException("Select at least two PDFs to merge.")
        }
        val sourceFiles = documentIds.map { documentId -> requireSourceFile(documentId) }
        val merged = PDDocument()
        try {
            sourceFiles.forEach { file ->
                PDDocument.load(file).use { source ->
                    for (index in 0 until source.numberOfPages) {
                        merged.importPage(source.getPage(index))
                    }
                }
            }
            val bytes = merged.toPdfBytes()
            return pdfStorage.savePdfToAppStorage(bytes, outputName)
        } finally {
            merged.close()
        }
    }

    fun mergeFromUris(sourceUris: List<Uri>, outputName: String): File {
        if (sourceUris.size < 2) {
            throw IOException("Select at least two PDFs to merge.")
        }
        val merged = PDDocument()
        try {
            sourceUris.forEach { uri ->
                val inputStream = appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("Unable to open selected PDF.")
                inputStream.use { stream ->
                    PDDocument.load(stream).use { source ->
                        for (index in 0 until source.numberOfPages) {
                            merged.importPage(source.getPage(index))
                        }
                    }
                }
            }
            return pdfStorage.savePdfToAppStorage(merged.toPdfBytes(), outputName)
        } finally {
            merged.close()
        }
    }

    fun splitAllPages(documentId: String, baseOutputName: String): List<File> {
        val sourceFile = requireSourceFile(documentId)
        PDDocument.load(sourceFile).use { source ->
            if (source.numberOfPages <= 0) {
                throw IOException("The selected PDF has no pages.")
            }
            val outputFiles = mutableListOf<File>()
            for (index in 0 until source.numberOfPages) {
                val singlePage = PDDocument()
                try {
                    singlePage.importPage(source.getPage(index))
                    val bytes = singlePage.toPdfBytes()
                    val suffix = "_page_${index + 1}.pdf"
                    outputFiles += pdfStorage.savePdfToAppStorage(bytes, "$baseOutputName$suffix")
                } finally {
                    singlePage.close()
                }
            }
            return outputFiles
        }
    }

    fun splitAllPages(sourceUri: Uri, baseOutputName: String): List<File> {
        val inputStream = appContext.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("Unable to open selected PDF.")
        inputStream.use { stream ->
            PDDocument.load(stream).use { source ->
                if (source.numberOfPages <= 0) {
                    throw IOException("The selected PDF has no pages.")
                }
                val outputFiles = mutableListOf<File>()
                for (index in 0 until source.numberOfPages) {
                    val singlePage = PDDocument()
                    try {
                        singlePage.importPage(source.getPage(index))
                        val bytes = singlePage.toPdfBytes()
                        val suffix = "_page_${index + 1}.pdf"
                        outputFiles += pdfStorage.savePdfToAppStorage(bytes, "$baseOutputName$suffix")
                    } finally {
                        singlePage.close()
                    }
                }
                return outputFiles
            }
        }
    }

    fun extractPages(documentId: String, pageIndices: List<Int>, outputName: String): File {
        val sourceFile = requireSourceFile(documentId)
        val sortedUnique = pageIndices.distinct().sorted()
        if (sortedUnique.isEmpty()) {
            throw IOException("Select at least one page to extract.")
        }
        PDDocument.load(sourceFile).use { source ->
            validatePageIndices(sortedUnique, source.numberOfPages)
            val output = PDDocument()
            try {
                sortedUnique.forEach { pageIndex ->
                    output.importPage(source.getPage(pageIndex))
                }
                return pdfStorage.savePdfToAppStorage(output.toPdfBytes(), outputName)
            } finally {
                output.close()
            }
        }
    }

    fun extractPages(sourceUri: Uri, pageIndices: List<Int>, outputName: String): File {
        val sortedUnique = pageIndices.distinct().sorted()
        if (sortedUnique.isEmpty()) {
            throw IOException("Select at least one page to extract.")
        }
        val inputStream = appContext.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("Unable to open selected PDF.")
        inputStream.use { stream ->
            PDDocument.load(stream).use { source ->
                validatePageIndices(sortedUnique, source.numberOfPages)
                val output = PDDocument()
                try {
                    sortedUnique.forEach { pageIndex ->
                        output.importPage(source.getPage(pageIndex))
                    }
                    return pdfStorage.savePdfToAppStorage(output.toPdfBytes(), outputName)
                } finally {
                    output.close()
                }
            }
        }
    }

    fun reorderPages(documentId: String, pageOrder: List<Int>, outputName: String): File {
        val sourceFile = requireSourceFile(documentId)
        PDDocument.load(sourceFile).use { source ->
            if (pageOrder.size != source.numberOfPages) {
                throw IOException("Page order does not match the PDF page count.")
            }
            validatePageIndices(pageOrder, source.numberOfPages)
            val output = PDDocument()
            try {
                pageOrder.forEach { pageIndex ->
                    output.importPage(source.getPage(pageIndex))
                }
                return pdfStorage.savePdfToAppStorage(output.toPdfBytes(), outputName)
            } finally {
                output.close()
            }
        }
    }

    fun reorderPages(sourceUri: Uri, pageOrder: List<Int>, outputName: String): File {
        val inputStream = appContext.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("Unable to open selected PDF.")
        inputStream.use { stream ->
            PDDocument.load(stream).use { source ->
                if (pageOrder.size != source.numberOfPages) {
                    throw IOException("Page order does not match the PDF page count.")
                }
                validatePageIndices(pageOrder, source.numberOfPages)
                val output = PDDocument()
                try {
                    pageOrder.forEach { pageIndex ->
                        output.importPage(source.getPage(pageIndex))
                    }
                    return pdfStorage.savePdfToAppStorage(output.toPdfBytes(), outputName)
                } finally {
                    output.close()
                }
            }
        }
    }

    private fun validatePageIndices(pageIndices: List<Int>, pageCount: Int) {
        val invalid = pageIndices.firstOrNull { it < 0 || it >= pageCount }
        if (invalid != null) {
            throw IOException("Invalid page index: $invalid")
        }
    }

    private fun requireSourceFile(documentId: String): File {
        return pdfStorage.resolveAppPdfFile(documentId)
            ?: throw FileNotFoundException("Document not found.")
    }

    private fun PDDocument.toPdfBytes(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        this.save(outputStream)
        val bytes = outputStream.toByteArray()
        if (bytes.isEmpty()) {
            throw IOException("Generated PDF is empty.")
        }
        return bytes
    }
}
