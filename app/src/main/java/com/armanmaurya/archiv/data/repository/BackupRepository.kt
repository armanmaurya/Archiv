package com.armanmaurya.archiv.data.repository

import android.content.Context
import android.net.Uri
import com.armanmaurya.archiv.data.local.ArchivDatabase
import com.armanmaurya.archiv.data.local.entities.TagEntity
import com.armanmaurya.archiv.data.local.mappers.toDocumentEntity
import androidx.core.net.toUri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class ImportConflictStrategy { SKIP, RENAME, OVERWRITE }

class BackupRepository(context: Context) {

    private val appContext = context.applicationContext
    private val database = ArchivDatabase.getInstance(appContext)
    private val documentDao = database.documentDao()
    private val tagDao = database.tagDao()
    private val documentTagDao = database.documentTagDao()

    // ── Export ──────────────────────────────────────────────────────────────

    /** Writes the full backup ZIP to [destUri] chosen by the user via system save dialog. */
    suspend fun exportBackup(destUri: Uri): Int {
        val allDocs = documentDao.getAll()
        if (allDocs.isEmpty()) throw IOException("No documents to export.")

        val docsDir = requireAppDocumentsDir()

        // Build metadata JSON
        val metaArray = JSONArray()
        for (dwt in allDocs) {
            val doc = dwt.document
            val tagsArr = JSONArray().apply { dwt.tags.forEach { put(it.name) } }
            metaArray.put(
                JSONObject().apply {
                    put("id", doc.id)
                    put("name", doc.fileName)
                    put("tags", tagsArr)
                    put("file", "documents/${doc.id}")
                }
            )
        }
        val metadata = JSONObject().apply {
            put("version", 1)
            put("exportedAt", isoNow())
            put("documents", metaArray)
        }

        // Write directly to the Uri the user picked
        val resolver = appContext.contentResolver
        resolver.openOutputStream(destUri)
            ?.buffered()
            ?.let { outputStream ->
                ZipOutputStream(outputStream).use { zos ->
                    // metadata.json
                    zos.putNextEntry(ZipEntry("metadata.json"))
                    zos.write(metadata.toString(2).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // PDFs
                    for (dwt in allDocs) {
                        currentCoroutineContext().ensureActive()
                        val srcFile = File(docsDir, dwt.document.id)
                        if (!srcFile.exists() || !srcFile.isFile) continue
                        zos.putNextEntry(ZipEntry("documents/${dwt.document.id}"))
                        srcFile.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            } ?: throw IOException("Cannot open output stream for backup file.")

        return allDocs.size
    }

    // ── Import ──────────────────────────────────────────────────────────────

    data class ImportResult(val imported: Int, val skipped: Int, val failed: Int)

    suspend fun importBackup(uri: Uri, strategy: ImportConflictStrategy): ImportResult {
        val resolver = appContext.contentResolver
        val docsDir = requireAppDocumentsDir()

        var imported = 0
        var skipped = 0
        var failed = 0

        // ── Pass 1: read only metadata.json (tiny text, safe to buffer) ──────
        val metaBytes = resolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream.buffered()).use { zis ->
                var entry = zis.nextEntry
                var result: ByteArray? = null
                while (entry != null) {
                    if (!entry.isDirectory && entry.name == "metadata.json") {
                        result = zis.readBytes()
                        break
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                result
            }
        } ?: throw IOException("Cannot open backup file.")
        metaBytes ?: throw IOException("Invalid backup: metadata.json missing.")

        val metadata = JSONObject(String(metaBytes, Charsets.UTF_8))
        val docsArray = metadata.getJSONArray("documents")

        // Build lookup: zip entry path → (originalId, tags)
        data class DocMeta(val originalId: String, val tags: List<String>)
        val docMetaByPath = mutableMapOf<String, DocMeta>()
        for (i in 0 until docsArray.length()) {
            val obj = docsArray.getJSONObject(i)
            val entryPath = obj.getString("file")
            val originalId = obj.getString("id")
            val tags = obj.getJSONArray("tags").let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
            docMetaByPath[entryPath] = DocMeta(originalId, tags)
        }

        // ── Pass 2: stream each PDF entry directly to disk ────────────────────
        resolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream.buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    currentCoroutineContext().ensureActive()
                    val meta = if (!entry.isDirectory) docMetaByPath[entry.name] else null
                    if (meta != null) {
                        try {
                            val existingFile = File(docsDir, meta.originalId)
                            val targetFile: File? = when {
                                !existingFile.exists() -> existingFile
                                strategy == ImportConflictStrategy.SKIP -> null
                                strategy == ImportConflictStrategy.OVERWRITE -> {
                                    documentDao.deleteById(meta.originalId)
                                    existingFile.delete()
                                    existingFile
                                }
                                else /* RENAME */ -> buildUniqueFile(docsDir, meta.originalId)
                            }

                            if (targetFile == null) {
                                skipped++
                            } else {
                                // Stream directly: no full-file ByteArray ever in memory
                                FileOutputStream(targetFile).use { out -> zis.copyTo(out) }

                                val entity = targetFile.toDocumentEntity(lastOpenedAtMillis = null)
                                documentDao.upsert(entity)

                                if (meta.tags.isNotEmpty()) {
                                    tagDao.insertAll(meta.tags.map { TagEntity(name = it) })
                                    val storedTags = tagDao.getByNames(meta.tags)
                                    documentTagDao.replaceTags(entity.id, storedTags.map { it.id })
                                    tagDao.deleteOrphanedTags()
                                }

                                imported++
                            }
                        } catch (_: Exception) {
                            failed++
                        }
                    }
                    // Do NOT call zis.closeEntry() before copyTo — ZipInputStream
                    // advances to next entry automatically after copyTo exhausts it.
                    entry = zis.nextEntry
                }
            }
        } ?: throw IOException("Cannot re-open backup file for import.")

        return ImportResult(imported = imported, skipped = skipped, failed = failed)
    }


    // ── Private helpers ─────────────────────────────────────────────────────

    private fun requireAppDocumentsDir(): File {
        val dir = appContext.getExternalFilesDir("documents")
            ?: throw IOException("App documents directory is unavailable.")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Unable to create app documents directory.")
        }
        return dir
    }

    private fun buildUniqueFile(directory: File, desiredName: String): File {
        val baseName = desiredName.substringBeforeLast('.', desiredName)
        val extension = desiredName.substringAfterLast('.', "pdf")
        var candidate = File(directory, "$baseName.$extension")
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "${baseName}_$index.$extension")
            index++
        }
        return candidate
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(Date())
}
