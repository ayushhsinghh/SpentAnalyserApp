package com.oracle.ee.spentanalyser.data.repository

import com.oracle.ee.spentanalyser.data.database.AppDao
import com.oracle.ee.spentanalyser.data.mapper.toDomain
import com.oracle.ee.spentanalyser.data.mapper.toEntity
import com.oracle.ee.spentanalyser.domain.model.ParseStatus
import com.oracle.ee.spentanalyser.domain.model.SmsLog
import com.oracle.ee.spentanalyser.domain.repository.SmsLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SmsLogRepositoryImpl(
    private val appDao: AppDao
) : SmsLogRepository {

    override fun getAllSmsLogsFlow(): Flow<List<SmsLog>> =
        appDao.getAllSmsLogsFlow().map { entities -> entities.map { it.toDomain() } }

    override suspend fun insertSmsLog(log: SmsLog): Long =
        appDao.insertSmsLog(log.toEntity())

    override suspend fun doesSmsLogExist(hash: String): Boolean =
        appDao.doesSmsLogExist(hash)

    override suspend fun updateSmsLogStatus(hash: String, status: ParseStatus) =
        appDao.updateSmsLogStatus(hash, status.name)

    override suspend fun getSmsLogByHash(hash: String): SmsLog? {
        return appDao.getSmsLogByHash(hash)?.let { entity ->
            SmsLog(
                uniqueHash = entity.uniqueHash,
                sender = entity.sender,
                body = entity.body,
                timestamp = entity.timestamp,
                status = ParseStatus.valueOf(entity.status)
            )
        }
    }
}
