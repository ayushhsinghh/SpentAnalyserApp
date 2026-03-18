package com.oracle.ee.spentanalyser.presentation.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import com.oracle.ee.spentanalyser.data.repository.ModelRepositoryImpl
import com.oracle.ee.spentanalyser.data.worker.ModelDownloadWorker
import com.oracle.ee.spentanalyser.domain.engine.LlmInferenceEngine
import com.oracle.ee.spentanalyser.domain.model.LlmModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class ModelsViewModel(
    private val modelRepository: ModelRepositoryImpl,
    private val llmEngine: LlmInferenceEngine,
    private val workManager: androidx.work.WorkManager
) : ViewModel() {

    private val _localState = MutableStateFlow(ModelsUiState())

    val uiState: StateFlow<ModelsUiState> = combine(
        modelRepository.getAvailableModelsFlow(),
        modelRepository.getUseGpuFlow(),
        _localState
    ) { models, useGpu, local ->
        local.copy(
            models = models,
            useGpu = useGpu
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelsUiState())

    init {
        refreshModels()
    }

    /** Fetch the model catalog from the remote API. */
    fun refreshModels() {
        viewModelScope.launch {
            _localState.update { it.copy(isLoadingModels = true, error = null) }
            try {
                modelRepository.refreshModelCatalog()
                _localState.update { it.copy(isLoadingModels = false) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh model catalog")
                _localState.update {
                    it.copy(isLoadingModels = false, error = "Failed to load models: ${e.message}")
                }
            }
        }
    }

    fun downloadModel(model: LlmModel) {
        viewModelScope.launch {
            _localState.update { it.copy(downloadingModelId = model.id, downloadProgress = 0, error = null) }
            try {
                modelRepository.enqueueDownloadWork(model)
                
                workManager.getWorkInfosForUniqueWorkLiveData("DOWNLOAD_${model.id}").asFlow().collect { workInfos ->
                    val workInfo = workInfos.firstOrNull() ?: return@collect
                    
                    when (workInfo.state) {
                        WorkInfo.State.ENQUEUED -> {
                            _localState.update { it.copy(downloadingModelId = model.id, downloadProgress = 0) }
                        }
                        WorkInfo.State.RUNNING -> {
                            val progress = workInfo.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0)
                            _localState.update { it.copy(downloadingModelId = model.id, downloadProgress = progress) }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            _localState.update { it.copy(downloadingModelId = null, downloadProgress = 100) }
                            modelRepository.forceRefreshState()
                            modelRepository.refreshModelCatalog()
                        }
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            _localState.update {
                                it.copy(downloadingModelId = null, error = "Download failed or was cancelled.")
                            }
                        }
                        else -> { /* Ignore other states like BLOCKED */ }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to enqueue model download: %s", model.name)
                _localState.update {
                    it.copy(downloadingModelId = null, error = "Download failed: ${e.message}")
                }
            }
        }
    }

    fun deleteModel(model: LlmModel) {
        viewModelScope.launch {
            try {
                if (model.isActive) {
                    llmEngine.release()
                }
                modelRepository.deleteModel(model)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete model: %s", model.name)
                _localState.update { it.copy(error = "Delete failed: ${e.message}") }
            }
        }
    }

    fun activateModel(model: LlmModel) {
        viewModelScope.launch {
            _localState.update { it.copy(initializingModelId = model.id, error = null) }
            try {
                val filePath = modelRepository.getModelFilePath(model)
                    ?: throw IllegalStateException("Model file not found on disk.")

                val useGpu = uiState.value.useGpu
                modelRepository.setActiveModel(model.id)
                val actualBackend = llmEngine.initialize(filePath, useGpu)
                
                // If we asked for GPU but got forced to CPU fallback, sync the UI state true -> false
                if (useGpu && actualBackend == LlmInferenceEngine.Backend.CPU) {
                    modelRepository.setUseGpu(false)
                    Timber.w("Model Initialization forced fallback to CPU. Synced preference to false.")
                }

                _localState.update { it.copy(initializingModelId = null) }
                Timber.d("Model activated: %s (Backend=%s)", model.name, actualBackend.name)
                
                // Immediately trigger background SMS parsing to catch up on unparsed messages
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.oracle.ee.spentanalyser.worker.SmsParsingWorker>()
                    .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                workManager.enqueueUniqueWork(
                    "ImmediateSmsParse",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    workRequest
                )
                Timber.d("Enqueued immediate SMS parsing worker after model activation.")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to activate model: %s", model.name)
                _localState.update {
                    it.copy(initializingModelId = null, error = "Activation failed: ${e.message}")
                }
            }
        }
    }

    fun toggleGpu(useGpu: Boolean) {
        viewModelScope.launch {
            modelRepository.setUseGpu(useGpu)
            val activeModel = uiState.value.models.find { it.isActive }
            if (activeModel != null && llmEngine.isInitialized()) {
                activateModel(activeModel)
            }
        }
    }

    fun dismissError() {
        _localState.update { it.copy(error = null) }
    }
}

