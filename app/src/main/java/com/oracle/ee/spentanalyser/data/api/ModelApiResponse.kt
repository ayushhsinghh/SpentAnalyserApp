package com.oracle.ee.spentanalyser.data.api

import kotlinx.serialization.Serializable

@Serializable
data class ModelApiResponse(
    val id: String,
    val name: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val lastModified: String? = null,
    val checksum: String? = null
)
