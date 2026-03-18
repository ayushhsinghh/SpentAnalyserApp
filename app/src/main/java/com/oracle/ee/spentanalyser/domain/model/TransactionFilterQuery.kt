package com.oracle.ee.spentanalyser.domain.model

data class TransactionFilterQuery(
    val searchQuery: String? = null,
    val merchant: String? = null,
    val category: String? = null,
    val amountMin: Double? = null,
    val amountMax: Double? = null,
    val type: String? = null, // "DEBIT", "CREDIT"
    val startTimestamp: Long? = null,
    val endTimestamp: Long? = null,
    val sortBy: SortOption = SortOption.DATE_DESC
)

enum class SortOption {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC
}
