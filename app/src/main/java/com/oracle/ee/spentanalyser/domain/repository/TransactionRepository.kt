package com.oracle.ee.spentanalyser.domain.repository

import com.oracle.ee.spentanalyser.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAllTransactionsFlow(): Flow<List<Transaction>>
    fun getTransactionsSinceFlow(sinceTimestamp: Long): Flow<List<Transaction>>
    fun getTransactionsBetweenFlow(startTimestamp: Long, endTimestamp: Long): Flow<List<Transaction>>
    suspend fun insertTransaction(transaction: Transaction): Long
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(id: Int)
}
