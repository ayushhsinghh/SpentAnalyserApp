package com.oracle.ee.spentanalyser.domain.repository

import com.oracle.ee.spentanalyser.domain.model.ParseStatus
import com.oracle.ee.spentanalyser.domain.model.SmsLog
import kotlinx.coroutines.flow.Flow

interface SmsLogRepository {
    fun getAllSmsLogsFlow(): Flow<List<SmsLog>>
    suspend fun insertSmsLog(log: SmsLog): Long
    suspend fun doesSmsLogExist(hash: String): Boolean
    suspend fun updateSmsLogStatus(hash: String, status: ParseStatus)
    suspend fun getSmsLogByHash(hash: String): SmsLog?
    suspend fun clearAllSmsLogs()
}
