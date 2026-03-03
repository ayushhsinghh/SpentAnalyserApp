package com.oracle.ee.spentanalyser.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long
    
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Int)
    
    // --- SMS Logs ---
    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC")
    fun getAllSmsLogsFlow(): Flow<List<SmsLogEntity>>
    
    @Query("SELECT * FROM sms_logs WHERE status != 'SUCCESS' AND status != 'IGNORED'")
    suspend fun getUnprocessedSmsLogs(): List<SmsLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSmsLog(log: SmsLogEntity): Long
    
    @Query("SELECT EXISTS(SELECT 1 FROM sms_logs WHERE uniqueHash = :hash)")
    suspend fun doesSmsLogExist(hash: String): Boolean
    
    @androidx.room.Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("UPDATE sms_logs SET status = :status WHERE uniqueHash = :hash")
    suspend fun updateSmsLogStatus(hash: String, status: String)
}
