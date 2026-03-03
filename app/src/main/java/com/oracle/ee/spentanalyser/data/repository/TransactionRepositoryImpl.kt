package com.oracle.ee.spentanalyser.data.repository

import com.oracle.ee.spentanalyser.data.database.AppDao
import com.oracle.ee.spentanalyser.data.mapper.toDomain
import com.oracle.ee.spentanalyser.data.mapper.toEntity
import com.oracle.ee.spentanalyser.domain.model.Transaction
import com.oracle.ee.spentanalyser.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepositoryImpl(
    private val appDao: AppDao
) : TransactionRepository {

    override fun getAllTransactionsFlow(): Flow<List<Transaction>> =
        appDao.getAllTransactionsFlow().map { entities -> entities.map { it.toDomain() } }

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
}
