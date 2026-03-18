package com.oracle.ee.spentanalyser.data.repository

import com.oracle.ee.spentanalyser.data.database.AppDao
import com.oracle.ee.spentanalyser.data.database.MerchantMappingEntity
import com.oracle.ee.spentanalyser.data.mapper.toDomain
import com.oracle.ee.spentanalyser.data.mapper.toEntity
import com.oracle.ee.spentanalyser.data.mapper.toEntity
import com.oracle.ee.spentanalyser.domain.model.MonthlySpendItem
import com.oracle.ee.spentanalyser.domain.model.SortOption
import com.oracle.ee.spentanalyser.domain.model.Transaction
import com.oracle.ee.spentanalyser.domain.model.TransactionFilterQuery
import com.oracle.ee.spentanalyser.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.sqlite.db.SimpleSQLiteQuery

class TransactionRepositoryImpl(
    private val appDao: AppDao
) : TransactionRepository {

    override fun getAllTransactionsFlow(): Flow<List<Transaction>> =
        appDao.getAllTransactionsFlow().map { entities -> entities.map { it.toDomain() } }

    override fun searchTransactions(query: TransactionFilterQuery): Flow<List<Transaction>> {
        val queryString = StringBuilder("SELECT * FROM transactions WHERE 1=1")
        val args = mutableListOf<Any>()

        query.searchQuery?.takeIf { it.isNotBlank() }?.let {
            queryString.append(" AND (merchant LIKE ? OR category LIKE ?)")
            args.add("%$it%")
            args.add("%$it%")
        }
        
        query.merchant?.takeIf { it.isNotBlank() }?.let {
            queryString.append(" AND merchant = ?")
            args.add(it)
        }
        
        query.category?.takeIf { it.isNotBlank() }?.let {
            queryString.append(" AND category = ?")
            args.add(it)
        }
        
        query.amountMin?.let {
            queryString.append(" AND amount >= ?")
            args.add(it)
        }
        
        query.amountMax?.let {
            queryString.append(" AND amount <= ?")
            args.add(it)
        }
        
        query.type?.takeIf { it.isNotBlank() }?.let {
            queryString.append(" AND type = ?")
            args.add(it)
        }
        
        if (query.startTimestamp != null && query.endTimestamp != null) {
            queryString.append(" AND sourceSmsHash IN (SELECT uniqueHash FROM sms_logs WHERE timestamp >= ? AND timestamp <= ?)")
            args.add(query.startTimestamp)
            args.add(query.endTimestamp)
        } else if (query.startTimestamp != null) {
            queryString.append(" AND sourceSmsHash IN (SELECT uniqueHash FROM sms_logs WHERE timestamp >= ?)")
            args.add(query.startTimestamp)
        }

        when (query.sortBy) {
            SortOption.DATE_DESC -> queryString.append(" ORDER BY id DESC")
            SortOption.DATE_ASC -> queryString.append(" ORDER BY id ASC")
            SortOption.AMOUNT_DESC -> queryString.append(" ORDER BY amount DESC")
            SortOption.AMOUNT_ASC -> queryString.append(" ORDER BY amount ASC")
        }

        val supportQuery = SimpleSQLiteQuery(queryString.toString(), args.toTypedArray())
        return appDao.searchTransactionsFlow(supportQuery).map { entities -> entities.map { it.toDomain() } }
    }

    override fun getTransactionsSinceFlow(sinceTimestamp: Long): Flow<List<Transaction>> =
        appDao.getTransactionsSinceFlow(sinceTimestamp).map { entities -> entities.map { it.toDomain() } }

    override fun getTransactionsBetweenFlow(startTimestamp: Long, endTimestamp: Long): Flow<List<Transaction>> =
        appDao.getTransactionsBetweenFlow(startTimestamp, endTimestamp).map { entities -> entities.map { it.toDomain() } }

    override suspend fun insertTransaction(transaction: Transaction): Long =
        appDao.insertTransaction(transaction.toEntity())

    override suspend fun updateTransaction(transaction: Transaction) =
        appDao.updateTransaction(transaction.toEntity())

    override suspend fun deleteTransaction(id: Int) =
        appDao.deleteTransaction(id)

    override suspend fun clearAllTransactions() =
        appDao.clearAllTransactions()

    override suspend fun saveMerchantMapping(alias: String, normalizedName: String) {
        val mapping = MerchantMappingEntity(
            alias = alias.trim().lowercase(),
            normalizedName = normalizedName.trim()
        )
        appDao.insertMerchantMapping(mapping)
    }

    override suspend fun getNormalizedMerchantOrDefault(alias: String): String {
        val cleanAlias = alias.trim().lowercase()
        return appDao.getNormalizedMerchant(cleanAlias) ?: com.oracle.ee.spentanalyser.domain.util.MerchantNormalizer.normalize(alias)
    }

    override suspend fun getAverageTicketSizeForMerchant(merchant: String): Float =
        appDao.getAverageTicketSizeForMerchant(merchant) ?: 0f

    override suspend fun getFrequencyForMerchant(merchant: String, sinceTimestamp: Long): Int =
        appDao.getFrequencyForMerchant(merchant, sinceTimestamp)

    override suspend fun getMonthlySpendTrendForMerchant(merchant: String): List<MonthlySpendItem> =
        appDao.getMonthlySpendTrendForMerchant(merchant).map { MonthlySpendItem(it.monthYear, it.totalAmount) }

    override suspend fun getAverageTicketSizeForCategory(category: String): Float =
        appDao.getAverageTicketSizeForCategory(category) ?: 0f

    override suspend fun getFrequencyForCategory(category: String, sinceTimestamp: Long): Int =
        appDao.getFrequencyForCategory(category, sinceTimestamp)

    override suspend fun getMonthlySpendTrendForCategory(category: String): List<MonthlySpendItem> =
        appDao.getMonthlySpendTrendForCategory(category).map { MonthlySpendItem(it.monthYear, it.totalAmount) }
}
