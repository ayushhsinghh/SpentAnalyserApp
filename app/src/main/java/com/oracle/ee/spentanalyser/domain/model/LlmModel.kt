package com.oracle.ee.spentanalyser.domain.model

data class LlmModel(
    val id: String,
    val name: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val checksum: String? = null,
    val isDownloaded: Boolean = false,
    val isActive: Boolean = false
)
