package com.oracle.ee.spentanalyser.presentation.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oracle.ee.spentanalyser.domain.model.ParseStatus
import com.oracle.ee.spentanalyser.domain.model.SmsLog
import com.oracle.ee.spentanalyser.domain.model.Transaction
import com.oracle.ee.spentanalyser.domain.repository.SmsLogRepository
import com.oracle.ee.spentanalyser.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class LogsViewModel(
    private val smsLogRepository: SmsLogRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val smsLogs: StateFlow<List<SmsLog>> =
        smsLogRepository.getAllSmsLogsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveManualTransaction(amount: Double, merchant: String, date: String, type: String, sourceHash: String) {
        viewModelScope.launch {
            try {
                transactionRepository.insertTransaction(
                    Transaction(
                        amount = amount,
                        merchant = merchant,
                        date = date,
                        type = type,
                        sourceSmsHash = sourceHash,
                        timestamp = System.currentTimeMillis()
                    )
                )
                smsLogRepository.updateSmsLogStatus(sourceHash, ParseStatus.MANUAL_REVIEW)
            } catch (e: Exception) {
                Timber.e(e, "Error saving manual transaction")
            }
        }
    }
}
