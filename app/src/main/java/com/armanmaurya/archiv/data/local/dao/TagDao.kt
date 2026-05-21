package com.armanmaurya.archiv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.armanmaurya.archiv.data.local.entities.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tags: List<TagEntity>): List<Long>

    @Query("SELECT * FROM tags WHERE name IN (:names)")
    suspend fun getByNames(names: List<String>): List<TagEntity>

    @Query("SELECT name FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeAllNames(): Flow<List<String>>

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tagId FROM document_tags)")
    suspend fun deleteOrphanedTags()
}
