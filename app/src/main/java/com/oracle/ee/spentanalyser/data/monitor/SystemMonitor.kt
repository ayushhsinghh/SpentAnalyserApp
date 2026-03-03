package com.oracle.ee.spentanalyser.data.monitor

import android.content.Context
import com.oracle.ee.spentanalyser.domain.engine.LlmInferenceEngine
import com.oracle.ee.spentanalyser.domain.model.SystemStatus
import com.oracle.ee.spentanalyser.domain.repository.ModelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update

/**
 * Periodically polls system resources and tracks inference state.
 * Provides an observable [SystemStatus] flow for the Monitoring screen.
 */
class SystemMonitor(
    private val context: Context,
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
        
        val tickerFlow = flow {
            while (true) {
                emit(Unit)
                delay(2000)
            }
        }

        pollingJob = combine(
            tickerFlow,
            modelRepository.getActiveModelFlow(),
            modelRepository.getUseGpuFlow()
        ) { _, activeModel, useGpu ->
            
            // 1. Get True Native Process Memory (Total PSS)
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfoArray = activityManager.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
            val totalPssMb = if (memoryInfoArray.isNotEmpty()) {
                memoryInfoArray[0].totalPss.toLong() / 1024L
            } else {
                0L
            }
            
            // 2. Get JVM limit just for UI max bounds
            val maxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)

            // 3. Get Thermal Headroom Data (Android 12+)
            var headroomNanos: Long = 0L
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val perfHintManager = context.getSystemService(Context.PERFORMANCE_HINT_SERVICE) as? android.os.PerformanceHintManager
                headroomNanos = perfHintManager?.preferredUpdateRateNanos ?: 0L
            }

            // 4. Update state emission natively
            _status.update { current ->
                current.copy(
                    memoryUsageMb = totalPssMb,
                    maxMemoryMb = maxMb,
                    thermalHeadroomNanos = headroomNanos,
                    activeModelName = activeModel?.name,
                    hardwareBackend = if (useGpu) "GPU" else "CPU",
                    activeErrors = _errors.takeLast(5),
                    totalInferences = _totalInferences,
                    successfulInferences = _successfulInferences
                )
            }
        }.launchIn(scope)
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
