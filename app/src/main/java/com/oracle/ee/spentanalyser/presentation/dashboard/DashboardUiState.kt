package com.oracle.ee.spentanalyser.presentation.dashboard

import androidx.work.WorkInfo

enum class AiModelState {
    CHECKING, READY, ERROR
}

enum class FilterPeriod {
    TODAY, LAST_7_DAYS, MONTH
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val aiModelState: AiModelState = AiModelState.CHECKING,
    val error: String? = null,
    val backgroundWorkerState: WorkInfo.State? = null
)
