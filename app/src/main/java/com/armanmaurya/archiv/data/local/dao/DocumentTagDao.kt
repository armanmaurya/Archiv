package com.armanmaurya.archiv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.armanmaurya.archiv.data.local.entities.DocumentTagEntity

@Dao
interface DocumentTagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(links: List<DocumentTagEntity>)

    @Query("DELETE FROM document_tags WHERE documentId = :documentId")
    suspend fun deleteByDocumentId(documentId: String)

    @Query("UPDATE document_tags SET documentId = :newId WHERE documentId = :oldId")
    suspend fun updateDocumentId(oldId: String, newId: String)

    @Transaction
    suspend fun replaceTags(documentId: String, tagIds: List<Long>) {
        deleteByDocumentId(documentId)
        if (tagIds.isNotEmpty()) {
            val links = tagIds.map { tagId ->
                DocumentTagEntity(documentId = documentId, tagId = tagId)
            }
            insertAll(links)
        }
    }
}
