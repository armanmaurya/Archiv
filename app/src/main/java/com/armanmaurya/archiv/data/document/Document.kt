package com.armanmaurya.archiv.data.document

data class Document(
    val id: String,
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val modifiedAtMillis: Long
)

