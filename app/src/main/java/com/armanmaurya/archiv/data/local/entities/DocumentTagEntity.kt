package com.armanmaurya.archiv.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "document_tags",
    primaryKeys = ["documentId", "tagId"],
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["tagId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DocumentTagEntity(
    val documentId: String,
    val tagId: Long
)
