package com.oracle.ee.spentanalyser.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oracle.ee.spentanalyser.domain.model.MonthlySpendItem
import com.oracle.ee.spentanalyser.domain.model.Transaction
import com.oracle.ee.spentanalyser.domain.model.TransactionFilterQuery
import com.oracle.ee.spentanalyser.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class DrillDownState(
    val entityType: EntityType = EntityType.MERCHANT,
    val entityName: String = "",
    val averageTicketSize: Float = 0f,
    val frequencyLast30Days: Int = 0,
    val monthlySpendTrend: List<MonthlySpendItem> = emptyList(),
    val totalTransactionsAmount: Double = 0.0,
    val isLoading: Boolean = true
)

enum class EntityType {
    MERCHANT, CATEGORY
}

class DrillDownViewModel(
    private val transactionRepository: TransactionRepository,
    private val smsLogRepository: com.oracle.ee.spentanalyser.domain.repository.SmsLogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DrillDownState())
    val state: StateFlow<DrillDownState> = _state

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions

    fun init(entityType: EntityType, entityName: String) {
        _state.value = _state.value.copy(
            entityType = entityType, 
            entityName = entityName,
            isLoading = true
        )
        loadAnalytics(entityType, entityName)
        loadTransactions(entityType, entityName)
    }

    private fun loadAnalytics(type: EntityType, name: String) {
        viewModelScope.launch {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            
            if (type == EntityType.MERCHANT) {
                val avg = transactionRepository.getAverageTicketSizeForMerchant(name)
                val freq = transactionRepository.getFrequencyForMerchant(name, thirtyDaysAgo)
                val trend = transactionRepository.getMonthlySpendTrendForMerchant(name)
                
                _state.value = _state.value.copy(
                    averageTicketSize = avg,
                    frequencyLast30Days = freq,
                    monthlySpendTrend = trend,
                    totalTransactionsAmount = trend.sumOf { it.totalAmount },
                    isLoading = false
                )
            } else {
                val avg = transactionRepository.getAverageTicketSizeForCategory(name)
                val freq = transactionRepository.getFrequencyForCategory(name, thirtyDaysAgo)
                val trend = transactionRepository.getMonthlySpendTrendForCategory(name)
                
                _state.value = _state.value.copy(
                    averageTicketSize = avg,
                    frequencyLast30Days = freq,
                    monthlySpendTrend = trend,
                    totalTransactionsAmount = trend.sumOf { it.totalAmount },
                    isLoading = false
                )
            }
        }
    }

    private fun loadTransactions(type: EntityType, name: String) {
        viewModelScope.launch {
            val query = if (type == EntityType.MERCHANT) {
                TransactionFilterQuery(merchant = name, type = "DEBIT")
            } else {
                TransactionFilterQuery(category = name, type = "DEBIT")
            }
            
            transactionRepository.searchTransactions(query).collect { txs ->
                _transactions.value = txs
            }
        }
    }

    fun updateExistingTransaction(transaction: Transaction, originalMerchant: String? = null) {
        viewModelScope.launch {
            try {
                if (originalMerchant != null && originalMerchant != transaction.merchant) {
                    transactionRepository.saveMerchantMapping(
                        alias = originalMerchant,
                        normalizedName = transaction.merchant
                    )
                }
                val normalizedTx = transaction.copy(merchant = transactionRepository.getNormalizedMerchantOrDefault(transaction.merchant))
                transactionRepository.updateTransaction(normalizedTx)
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
