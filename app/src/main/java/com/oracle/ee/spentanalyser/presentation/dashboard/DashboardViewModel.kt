package com.oracle.ee.spentanalyser.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.oracle.ee.spentanalyser.domain.engine.LlmInferenceEngine
import com.oracle.ee.spentanalyser.domain.model.Transaction
import com.oracle.ee.spentanalyser.domain.repository.ModelRepository
import com.oracle.ee.spentanalyser.domain.repository.TransactionRepository
import com.oracle.ee.spentanalyser.domain.usecase.ParseSmsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import kotlinx.coroutines.flow.first

/**
 * Dashboard ViewModel — focused on financial data and SMS parsing.
 * Model lifecycle (download, init, GPU toggle) is now owned by ModelsViewModel.
 * This VM only checks if the engine is ready and triggers parsing.
 */
class DashboardViewModel(
    private val transactionRepository: TransactionRepository,
    private val modelRepository: ModelRepository,
    private val llmEngine: LlmInferenceEngine,
    private val parseSmsUseCase: ParseSmsUseCase,
    private val workManager: WorkManager,
    private val preferencesManager: com.oracle.ee.spentanalyser.data.database.PreferencesManager,
    private val smsLogRepository: com.oracle.ee.spentanalyser.domain.repository.SmsLogRepository
) : ViewModel() {

    private var parsingJob: Job? = null


    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val currentCalendar = Calendar.getInstance()

    private val _selectedMonth = MutableStateFlow(currentCalendar.get(Calendar.MONTH))
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()

    private val _selectedYear = MutableStateFlow(currentCalendar.get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private val _filterPeriod = MutableStateFlow(FilterPeriod.MONTH)
    val filterPeriod: StateFlow<FilterPeriod> = _filterPeriod.asStateFlow()

    private val _customStartDateMillis = MutableStateFlow<Long?>(null)
    val customStartDateMillis: StateFlow<Long?> = _customStartDateMillis.asStateFlow()

    private val _customEndDateMillis = MutableStateFlow<Long?>(null)
    val customEndDateMillis: StateFlow<Long?> = _customEndDateMillis.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<Transaction>> = combine(
        _selectedMonth, _selectedYear, _filterPeriod, _customStartDateMillis, _customEndDateMillis
    ) { month, year, period, customStart, customEnd ->
        DashboardFilterState(month, year, period, customStart, customEnd)
    }.flatMapLatest { state ->
        val calendar = Calendar.getInstance()

        when (state.period) {
            FilterPeriod.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                transactionRepository.getTransactionsSinceFlow(calendar.timeInMillis)
            }
            FilterPeriod.YESTERDAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                val start = calendar.timeInMillis
                
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val end = calendar.timeInMillis
                
                transactionRepository.getTransactionsBetweenFlow(start, end)
            }
            FilterPeriod.LAST_7_DAYS -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                transactionRepository.getTransactionsSinceFlow(calendar.timeInMillis)
            }
            FilterPeriod.MONTH -> {
                calendar.set(Calendar.YEAR, state.year)
                calendar.set(Calendar.MONTH, state.month)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                val startTimestamp = calendar.timeInMillis

                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endTimestamp = calendar.timeInMillis

                transactionRepository.getTransactionsBetweenFlow(startTimestamp, endTimestamp)
            }
            FilterPeriod.CUSTOM -> {
                val start = state.customStart ?: 0L
                val end = state.customEnd ?: Long.MAX_VALUE
                transactionRepository.getTransactionsBetweenFlow(start, end)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSelectedMonth(month: Int) {
        _selectedMonth.value = month
        _filterPeriod.value = FilterPeriod.MONTH
    }

    fun updateSelectedYear(year: Int) {
        _selectedYear.value = year
        _filterPeriod.value = FilterPeriod.MONTH
    }

    fun updateFilterPeriod(period: FilterPeriod) {
        _filterPeriod.value = period
    }
    
    fun setCustomDateRange(startMillis: Long?, endMillis: Long?) {
        _customStartDateMillis.value = startMillis
        _customEndDateMillis.value = endMillis
        if (startMillis != null && endMillis != null) {
            _filterPeriod.value = FilterPeriod.CUSTOM
        }
    }

    init {
        viewModelScope.launch {
            modelRepository.getActiveModelFlow().collect { activeModel ->
                if (activeModel != null && activeModel.isDownloaded) {
                    if (!llmEngine.isInitialized()) {
                        modelRepository.autoInitializeEngine(llmEngine)
                    }
                    val isReady = llmEngine.isInitialized()
                    if (isReady) {
                        _uiState.update { it.copy(aiModelState = AiModelState.READY, error = null) }
                        if (parsingJob?.isActive != true) {
                            loadData()
                        }
                    } else {
                        _uiState.update { 
                            it.copy(
                                aiModelState = AiModelState.ERROR, 
                                error = "Failed to initialize AI engine even though a model was activated. Please try re-activating."
                            ) 
                        }
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            aiModelState = AiModelState.ERROR,
                            error = "No model active. Go to Models to download and activate one."
                        ) 
                    }
                }
            }
        }
        observeWorkManager()
    }

    private fun observeWorkManager() {
        viewModelScope.launch {
            val immediateFlow = workManager.getWorkInfosForUniqueWorkFlow("ImmediateSmsParse")
            val periodicFlow = workManager.getWorkInfosForUniqueWorkFlow("SmsParsingWorker")

            combine(immediateFlow, periodicFlow) { immediate, periodic ->
                val allInfos = immediate + periodic

                val workerState = when {
                    allInfos.any { it.state == WorkInfo.State.RUNNING } -> WorkInfo.State.RUNNING
                    allInfos.any { it.state == WorkInfo.State.ENQUEUED } -> WorkInfo.State.ENQUEUED
                    allInfos.any { it.state == WorkInfo.State.FAILED } -> WorkInfo.State.FAILED
                    else -> null
                }

                // Get next scheduled run time from periodic worker
                val nextRunTime = periodic
                    .filter { it.state == WorkInfo.State.ENQUEUED }
                    .mapNotNull { it.nextScheduleTimeMillis }
                    .filter { it > 0 }
                    .minOrNull()

                Pair(workerState, nextRunTime)
            }.collect { (combinedState, nextRunTime) ->
                _uiState.update {
                    it.copy(
                        backgroundWorkerState = combinedState,
                        nextScheduleTimeMillis = nextRunTime
                    )
                }
            }
        }
    }

    /**
     * Simply checks if the engine is already initialized (by Models screen).
     * No more download/init logic here — that lives in ModelsViewModel.
     */
    private fun checkEngineState() {
        if (llmEngine.isInitialized()) {
            _uiState.update { it.copy(aiModelState = AiModelState.READY) }
        } else {
            _uiState.update { it.copy(
                aiModelState = AiModelState.ERROR,
                error = "No model active. Go to Models to download and activate one."
            ) }
        }
    }

    fun loadData() {
        if (parsingJob?.isActive == true) return

        parsingJob = viewModelScope.launch {
            if (!llmEngine.isInitialized()) {
                _uiState.update {
                    it.copy(
                        aiModelState = AiModelState.ERROR,
                        error = "No model active. Go to Models to download and activate one."
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    aiModelState = AiModelState.READY,
                    parsingProcessed = 0,
                    parsingTotal = 0
                )
            }
            try {
                // Determine limits by checking historical flag
                val isInitialDone = preferencesManager.isInitialHistoryProcessedFlow.first()
                val parseLimit = if (isInitialDone) 10 else Int.MAX_VALUE
            
                parseSmsUseCase(limit = parseLimit) { processed, total ->
                    _uiState.update {
                        it.copy(parsingProcessed = processed, parsingTotal = total)
                    }
                }
                
                // If it successfully completes a full historical sweep, tag it as done.
                if (!isInitialDone) {
                    preferencesManager.setInitialHistoryProcessed(true)
                }
                
                _uiState.update {
                    it.copy(isLoading = false, parsingProcessed = 0, parsingTotal = 0)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading data")
                _uiState.update {
                    it.copy(isLoading = false, error = e.message, parsingProcessed = 0, parsingTotal = 0)
                }
            }
        }
    }

    fun cancelParsing() {
        parsingJob?.cancel()
        parsingJob = null
        _uiState.update {
            it.copy(
                isLoading = false,
                parsingProcessed = 0,
                parsingTotal = 0,
                aiModelState = AiModelState.READY
            )
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

data class DashboardFilterState(
    val month: Int,
    val year: Int,
    val period: FilterPeriod,
    val customStart: Long?,
    val customEnd: Long?
)
