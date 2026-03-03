package com.oracle.ee.spentanalyser.presentation.models

import com.oracle.ee.spentanalyser.domain.model.LlmModel

data class ModelsUiState(
    val models: List<LlmModel> = emptyList(),
    val useGpu: Boolean = true,
    val isLoadingModels: Boolean = false,
    val downloadingModelId: String? = null,
    val downloadProgress: Int = 0,
    val initializingModelId: String? = null,
    val error: String? = null
)
