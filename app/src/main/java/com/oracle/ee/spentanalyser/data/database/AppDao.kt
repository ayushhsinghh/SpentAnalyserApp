package com.oracle.ee.spentanalyser.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    
    // --- Transactions ---
    @Query("SELECT * FROM transactions ORDER BY id DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE sourceSmsHash IN (SELECT uniqueHash FROM sms_logs WHERE timestamp >= :sinceTimestamp) ORDER BY id DESC")
    fun getTransactionsSinceFlow(sinceTimestamp: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE sourceSmsHash IN (SELECT uniqueHash FROM sms_logs WHERE timestamp >= :startTimestamp AND timestamp <= :endTimestamp) ORDER BY id DESC")
    fun getTransactionsBetweenFlow(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionEntity>>

    @RawQuery(observedEntities = [TransactionEntity::class])
    fun searchTransactionsFlow(query: SupportSQLiteQuery): Flow<List<TransactionEntity>>

    // --- Analytics Aggregation ---
    @Query("SELECT AVG(amount) FROM transactions WHERE merchant = :merchant AND type = 'DEBIT'")
    suspend fun getAverageTicketSizeForMerchant(merchant: String): Float?

    @Query("SELECT COUNT(id) FROM transactions WHERE merchant = :merchant AND type = 'DEBIT' AND timestamp >= :sinceTimestamp")
    suspend fun getFrequencyForMerchant(merchant: String, sinceTimestamp: Long): Int

    @Query("SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') as monthYear, SUM(amount) as totalAmount FROM transactions WHERE merchant = :merchant AND type = 'DEBIT' GROUP BY monthYear ORDER BY monthYear ASC")
    suspend fun getMonthlySpendTrendForMerchant(merchant: String): List<MonthlySpendProjection>

    @Query("SELECT AVG(amount) FROM transactions WHERE category = :category AND type = 'DEBIT'")
    suspend fun getAverageTicketSizeForCategory(category: String): Float?

    @Query("SELECT COUNT(id) FROM transactions WHERE category = :category AND type = 'DEBIT' AND timestamp >= :sinceTimestamp")
    suspend fun getFrequencyForCategory(category: String, sinceTimestamp: Long): Int

    @Query("SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') as monthYear, SUM(amount) as totalAmount FROM transactions WHERE category = :category AND type = 'DEBIT' GROUP BY monthYear ORDER BY monthYear ASC")
    suspend fun getMonthlySpendTrendForCategory(category: String): List<MonthlySpendProjection>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long
    
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Int)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    // --- SMS Logs ---
    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC")
    fun getAllSmsLogsFlow(): Flow<List<SmsLogEntity>>
    
    @Query("SELECT * FROM sms_logs WHERE status != 'SUCCESS' AND status != 'IGNORED'")
    suspend fun getUnprocessedSmsLogs(): List<SmsLogEntity>

    @Query("DELETE FROM sms_logs")
    suspend fun clearAllSmsLogs()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSmsLog(log: SmsLogEntity): Long
    
    @Query("SELECT EXISTS(SELECT 1 FROM sms_logs WHERE uniqueHash = :hash)")
    suspend fun doesSmsLogExist(hash: String): Boolean
    
    @Query("SELECT * FROM sms_logs WHERE uniqueHash = :hash LIMIT 1")
    suspend fun getSmsLogByHash(hash: String): SmsLogEntity?
    
    @androidx.room.Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("UPDATE sms_logs SET status = :status WHERE uniqueHash = :hash")
    suspend fun updateSmsLogStatus(hash: String, status: String)

    // --- Merchant Mappings ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMerchantMapping(mapping: MerchantMappingEntity)

    @Query("SELECT normalizedName FROM merchant_mappings WHERE alias = :alias LIMIT 1")
    suspend fun getNormalizedMerchant(alias: String): String?
}
