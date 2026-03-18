package com.oracle.ee.spentanalyser.data.repository

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.oracle.ee.spentanalyser.data.api.ModelApiService
import com.oracle.ee.spentanalyser.data.database.PreferencesManager
import com.oracle.ee.spentanalyser.domain.model.LlmModel
import com.oracle.ee.spentanalyser.domain.repository.ModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Manages the model catalog (fetched from API), file downloads, active selection, and GPU preference.
 */
class ModelRepositoryImpl(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val modelApiService: ModelApiService
) : ModelRepository {

    // Cached model catalog fetched from API
    private val _cachedModels = MutableStateFlow<List<LlmModel>>(emptyList())

    // Mutable trigger to force refresh of download state
    private val _refreshTrigger = MutableStateFlow(0)

    override fun getAvailableModelsFlow(): Flow<List<LlmModel>> {
        return combine(
            _cachedModels,
            preferencesManager.activeModelIdFlow,
            _refreshTrigger
        ) { models, activeId, _ ->
            models.map { model ->
                model.copy(
                    isDownloaded = isModelDownloaded(model),
                    isActive = model.id == activeId
                )
            }
        }
    }

    /**
     * Fetches the model catalog from the remote API and updates the cached list.
     * Call this from the ViewModel to populate the Models screen.
     */
    suspend fun refreshModelCatalog() = withContext(Dispatchers.IO) {
        try {
            val models = modelApiService.fetchAvailableModels()
            _cachedModels.value = models
            Timber.d("Refreshed model catalog: %d models available", models.size)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch model catalog from API")
            throw e
        }
    }

    override fun enqueueDownloadWork(model: LlmModel) {
        if (isModelDownloaded(model)) {
            Timber.d("Model %s is already downloaded.", model.name)
            return
        }

        val inputData = Data.Builder()
            .putString(com.oracle.ee.spentanalyser.data.worker.ModelDownloadWorker.KEY_URL, model.downloadUrl)
            .putString(com.oracle.ee.spentanalyser.data.worker.ModelDownloadWorker.KEY_FILE_NAME, model.fileName)
            .build()

        val workerRequest = OneTimeWorkRequestBuilder<com.oracle.ee.spentanalyser.data.worker.ModelDownloadWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(inputData)
            .addTag("MODEL_DOWNLOAD_${model.id}")
            .build()
            
        WorkManager.getInstance(context).enqueueUniqueWork(
            "DOWNLOAD_${model.id}",
            ExistingWorkPolicy.KEEP,
            workerRequest
        )
    }

    override suspend fun deleteModel(model: LlmModel) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, model.fileName)
        if (file.exists()) {
            file.delete()
            Timber.d("Deleted model: %s", model.name)
        }
        _refreshTrigger.update { it + 1 }
    }

    override suspend fun setActiveModel(modelId: String) {
        val model = _cachedModels.value.find { it.id == modelId }
        if (model != null) {
            preferencesManager.setActiveModel(modelId, model.fileName, model.name)
        } else {
            Timber.w("Model ID %s not found in cache. Cannot set as active and save filename.", modelId)
        }
    }

    override suspend fun autoInitializeEngine(engine: com.oracle.ee.spentanalyser.domain.engine.LlmInferenceEngine) {
        if (engine.isInitialized()) return

        val fileName = preferencesManager.activeModelFileNameFlow.firstOrNull()
        val useGpu = preferencesManager.useGpuFlow.first()

        if (!fileName.isNullOrEmpty()) {
            val file = File(context.filesDir, fileName)
            if (file.exists() && file.length() > 0) {
                try {
                    val actualBackend = engine.initialize(file.absolutePath, useGpu)
                    if (useGpu && actualBackend == com.oracle.ee.spentanalyser.domain.engine.LlmInferenceEngine.Backend.CPU) {
                        preferencesManager.setUseGpu(false)
                        Timber.w("Auto-initialize forced CPU fallback. Synchronizing preference down to false.")
                    }
                    Timber.d("Auto-initialized engine with model: %s (Backend: %s)", fileName, actualBackend.name)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to auto-initialize engine on startup")
                }
            } else {
                Timber.w("Saved active model file not found: %s", fileName)
            }
        }
    }

    override fun getActiveModelFlow(): Flow<LlmModel?> {
        return combine(
            _cachedModels,
            preferencesManager.activeModelIdFlow,
            preferencesManager.activeModelNameFlow,
            preferencesManager.activeModelFileNameFlow,
            _refreshTrigger
        ) { models, activeId, activeName, activeFileName, _ ->
            if (activeId == null) return@combine null

            // Try to find it in the remote catalog first
            val catalogModel = models.find { it.id == activeId && isModelDownloaded(it) }
            if (catalogModel != null) {
                return@combine catalogModel.copy(isDownloaded = true, isActive = true)
            }

            // If catalog is empty (app just started) but we have the saved preferences, synthesize a local object!
            if (activeName != null && activeFileName != null) {
                val synthesizedModel = LlmModel(
                    id = activeId,
                    name = activeName,
                    fileName = activeFileName,
                    downloadUrl = "",
                    sizeBytes = 0L,
                    isDownloaded = true,
                    isActive = true
                )
                if (isModelDownloaded(synthesizedModel)) {
                    return@combine synthesizedModel
                }
            }
            null
        }
    }

    override fun getUseGpuFlow(): Flow<Boolean> = preferencesManager.useGpuFlow

    override suspend fun setUseGpu(useGpu: Boolean) {
        preferencesManager.setUseGpu(useGpu)
    }

    override fun getModelFilePath(model: LlmModel): String? {
        val file = File(context.filesDir, model.fileName)
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    private fun isModelDownloaded(model: LlmModel): Boolean {
        val file = File(context.filesDir, model.fileName)
        return file.exists() && file.length() > 0
    }

    override fun forceRefreshState() {
        _refreshTrigger.update { it + 1 }
    }
}
