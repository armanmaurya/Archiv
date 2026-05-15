package com.armanmaurya.archiv.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["fileName"]),
        Index(value = ["modifiedAtMillis"]),
        Index(value = ["lastOpenedAtMillis"])
    ]
)
data class DocumentEntity(
    @PrimaryKey
    val id: String,
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val modifiedAtMillis: Long,
    val lastOpenedAtMillis: Long?
)
