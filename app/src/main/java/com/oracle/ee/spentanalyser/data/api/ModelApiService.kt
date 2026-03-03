package com.oracle.ee.spentanalyser.data.api

import com.oracle.ee.spentanalyser.domain.model.LlmModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple HTTP client for the model catalog API.
 * Uses HttpURLConnection to keep dependencies minimal (no Retrofit).
 */
class ModelApiService {

    companion object {
        private const val BASE_URL = "https://api.ayush.ltd"
        private const val MODELS_ENDPOINT = "$BASE_URL/api/models"
        const val API_KEY = "AYUSH_BOSS"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Fetches the list of available models from the API.
     * Returns domain [LlmModel] entities with resolved download URLs.
     */
    suspend fun fetchAvailableModels(): List<LlmModel> = withContext(Dispatchers.IO) {
        Timber.d("Fetching model catalog from %s", MODELS_ENDPOINT)

        val url = URL(MODELS_ENDPOINT)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-Api-Key", API_KEY)
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000

        try {
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("API returned HTTP ${connection.responseCode} ${connection.responseMessage}")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            Timber.d("API response: %s", responseBody)

            val apiModels = json.decodeFromString<List<ModelApiResponse>>(responseBody)

            apiModels.map { apiModel ->
                LlmModel(
                    id = apiModel.id,
                    name = apiModel.name,
                    fileName = apiModel.fileName,
                    // Resolve relative download URLs to absolute
                    downloadUrl = resolveDownloadUrl(apiModel.downloadUrl),
                    sizeBytes = apiModel.sizeBytes,
                    checksum = apiModel.checksum
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Resolves a potentially relative download URL to an absolute one.
     * e.g. "/api/models/download/abc123" → "https://api.ayush.ltd/api/models/download/abc123"
     */
    private fun resolveDownloadUrl(downloadUrl: String): String {
        return if (downloadUrl.startsWith("http")) {
            downloadUrl
        } else {
            "$BASE_URL$downloadUrl"
        }
    }
}
