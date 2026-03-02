package com.oracle.ee.spentanalyser.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oracle.ee.spentanalyser.data.LlmInferenceRepository
import com.oracle.ee.spentanalyser.data.SmsRepository
import com.oracle.ee.spentanalyser.data.database.AppDao
import com.oracle.ee.spentanalyser.data.database.ParseStatus
import com.oracle.ee.spentanalyser.data.database.SmsLogEntity
import com.oracle.ee.spentanalyser.data.database.TransactionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest

enum class AiModelState {
    CHECKING, DOWNLOADING, READY, ERROR
}

data class MainUiState(
    val isLoading: Boolean = false,
    val aiModelState: AiModelState = AiModelState.CHECKING,
    val downloadProgress: Int = 0,
    val error: String? = null
)

class MainViewModel(
    private val appDao: AppDao,
    private val smsRepository: SmsRepository,
    private val llmInferenceRepository: LlmInferenceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val currentCalendar = Calendar.getInstance()
    
    // Default to the current month & year
    private val _selectedMonth = MutableStateFlow(currentCalendar.get(Calendar.MONTH))
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()

    private val _selectedYear = MutableStateFlow(currentCalendar.get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<TransactionEntity>> = kotlinx.coroutines.flow.combine(
        _selectedMonth, _selectedYear
    ) { month, year ->
        Pair(month, year)
    }.flatMapLatest { (month, year) ->
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTimestamp = calendar.timeInMillis
        
        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        val endTimestamp = calendar.timeInMillis

        appDao.getTransactionsBetweenFlow(startTimestamp, endTimestamp)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val smsLogs = appDao.getAllSmsLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSelectedMonth(month: Int) {
        _selectedMonth.value = month
    }

    fun updateSelectedYear(year: Int) {
        _selectedYear.value = year
    }

    init {
        checkAndInitializeModel()
    }

    private fun checkAndInitializeModel() {
        viewModelScope.launch {
            try {
                if (!llmInferenceRepository.isModelAvailable()) {
                    _uiState.update { it.copy(aiModelState = AiModelState.DOWNLOADING, downloadProgress = 0) }
                    llmInferenceRepository.downloadModel { progress ->
                        _uiState.update { it.copy(downloadProgress = progress) }
                    }
                }
                
                llmInferenceRepository.initializeModel()
                _uiState.update { it.copy(aiModelState = AiModelState.READY) }
                
                // Once ready, load initial data
                loadData()
            } catch (e: Exception) {
                Timber.e(e, "Error downloading or initializing AI model")
                _uiState.update { 
                    it.copy(
                        aiModelState = AiModelState.ERROR,
                        error = "Failed to load AI Engine: ${e.message}"
                    )
                }
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val messages = smsRepository.readRecentBankSms(limit = 5)
                
                for (msg in messages) {
                    val attempt = llmInferenceRepository.parseSms(msg.body)
                    
                    if (attempt.success && attempt.data != null) {
                        appDao.insertTransaction(
                            TransactionEntity(
                                amount = attempt.data.amount,
                                merchant = attempt.data.merchant,
                                date = attempt.data.date,
                                type = attempt.data.type,
                                sourceSmsHash = msg.uniqueHash
                            )
                        )
                        appDao.insertSmsLog(SmsLogEntity(msg.uniqueHash, msg.sender, msg.body, msg.timestamp, ParseStatus.SUCCESS.name, rawLlmOutput = attempt.rawOutput))
                    } else {
                        appDao.insertSmsLog(SmsLogEntity(msg.uniqueHash, msg.sender, msg.body, msg.timestamp, ParseStatus.ERROR.name, errorMessage = attempt.error, rawLlmOutput = attempt.rawOutput))
                    }
                }
                
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Error loading data")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
