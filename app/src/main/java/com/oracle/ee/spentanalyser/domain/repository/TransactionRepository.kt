package com.oracle.ee.spentanalyser.domain.repository

import com.oracle.ee.spentanalyser.domain.model.MonthlySpendItem
import com.oracle.ee.spentanalyser.domain.model.Transaction
import com.oracle.ee.spentanalyser.domain.model.TransactionFilterQuery
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAllTransactionsFlow(): Flow<List<Transaction>>
    fun searchTransactions(query: TransactionFilterQuery): Flow<List<Transaction>>
    fun getTransactionsSinceFlow(sinceTimestamp: Long): Flow<List<Transaction>>
    fun getTransactionsBetweenFlow(startTimestamp: Long, endTimestamp: Long): Flow<List<Transaction>>
    suspend fun insertTransaction(transaction: Transaction): Long
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(id: Int)
    suspend fun clearAllTransactions()
    
    suspend fun saveMerchantMapping(alias: String, normalizedName: String)
    suspend fun getNormalizedMerchantOrDefault(alias: String): String

    suspend fun getAverageTicketSizeForMerchant(merchant: String): Float
    suspend fun getFrequencyForMerchant(merchant: String, sinceTimestamp: Long): Int
    suspend fun getMonthlySpendTrendForMerchant(merchant: String): List<MonthlySpendItem>
    
    suspend fun getAverageTicketSizeForCategory(category: String): Float
    suspend fun getFrequencyForCategory(category: String, sinceTimestamp: Long): Int
    suspend fun getMonthlySpendTrendForCategory(category: String): List<MonthlySpendItem>
}
