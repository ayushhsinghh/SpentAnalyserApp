package com.oracle.ee.spentanalyser.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oracle.ee.spentanalyser.domain.model.Transaction
import com.oracle.ee.spentanalyser.domain.model.TransactionFilterQuery
import com.oracle.ee.spentanalyser.domain.repository.TransactionRepository
import com.oracle.ee.spentanalyser.domain.util.MerchantNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val smsLogRepository: com.oracle.ee.spentanalyser.domain.repository.SmsLogRepository
) : ViewModel() {

    private val _filterQuery = MutableStateFlow(TransactionFilterQuery())
    val filterQuery: StateFlow<TransactionFilterQuery> = _filterQuery

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<Transaction>> = _filterQuery
        .flatMapLatest { query ->
            transactionRepository.searchTransactions(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateFilterQuery(query: TransactionFilterQuery) {
        _filterQuery.value = query
    }

    fun updateExistingTransaction(transaction: Transaction, originalMerchant: String? = null) {
        viewModelScope.launch {
            try {
                // If the user changed the merchant string via manual edit, save it as an alias mapping!
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
