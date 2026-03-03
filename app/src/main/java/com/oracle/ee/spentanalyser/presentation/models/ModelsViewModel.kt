package com.oracle.ee.spentanalyser.presentation.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oracle.ee.spentanalyser.data.repository.ModelRepositoryImpl
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
    private val llmEngine: LlmInferenceEngine
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
                modelRepository.downloadModel(model) { progress ->
                    _localState.update { it.copy(downloadProgress = progress) }
                }
                _localState.update { it.copy(downloadingModelId = null, downloadProgress = 100) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to download model: %s", model.name)
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
                llmEngine.initialize(filePath, useGpu)

                _localState.update { it.copy(initializingModelId = null) }
                Timber.d("Model activated: %s (GPU=%s)", model.name, useGpu)
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

