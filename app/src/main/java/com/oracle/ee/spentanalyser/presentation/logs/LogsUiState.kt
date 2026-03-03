package com.oracle.ee.spentanalyser.presentation.logs

import com.oracle.ee.spentanalyser.domain.model.SmsLog

data class LogsUiState(
    val smsLogs: List<SmsLog> = emptyList()
)
