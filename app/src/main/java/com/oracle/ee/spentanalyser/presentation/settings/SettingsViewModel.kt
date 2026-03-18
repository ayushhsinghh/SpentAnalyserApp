package com.oracle.ee.spentanalyser.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oracle.ee.spentanalyser.data.database.PreferencesManager
import com.oracle.ee.spentanalyser.domain.repository.SmsLogRepository
import com.oracle.ee.spentanalyser.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val transactionRepository: TransactionRepository,
    private val smsLogRepository: SmsLogRepository
) : ViewModel() {

    val isAutoParsingEnabled: StateFlow<Boolean> = preferencesManager.isAutoParsingEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAutoParsingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoParsingEnabled(enabled)
        }
    }

    fun clearDatabase(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                Timber.w("Wiping all transactions and SMS logs from the database.")
                transactionRepository.clearAllTransactions()
                smsLogRepository.clearAllSmsLogs()
                
                // Also reset the last processed timestamp so the app can start fresh if they want
                preferencesManager.updateLastProcessedTimestamp(0L)
                preferencesManager.setInitialHistoryProcessed(false)
                
                onSuccess()
            } catch (e: Exception) {
                Timber.e(e, "Error clearing databases")
            }
        }
    }
}
