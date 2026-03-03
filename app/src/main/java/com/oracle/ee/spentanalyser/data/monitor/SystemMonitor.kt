package com.oracle.ee.spentanalyser.data.monitor

import com.oracle.ee.spentanalyser.domain.engine.LlmInferenceEngine
import com.oracle.ee.spentanalyser.domain.model.SystemStatus
import com.oracle.ee.spentanalyser.domain.repository.ModelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Periodically polls system resources and tracks inference state.
 * Provides an observable [SystemStatus] flow for the Monitoring screen.
 */
class SystemMonitor(
    private val llmEngine: LlmInferenceEngine,
    private val modelRepository: ModelRepository
) {
    private val _status = MutableStateFlow(SystemStatus())
    val status: StateFlow<SystemStatus> = _status.asStateFlow()

    private var pollingJob: Job? = null

    private var _totalInferences = 0
    private var _successfulInferences = 0
    private val _errors = mutableListOf<String>()

    fun startPolling(scope: CoroutineScope) {
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val runtime = Runtime.getRuntime()
                val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val maxMb = runtime.maxMemory() / (1024 * 1024)

                val activeModel = modelRepository.getActiveModelFlow().first()
                val useGpu = modelRepository.getUseGpuFlow().first()

                _status.update { current ->
                    current.copy(
                        memoryUsageMb = usedMb,
                        maxMemoryMb = maxMb,
                        activeModelName = activeModel?.name,
                        hardwareBackend = if (useGpu) "GPU" else "CPU",
                        activeErrors = _errors.takeLast(5),
                        totalInferences = _totalInferences,
                        successfulInferences = _successfulInferences
                    )
                }
                delay(2000) // Poll every 2 seconds
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun setCurrentTask(task: String) {
        _status.update { it.copy(currentTask = task) }
    }

    fun recordInference(success: Boolean, error: String? = null) {
        _totalInferences++
        if (success) _successfulInferences++
        if (error != null) _errors.add(error)
    }
}
