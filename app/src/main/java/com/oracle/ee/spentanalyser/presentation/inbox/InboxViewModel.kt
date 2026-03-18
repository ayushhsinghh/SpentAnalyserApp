package com.oracle.ee.spentanalyser.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oracle.ee.spentanalyser.data.SmsMessage
import com.oracle.ee.spentanalyser.data.datasource.SmsInboxDataSource
import com.oracle.ee.spentanalyser.domain.model.ParseStatus
import com.oracle.ee.spentanalyser.domain.repository.SmsLogRepository
import com.oracle.ee.spentanalyser.domain.usecase.ParseSmsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class InboxSmsItem(
    val sms: SmsMessage,
    val status: ParseStatus?, // null if not present in DB
    val isParsing: Boolean = false
)

data class InboxState(
    val messages: List<InboxSmsItem> = emptyList(),
    val isLoading: Boolean = false,
    val customSenders: List<String> = emptyList(),
    val customKeywords: List<String> = emptyList(),
    val senderInput: String = "",
    val keywordInput: String = ""
)

class InboxViewModel(
    private val smsInboxDataSource: SmsInboxDataSource,
    private val smsLogRepository: SmsLogRepository,
    private val parseSmsUseCase: ParseSmsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(InboxState())
    val state: StateFlow<InboxState> = _state.asStateFlow()

    init {
        fetchInbox()
    }

    fun onSenderInputChanged(input: String) {
        _state.update { it.copy(senderInput = input) }
    }

    fun onKeywordInputChanged(input: String) {
        _state.update { it.copy(keywordInput = input) }
    }

    fun addCustomSender() {
        val input = _state.value.senderInput.trim()
        if (input.isNotEmpty() && !_state.value.customSenders.contains(input)) {
            _state.update { 
                it.copy(
                    customSenders = it.customSenders + input,
                    senderInput = ""
                ) 
            }
            fetchInbox()
        }
    }

    fun removeCustomSender(sender: String) {
        _state.update { 
            it.copy(customSenders = it.customSenders - sender) 
        }
        fetchInbox()
    }

    fun addCustomKeyword() {
        val input = _state.value.keywordInput.trim()
        if (input.isNotEmpty() && !_state.value.customKeywords.contains(input)) {
            _state.update { 
                it.copy(
                    customKeywords = it.customKeywords + input,
                    keywordInput = ""
                ) 
            }
            fetchInbox()
        }
    }

    fun removeCustomKeyword(keyword: String) {
        _state.update { 
            it.copy(customKeywords = it.customKeywords - keyword) 
        }
        fetchInbox()
    }

    fun fetchInbox(days: Int = 30) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            try {
                val currentSenders = _state.value.customSenders
                val currentKeywords = _state.value.customKeywords
                
                // Fetch raw SMS based on dynamic filters
                val rawMessages = smsInboxDataSource.readSmsForInbox(
                    days = days,
                    additionalSenders = currentSenders,
                    additionalKeywords = currentKeywords
                )
                
                // Cross-reference with DB
                val mappedItems = rawMessages.map { sms ->
                    val dbLog = smsLogRepository.getSmsLogByHash(sms.uniqueHash)
                    InboxSmsItem(
                        sms = sms,
                        status = dbLog?.status
                    )
                }
                
                _state.update { it.copy(messages = mappedItems, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Error fetching inbox")
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun parseMessage(hash: String) {
        val currentState = _state.value
        val itemToParse = currentState.messages.find { it.sms.uniqueHash == hash } ?: return
        
        // Mark as parsing purely for UI spinner
        _state.update { state -> 
            state.copy(
                messages = state.messages.map { 
                    if (it.sms.uniqueHash == hash) it.copy(isParsing = true) else it 
                }
            )
        }

        viewModelScope.launch {
            try {
                // Since this runs directly off a raw SMS object bypassing standard log creation flow,
                // we should let parseSmsUseCase handle injecting it into the pipeline if it doesn't exist.
                // But wait, the standard parseSingleSms signature is parseSingleSms(log: SmsLog).
                // Our SmsReceiver creates a SmsLog and calls parseSingleSms.
                
                // For simplicity, we can fetch the SMS again as an SmsLog. If it's not in DB, create it:
                var log = smsLogRepository.getSmsLogByHash(hash)
                if (log == null) {
                    val rawSms = itemToParse.sms
                    val newLog = com.oracle.ee.spentanalyser.domain.model.SmsLog(
                        uniqueHash = rawSms.uniqueHash,
                        sender = rawSms.sender,
                        body = rawSms.body,
                        timestamp = rawSms.timestamp,
                        status = ParseStatus.PENDING
                    )
                    smsLogRepository.insertSmsLog(newLog)
                    log = newLog
                } else {
                    smsLogRepository.updateSmsLogStatus(hash, ParseStatus.PENDING)
                }

                // Call the unified retry engine!
                parseSmsUseCase.parseSingleSms(log, "")
                
            } catch (e: Exception) {
                Timber.e(e, "Error parsing manual inbox SMS")
                // Handle fallback
                smsLogRepository.updateSmsLogStatus(hash, ParseStatus.ERROR)
            } finally {
                // Fetch inbox again to get the true database state
                fetchInbox()
            }
        }
    }
}
