package com.armanmaurya.archiv.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.armanmaurya.archiv.data.local.ArchivDatabase
import com.armanmaurya.archiv.data.local.mappers.toDomainDocument
import com.armanmaurya.archiv.data.local.mappers.toDocumentEntity
import com.armanmaurya.archiv.domain.model.Document
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class DocumentSort {
    MODIFIED_DESC,
    NAME_ASC,
    LAST_OPENED_DESC
}

class DocumentRepository(context: Context) {

    private val appContext = context.applicationContext
    private val documentDao = ArchivDatabase.getInstance(appContext).documentDao()

    suspend fun savePdfToAppStorage(pdfBytes: ByteArray): File {
        return savePdfToAppStorage(pdfBytes, buildDefaultPdfName())
    }

    suspend fun savePdfToAppStorage(pdfBytes: ByteArray, desiredName: String): File {
        if (pdfBytes.isEmpty()) {
            throw IOException("Generated PDF is empty.")
        }
        val outputDir = requireAppDocumentsDir()
        val outputFile = buildUniqueFile(outputDir, ensurePdfExtension(desiredName))

        FileOutputStream(outputFile).use { outputStream ->
            outputStream.write(pdfBytes)
            outputStream.flush()
        }

        if (outputFile.length() == 0L) {
            outputFile.delete()
            throw IOException("Saved PDF is empty.")
        }

        val documentEntity = outputFile.toDocumentEntity(lastOpenedAtMillis = System.currentTimeMillis())
        documentDao.upsert(documentEntity)

        return outputFile
    }

    fun observeDocuments(searchQuery: String, sort: DocumentSort): Flow<List<Document>> {
        val normalizedQuery = searchQuery.trim()
        val query = if (normalizedQuery.isBlank()) "" else normalizedQuery
        val source = when (sort) {
            DocumentSort.MODIFIED_DESC -> documentDao.observeByModifiedDesc(query)
            DocumentSort.NAME_ASC -> documentDao.observeByNameAsc(query)
            DocumentSort.LAST_OPENED_DESC -> documentDao.observeByLastOpenedDesc(query)
        }
        return source.map { documents -> documents.map { it.toDomainDocument() } }
    }

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
        return exportToDownloads(file)
    }

    suspend fun deleteDocument(documentId: String) {
        if (!deleteAppPdfFile(documentId)) {
            throw IOException("Unable to delete the selected document.")
        }
        documentDao.deleteById(documentId)
    }

    suspend fun updateLastOpened(documentId: String) {
        documentDao.updateLastOpened(documentId, System.currentTimeMillis())
    }

    fun listAppPdfFiles(): List<File> {
        val outputDir = requireAppDocumentsDir()
        return outputDir.listFiles()
            ?.asSequence()
            ?.filter { file -> file.isFile && file.extension.equals("pdf", ignoreCase = true) }
            ?.sortedByDescending { file -> file.lastModified() }
            ?.toList()
            ?: emptyList()
    }

    fun resolveAppPdfFile(documentId: String): File? {
        if (documentId.contains('\\') || documentId.contains('/')) {
            return null
        }
        val outputFile = File(requireAppDocumentsDir(), documentId)
        return if (
            outputFile.exists() &&
            outputFile.isFile &&
            outputFile.extension.equals("pdf", ignoreCase = true)
        ) {
            outputFile
        } else {
            null
        }
    }

    fun deleteAppPdfFile(documentId: String): Boolean {
        val outputFile = resolveAppPdfFile(documentId) ?: return false
        return outputFile.delete()
    }

    fun exportToDownloads(sourceFile: File): Uri {
        if (!sourceFile.exists() || !sourceFile.isFile) {
            throw IOException("Source PDF does not exist.")
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportToMediaStore(sourceFile)
        } else {
            exportToLegacyDownloads(sourceFile)
        }
    }

    private fun requireDocumentFile(documentId: String): File {
        return resolveAppPdfFile(documentId)
            ?: throw FileNotFoundException("Document not found.")
    }

    private fun requireAppDocumentsDir(): File {
        val outputDir = appContext.getExternalFilesDir("documents")
            ?: throw IOException("App documents directory is unavailable.")
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IOException("Unable to create app documents directory.")
        }
        return outputDir
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToMediaStore(sourceFile: File): Uri {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val destinationUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to create export destination in Downloads.")

        try {
            sourceFile.inputStream().use { inputStream ->
                resolver.openOutputStream(destinationUri, "w")?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                    outputStream.flush()
                } ?: throw IOException("Unable to open export destination output stream.")
            }

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

    @Suppress("DEPRECATION")
    private fun exportToLegacyDownloads(sourceFile: File): Uri {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IOException("Downloads directory is unavailable.")
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            throw IOException("Unable to create Downloads directory.")
        }

        val destinationFile = buildUniqueFile(downloadsDir, sourceFile.name)
        sourceFile.inputStream().use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
                outputStream.flush()
            }
        }

        if (destinationFile.length() == 0L) {
            destinationFile.delete()
            throw IOException("Exported PDF is empty.")
        }

        MediaScannerConnection.scanFile(
            appContext,
            arrayOf(destinationFile.absolutePath),
            arrayOf("application/pdf"),
            null
        )

        return destinationFile.toUri()
    }

    private fun buildUniqueFile(directory: File, desiredName: String): File {
        val sanitizedName = desiredName.ifBlank { buildDefaultPdfName() }
        val baseName = sanitizedName.substringBeforeLast('.', sanitizedName)
        val extension = sanitizedName.substringAfterLast('.', "pdf")
        var candidate = File(directory, "$baseName.$extension")
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "${'$'}{baseName}_$index.$extension")
            index++
        }
        return candidate
    }

    private fun buildDefaultPdfName(): String {
        val timestamp = SimpleDateFormat("dd MMMM yyyy hh-mm a", Locale.ENGLISH)
            .format(Date())
        return "Archiv $timestamp.pdf"
    }

    private fun ensurePdfExtension(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return buildDefaultPdfName()
        }
        return if (trimmed.endsWith(".pdf", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed.pdf"
        }
    }

}
