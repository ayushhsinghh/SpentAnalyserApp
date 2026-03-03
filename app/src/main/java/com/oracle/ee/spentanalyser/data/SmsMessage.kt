package com.oracle.ee.spentanalyser.data

/**
 * Represents a raw SMS message read from the device inbox.
 * This is a Data-layer DTO, not a domain entity.
 */
data class SmsMessage(
    val uniqueHash: String,
    val sender: String,
    val body: String,
    val timestamp: Long
)
