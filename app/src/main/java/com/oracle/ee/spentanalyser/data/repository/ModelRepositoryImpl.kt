package com.oracle.ee.spentanalyser.data.repository

import android.content.Context
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

    override suspend fun downloadModel(model: LlmModel, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        if (isModelDownloaded(model)) {
            onProgress(100)
            return@withContext
        }

        Timber.d("Starting model download: %s from %s", model.name, model.downloadUrl)
        val url = java.net.URL(model.downloadUrl)
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.setRequestProperty("X-Api-Key", ModelApiService.API_KEY)
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.connect()

        if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
            throw Exception("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
        }

        val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: model.sizeBytes
        val finalFile = File(context.filesDir, model.fileName)
        val tempFile = File(context.filesDir, "${model.fileName}.tmp")

        try {
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var totalBytes: Long = 0
                    var bytesRead: Int
                    var lastProgress = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        totalBytes += bytesRead
                        if (contentLength > 0) {
                            val progress = (totalBytes * 100 / contentLength).toInt().coerceAtMost(100)
                            if (progress != lastProgress) {
                                onProgress(progress)
                                lastProgress = progress
                            }
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (!tempFile.renameTo(finalFile)) {
                    throw Exception("Failed to rename temporary model file.")
                }
                Timber.d("Download complete: %s (%d bytes)", model.name, finalFile.length())
                _refreshTrigger.update { it + 1 }
            } else {
                throw Exception("Downloaded model file is empty or missing.")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error downloading model: %s", model.name)
            if (tempFile.exists()) tempFile.delete()
            throw e
        }
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
            preferencesManager.setActiveModel(modelId, model.fileName)
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
                    engine.initialize(file.absolutePath, useGpu)
                    Timber.d("Auto-initialized engine with model: %s", fileName)
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
            _refreshTrigger
        ) { models, activeId, _ ->
            models.find { it.id == activeId && isModelDownloaded(it) }
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
}
