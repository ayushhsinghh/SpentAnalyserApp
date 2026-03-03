package com.oracle.ee.spentanalyser.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oracle.ee.spentanalyser.domain.model.Transaction
import com.oracle.ee.spentanalyser.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val smsLogRepository: com.oracle.ee.spentanalyser.domain.repository.SmsLogRepository
) : ViewModel() {

    val transactions: StateFlow<List<Transaction>> =
        transactionRepository.getAllTransactionsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateExistingTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                transactionRepository.updateTransaction(transaction)
            } catch (e: Exception) {
                Timber.e(e, "Error updating transaction")
            }
        }
    }

    fun deleteTransaction(id: Int) {
        viewModelScope.launch {
            try {
                transactionRepository.deleteTransaction(id)
            } catch (e: Exception) {
                Timber.e(e, "Error deleting transaction")
            }
        }
    }

    suspend fun getSourceSms(hash: String): com.oracle.ee.spentanalyser.domain.model.SmsLog? {
        return try {
            smsLogRepository.getSmsLogByHash(hash)
        } catch (e: Exception) {
            Timber.e(e, "Error fetching source SMS for hash: $hash")
            null
        }
    }
}
