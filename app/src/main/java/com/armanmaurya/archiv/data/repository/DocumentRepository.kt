package com.armanmaurya.archiv.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.armanmaurya.archiv.data.local.ArchivDatabase
import com.armanmaurya.archiv.data.local.mappers.toDomainDocument
import com.armanmaurya.archiv.data.local.mappers.toDocumentEntity
import com.armanmaurya.archiv.data.local.entities.TagEntity
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
    private val database = ArchivDatabase.getInstance(appContext)
    private val documentDao = database.documentDao()
    private val tagDao = database.tagDao()
    private val documentTagDao = database.documentTagDao()

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

    fun observeDocuments(
        searchQuery: String,
        sort: DocumentSort,
        selectedTags: List<String> = emptyList()
    ): Flow<List<Document>> {
        val normalizedQuery = searchQuery.trim()
        val query = if (normalizedQuery.isBlank()) "" else normalizedQuery
        val tags = normalizeTagNames(selectedTags)
        val source = if (tags.isEmpty()) {
            when (sort) {
                DocumentSort.MODIFIED_DESC -> documentDao.observeByModifiedDesc(query)
                DocumentSort.NAME_ASC -> documentDao.observeByNameAsc(query)
                DocumentSort.LAST_OPENED_DESC -> documentDao.observeByLastOpenedDesc(query)
            }
        } else {
            val tagCount = tags.size
            when (sort) {
                DocumentSort.MODIFIED_DESC -> documentDao.observeByModifiedDescWithTags(
                    query = query,
                    tags = tags,
                    tagCount = tagCount
                )
                DocumentSort.NAME_ASC -> documentDao.observeByNameAscWithTags(
                    query = query,
                    tags = tags,
                    tagCount = tagCount
                )
                DocumentSort.LAST_OPENED_DESC -> documentDao.observeByLastOpenedDescWithTags(
                    query = query,
                    tags = tags,
                    tagCount = tagCount
                )
            }
        }
        return source.map { documents -> documents.map { it.toDomainDocument() } }
    }

    fun observeTagNames(): Flow<List<String>> {
        return tagDao.observeAllNames()
    }

    suspend fun updateDocumentTags(documentId: String, rawTags: List<String>) {
        val tags = normalizeTagNames(rawTags)
        if (tags.isEmpty()) {
            documentTagDao.replaceTags(documentId, emptyList())
            tagDao.deleteOrphanedTags()
            return
        }
        tagDao.insertAll(tags.map { TagEntity(name = it) })
        val storedTags = tagDao.getByNames(tags)
        val tagIds = storedTags.map { it.id }
        documentTagDao.replaceTags(documentId, tagIds)
        tagDao.deleteOrphanedTags()
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

    data class PdfImportResult(
        val imported: Int,
        val failed: Int
    )

    suspend fun importPdfDocuments(uris: List<Uri>): PdfImportResult {
        var importedCount = 0
        var failedCount = 0
        for (uri in uris) {
            val imported = runCatching { importSinglePdf(uri) }.isSuccess
            if (imported) {
                importedCount++
            } else {
                failedCount++
            }
        }
        return PdfImportResult(imported = importedCount, failed = failedCount)
    }

    suspend fun deleteDocument(documentId: String) {
        if (!deleteAppPdfFile(documentId)) {
            throw IOException("Unable to delete the selected document.")
        }
        documentDao.deleteById(documentId)
        tagDao.deleteOrphanedTags()
    }

    suspend fun renameDocument(oldDocumentId: String, desiredName: String): File {
        val oldFile = resolveAppPdfFile(oldDocumentId)
            ?: throw java.io.FileNotFoundException("Document not found.")

        val outputDir = requireAppDocumentsDir()
        val newFile = buildUniqueFile(outputDir, ensurePdfExtension(desiredName))

        val moved = try {
            oldFile.renameTo(newFile)
        } catch (_: Exception) {
            false
        }

        if (!moved) {
            // fallback to copy & delete
            try {
                oldFile.inputStream().use { input ->
                    FileOutputStream(newFile).use { out ->
                        input.copyTo(out)
                        out.flush()
                    }
                }
                if (!oldFile.delete()) {
                    // best effort: if delete fails, remove the new file to avoid duplicates
                    newFile.delete()
                    throw IOException("Unable to remove original file after copy.")
                }
            } catch (ex: Exception) {
                if (newFile.exists()) newFile.delete()
                throw IOException("Unable to rename document: ${ex.message}")
            }
        }

        if (!newFile.exists() || !newFile.isFile) {
            throw IOException("Renamed file is unavailable.")
        }

        // Insert new DB record and move tag links
        val newEntity = newFile.toDocumentEntity(lastOpenedAtMillis = null)
        documentDao.upsert(newEntity)
        try {
            documentTagDao.updateDocumentId(oldDocumentId, newEntity.id)
        } catch (_: Exception) {
            // ignore, best-effort
        }
        documentDao.deleteById(oldDocumentId)

        return newFile
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

    private suspend fun importSinglePdf(uri: Uri) {
        val resolver = appContext.contentResolver
        val displayName = getDisplayName(resolver, uri) ?: uri.lastPathSegment
        val desiredName = displayName ?: buildDefaultPdfName()
        val outputDir = requireAppDocumentsDir()
        val outputFile = buildUniqueFile(outputDir, ensurePdfExtension(desiredName))

        resolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(outputFile).use { outputStream ->
                inputStream.copyTo(outputStream)
                outputStream.flush()
            }
        } ?: throw IOException("Unable to read selected PDF.")

        if (outputFile.length() == 0L) {
            outputFile.delete()
            throw IOException("Imported PDF is empty.")
        }

        val documentEntity = outputFile.toDocumentEntity(lastOpenedAtMillis = System.currentTimeMillis())
        documentDao.upsert(documentEntity)
    }

    private fun getDisplayName(resolver: android.content.ContentResolver, uri: Uri): String? {
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)
                } else {
                    null
                }
            }
    }

    private fun requireAppDocumentsDir(): File {
        val outputDir = appContext.getExternalFilesDir("documents")
            ?: throw IOException("App documents directory is unavailable.")
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IOException("Unable to create app documents directory.")
        }
        return outputDir
    }

    private fun normalizeTagNames(rawTags: List<String>): List<String> {
        if (rawTags.isEmpty()) return emptyList()
        val seen = LinkedHashSet<String>()
        rawTags.forEach { tag ->
            val normalized = tag.trim()
            if (normalized.isNotEmpty()) {
                seen.add(normalized)
            }
        }
        return seen.toList()
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
