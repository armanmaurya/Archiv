package com.armanmaurya.archiv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.armanmaurya.archiv.data.local.entities.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query(
        "SELECT * FROM documents " +
            "WHERE fileName LIKE '%' || :query || '%' " +
            "ORDER BY modifiedAtMillis DESC"
    )
    fun observeByModifiedDesc(query: String): Flow<List<DocumentEntity>>

    @Query(
        "SELECT * FROM documents " +
            "WHERE fileName LIKE '%' || :query || '%' " +
            "ORDER BY fileName COLLATE NOCASE ASC"
    )
    fun observeByNameAsc(query: String): Flow<List<DocumentEntity>>

    @Query(
        "SELECT * FROM documents " +
            "WHERE fileName LIKE '%' || :query || '%' " +
            "ORDER BY COALESCE(lastOpenedAtMillis, 0) DESC, modifiedAtMillis DESC"
    )
    fun observeByLastOpenedDesc(query: String): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Query("UPDATE documents SET lastOpenedAtMillis = :timestamp WHERE id = :documentId")
    suspend fun updateLastOpened(documentId: String, timestamp: Long)

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteById(documentId: String)
}
