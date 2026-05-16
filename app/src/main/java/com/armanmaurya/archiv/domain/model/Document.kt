package com.armanmaurya.archiv.domain.model

data class Document(
    val id: String,
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val modifiedAtMillis: Long,
    val tags: List<String> = emptyList()
)