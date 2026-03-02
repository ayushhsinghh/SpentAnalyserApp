package com.oracle.ee.spentanalyser.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oracle.ee.spentanalyser.data.LlmInferenceRepository
import com.oracle.ee.spentanalyser.data.SmsRepository
import com.oracle.ee.spentanalyser.data.TransactionData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

enum class AiModelState {
    CHECKING, DOWNLOADING, READY, ERROR
}

data class MainUiState(
    val isLoading: Boolean = false,
    val aiModelState: AiModelState = AiModelState.CHECKING,
    val downloadProgress: Int = 0,
    val transactions: List<TransactionData> = emptyList(),
    val error: String? = null
)

class MainViewModel(
    private val smsRepository: SmsRepository,
    private val llmInferenceRepository: LlmInferenceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

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
                // For now, it's scaffolded.
                val messages = smsRepository.readRecentBankSms()
                val parsedTransactions = mutableListOf<TransactionData>()
                
                for (msg in messages) {
                    val parsed = llmInferenceRepository.parseSms(msg.body)
                    if (parsed != null) {
                        parsedTransactions.add(parsed)
                    }
                }
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        transactions = parsedTransactions
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading data")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
