package com.oracle.ee.spentanalyser.presentation.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oracle.ee.spentanalyser.data.monitor.SystemMonitor
import com.oracle.ee.spentanalyser.domain.model.SystemStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MonitoringViewModel(
    private val systemMonitor: SystemMonitor
) : ViewModel() {

    val systemStatus: StateFlow<SystemStatus> = systemMonitor.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SystemStatus())

    init {
        systemMonitor.startPolling(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        systemMonitor.stopPolling()
    }
}
