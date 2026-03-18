package com.oracle.ee.spentanalyser.presentation.dashboard

import androidx.work.WorkInfo

enum class AiModelState {
    CHECKING, READY, ERROR
}

enum class FilterPeriod {
    TODAY, YESTERDAY, LAST_7_DAYS, MONTH, CUSTOM
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val aiModelState: AiModelState = AiModelState.CHECKING,
    val error: String? = null,
    val backgroundWorkerState: WorkInfo.State? = null,
    val nextScheduleTimeMillis: Long? = null,
    // SMS parsing progress
    val parsingTotal: Int = 0,
    val parsingProcessed: Int = 0
)
