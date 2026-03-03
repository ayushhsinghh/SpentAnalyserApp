package com.oracle.ee.spentanalyser.domain.repository

import com.oracle.ee.spentanalyser.domain.model.LlmModel
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    /** Observable list of all known models with their current download/active state. */
    fun getAvailableModelsFlow(): Flow<List<LlmModel>>

    /** Download a model file, reporting progress (0–100). */
    suspend fun downloadModel(model: LlmModel, onProgress: (Int) -> Unit)

    /** Delete a downloaded model file from disk. */
    suspend fun deleteModel(model: LlmModel)

    /** Set a model as the active model for inference. */
    suspend fun setActiveModel(modelId: String)

    /** Observable active model (null if none selected or downloaded). */
    fun getActiveModelFlow(): Flow<LlmModel?>

    /** Observable GPU preference. */
    fun getUseGpuFlow(): Flow<Boolean>

    /** Persist the GPU preference. */
    suspend fun setUseGpu(useGpu: Boolean)

    /** Get the absolute file path for a downloaded model, or null. */
    fun getModelFilePath(model: LlmModel): String?

    /** Automatically initialize the provided engine with the saved active model and GPU settings (if any exist). */
    suspend fun autoInitializeEngine(engine: com.oracle.ee.spentanalyser.domain.engine.LlmInferenceEngine)
}
