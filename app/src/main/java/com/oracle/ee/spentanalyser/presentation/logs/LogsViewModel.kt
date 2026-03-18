package com.oracle.ee.spentanalyser.presentation.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oracle.ee.spentanalyser.domain.model.ParseStatus
import com.oracle.ee.spentanalyser.domain.model.SmsLog
import com.oracle.ee.spentanalyser.domain.model.Transaction
import com.oracle.ee.spentanalyser.domain.repository.SmsLogRepository
import com.oracle.ee.spentanalyser.domain.repository.TransactionRepository
import com.oracle.ee.spentanalyser.domain.usecase.ParseSmsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class LogsViewModel(
    private val smsLogRepository: SmsLogRepository,
    private val transactionRepository: TransactionRepository,
    private val parseSmsUseCase: ParseSmsUseCase
) : ViewModel() {

    private val _isRetrying = MutableStateFlow<String?>(null) // hash of log being retried
    val isRetrying: StateFlow<String?> = _isRetrying.asStateFlow()

    val smsLogs: StateFlow<List<SmsLog>> =
        smsLogRepository.getAllSmsLogsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveManualTransaction(amount: Double, merchant: String, category: String, date: String, type: String, sourceHash: String) {
        viewModelScope.launch {
            try {
                val finalMerchant = transactionRepository.getNormalizedMerchantOrDefault(merchant)
                transactionRepository.insertTransaction(
                    Transaction(
                        amount = amount,
                        merchant = finalMerchant,
                        category = category,
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

    fun retryParsing(log: SmsLog, promptHint: String) {
        if (_isRetrying.value != null) return // One at a time
        
        viewModelScope.launch {
            _isRetrying.value = log.uniqueHash
            
            try {
                // Update log status to pending strictly to show UI response
                smsLogRepository.updateSmsLogStatus(log.uniqueHash, ParseStatus.PENDING)
                
                parseSmsUseCase.parseSingleSms(log, promptHint)
                
            } catch (e: Exception) {
                Timber.e(e, "Error retrying parse")
                smsLogRepository.updateSmsLogStatus(log.uniqueHash, ParseStatus.ERROR)
            } finally {
                _isRetrying.value = null
            }
        }
    }
}
