package com.armanmaurya.archiv.data.local.mappers

import com.armanmaurya.archiv.data.local.entities.DocumentEntity
import com.armanmaurya.archiv.domain.model.Document
import java.io.File

fun DocumentEntity.toDomainDocument(): Document {
    return Document(
        id = id,
        fileName = fileName,
        filePath = filePath,
        fileSizeBytes = fileSizeBytes,
        modifiedAtMillis = modifiedAtMillis
    )
}

fun File.toDocumentEntity(lastOpenedAtMillis: Long?): DocumentEntity {
    return DocumentEntity(
        id = name,
        fileName = name,
        filePath = absolutePath,
        fileSizeBytes = length(),
        modifiedAtMillis = lastModified(),
        lastOpenedAtMillis = lastOpenedAtMillis
    )
}
