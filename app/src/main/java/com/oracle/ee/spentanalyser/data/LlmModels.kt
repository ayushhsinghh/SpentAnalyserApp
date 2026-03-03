package com.oracle.ee.spentanalyser.data

import kotlinx.serialization.Serializable

/**
 * DTO for the structured transaction data parsed by the LLM.
 */
@Serializable
data class TransactionData(
    val amount: Double,
    val merchant: String,
    val date: String,
    val type: String // E.g: "DEBIT", "CREDIT"
)

/**
 * Result wrapper for an LLM parse attempt.
 */
data class ParseResult(
    val success: Boolean,
    val data: TransactionData? = null,
    val rawOutput: String = "",
    val error: String? = null
)
