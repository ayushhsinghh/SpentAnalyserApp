package com.oracle.ee.spentanalyser.presentation.transactions

import com.oracle.ee.spentanalyser.domain.model.Transaction

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList()
)
