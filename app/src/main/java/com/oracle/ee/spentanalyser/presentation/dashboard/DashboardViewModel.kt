package com.oracle.ee.spentanalyser.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.oracle.ee.spentanalyser.domain.engine.LlmInferenceEngine
import com.oracle.ee.spentanalyser.domain.model.Transaction
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
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar

/**
 * Dashboard ViewModel — focused on financial data and SMS parsing.
 * Model lifecycle (download, init, GPU toggle) is now owned by ModelsViewModel.
 * This VM only checks if the engine is ready and triggers parsing.
 */
class DashboardViewModel(
    private val transactionRepository: TransactionRepository,
    private val llmEngine: LlmInferenceEngine,
    private val parseSmsUseCase: ParseSmsUseCase,
    private val workManager: WorkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val currentCalendar = Calendar.getInstance()

    private val _selectedMonth = MutableStateFlow(currentCalendar.get(Calendar.MONTH))
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()

    private val _selectedYear = MutableStateFlow(currentCalendar.get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private val _filterPeriod = MutableStateFlow(FilterPeriod.MONTH)
    val filterPeriod: StateFlow<FilterPeriod> = _filterPeriod.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<Transaction>> = combine(
        _selectedMonth, _selectedYear, _filterPeriod
    ) { month, year, period ->
        Triple(month, year, period)
    }.flatMapLatest { (month, year, period) ->
        val calendar = Calendar.getInstance()

        when (period) {
            FilterPeriod.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                transactionRepository.getTransactionsSinceFlow(calendar.timeInMillis)
            }
            FilterPeriod.LAST_7_DAYS -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                transactionRepository.getTransactionsSinceFlow(calendar.timeInMillis)
            }
            FilterPeriod.MONTH -> {
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
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

    init {
        checkEngineState()
        observeWorkManager()
    }

    private fun observeWorkManager() {
        viewModelScope.launch {
            val immediateFlow = workManager.getWorkInfosForUniqueWorkFlow("ImmediateSmsParse")
            val periodicFlow = workManager.getWorkInfosForUniqueWorkFlow("SmsParsingWorker")

            combine(immediateFlow, periodicFlow) { immediate, periodic ->
                val allInfos = immediate + periodic
                when {
                    allInfos.any { it.state == androidx.work.WorkInfo.State.RUNNING } -> androidx.work.WorkInfo.State.RUNNING
                    allInfos.any { it.state == androidx.work.WorkInfo.State.ENQUEUED } -> androidx.work.WorkInfo.State.ENQUEUED
                    allInfos.any { it.state == androidx.work.WorkInfo.State.FAILED } -> androidx.work.WorkInfo.State.FAILED
                    else -> null
                }
            }.collect { combinedState ->
                _uiState.update { it.copy(backgroundWorkerState = combinedState) }
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
            _uiState.update { it.copy(aiModelState = AiModelState.CHECKING) }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            if (!llmEngine.isInitialized()) {
                _uiState.update {
                    it.copy(
                        aiModelState = AiModelState.ERROR,
                        error = "No model active. Go to Models to download and activate one."
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null, aiModelState = AiModelState.READY) }
            try {
                parseSmsUseCase(limit = 5)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Error loading data")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
