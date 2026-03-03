package com.oracle.ee.spentanalyser.domain.model

enum class ParseStatus {
    PENDING, SUCCESS, ERROR, MANUAL_REVIEW, IGNORED
}

data class SmsLog(
    val uniqueHash: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val status: ParseStatus,
    val errorMessage: String? = null,
    val rawLlmOutput: String? = null
)
