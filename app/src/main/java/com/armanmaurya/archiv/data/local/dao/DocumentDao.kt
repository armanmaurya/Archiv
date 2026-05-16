package com.armanmaurya.archiv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.armanmaurya.archiv.data.local.entities.DocumentEntity
import com.armanmaurya.archiv.data.local.relations.DocumentWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Transaction
    @Query(
        "SELECT * FROM documents " +
            "WHERE fileName LIKE '%' || :query || '%' " +
            "ORDER BY modifiedAtMillis DESC"
    )
    fun observeByModifiedDesc(query: String): Flow<List<DocumentWithTags>>

    @Transaction
    @Query(
        "SELECT * FROM documents " +
            "WHERE fileName LIKE '%' || :query || '%' " +
            "ORDER BY fileName COLLATE NOCASE ASC"
    )
    fun observeByNameAsc(query: String): Flow<List<DocumentWithTags>>

    @Transaction
    @Query(
        "SELECT * FROM documents " +
            "WHERE fileName LIKE '%' || :query || '%' " +
            "ORDER BY COALESCE(lastOpenedAtMillis, 0) DESC, modifiedAtMillis DESC"
    )
    fun observeByLastOpenedDesc(query: String): Flow<List<DocumentWithTags>>

    @Transaction
    @Query(
        "SELECT * FROM documents " +
            "WHERE fileName LIKE '%' || :query || '%' " +
            "AND id IN (" +
            "SELECT documentId FROM document_tags " +
            "INNER JOIN tags ON tags.id = document_tags.tagId " +
            "WHERE tags.name IN (:tags) " +
            "GROUP BY documentId " +
            "HAVING COUNT(DISTINCT tags.name) = :tagCount" +
            ") " +
            "ORDER BY modifiedAtMillis DESC"
    )
    fun observeByModifiedDescWithTags(
        query: String,
        tags: List<String>,
        tagCount: Int
    ): Flow<List<DocumentWithTags>>

    @Transaction
    @Query(
        "SELECT * FROM documents " +
            "WHERE fileName LIKE '%' || :query || '%' " +
            "AND id IN (" +
            "SELECT documentId FROM document_tags " +
            "INNER JOIN tags ON tags.id = document_tags.tagId " +
            "WHERE tags.name IN (:tags) " +
            "GROUP BY documentId " +
            "HAVING COUNT(DISTINCT tags.name) = :tagCount" +
            ") " +
            "ORDER BY fileName COLLATE NOCASE ASC"
    )
    fun observeByNameAscWithTags(
        query: String,
        tags: List<String>,
        tagCount: Int
    ): Flow<List<DocumentWithTags>>

    @Transaction
    @Query(
        "SELECT * FROM documents " +
            "WHERE fileName LIKE '%' || :query || '%' " +
            "AND id IN (" +
            "SELECT documentId FROM document_tags " +
            "INNER JOIN tags ON tags.id = document_tags.tagId " +
            "WHERE tags.name IN (:tags) " +
            "GROUP BY documentId " +
            "HAVING COUNT(DISTINCT tags.name) = :tagCount" +
            ") " +
            "ORDER BY COALESCE(lastOpenedAtMillis, 0) DESC, modifiedAtMillis DESC"
    )
    fun observeByLastOpenedDescWithTags(
        query: String,
        tags: List<String>,
        tagCount: Int
    ): Flow<List<DocumentWithTags>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Query("UPDATE documents SET lastOpenedAtMillis = :timestamp WHERE id = :documentId")
    suspend fun updateLastOpened(documentId: String, timestamp: Long)

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteById(documentId: String)
}
