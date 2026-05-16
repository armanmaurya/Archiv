package com.armanmaurya.archiv.data.local.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.armanmaurya.archiv.data.local.entities.DocumentEntity
import com.armanmaurya.archiv.data.local.entities.DocumentTagEntity
import com.armanmaurya.archiv.data.local.entities.TagEntity

data class DocumentWithTags(
    @Embedded val document: DocumentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = DocumentTagEntity::class,
            parentColumn = "documentId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)
