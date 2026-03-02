package com.oracle.ee.spentanalyser.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ParseStatus {
    PENDING, SUCCESS, ERROR, MANUAL_REVIEW, IGNORED
}

@Entity(
    tableName = "sms_logs",
    indices = [androidx.room.Index(value = ["uniqueHash"])]
)
data class SmsLogEntity(
    @PrimaryKey val uniqueHash: String, // Use timestamp + sender + body hash as a unique key for the SMS
    val sender: String,
    val body: String,
    val timestamp: Long,
    val status: String, // String representation of ParseStatus
    val errorMessage: String? = null,
    val rawLlmOutput: String? = null
)
