package com.oracle.ee.spentanalyser.data

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TransactionData(
    val amount: Double,
    val merchant: String,
    val date: String,
    val type: String // E.g: "DEBIT", "CREDIT"
)

class LlmInferenceRepository(private val context: Context) {

    private var llmInference: LlmInference? = null
    
    // Using Gemma Instruct endpoint as a placeholder (replace with actual .bin url you want)
    // NOTE: In production, the URL shouldn't be hardcoded and it's 1-2GBs in size.
    private val modelUrl = "http://localhost:8000/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"
    private val modelFileName = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"

    fun isModelAvailable(): Boolean {
        val file = File(context.filesDir, modelFileName)
        return file.exists() && file.length() > 0
    }

    suspend fun downloadModel(onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        if (isModelAvailable()) return@withContext

        Timber.d("Starting model download from %s", modelUrl)
        val url = java.net.URL(modelUrl)
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.connect()

        if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
            throw Exception("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
        }

        val fileLength = connection.contentLength
        val modelFile = File(context.filesDir, modelFileName)

        connection.inputStream.use { input ->
            modelFile.outputStream().use { output ->
                val data = ByteArray(8192)
                var total: Long = 0
                var count: Int
                var lastProgress = 0
                while (input.read(data).also { count = it } != -1) {
                    total += count.toLong()
                    if (fileLength > 0) {
                        val progress = (total * 100 / fileLength).toInt()
                        if (progress != lastProgress) {
                            onProgress(progress)
                            lastProgress = progress
                        }
                    }
                    output.write(data, 0, count)
                }
            }
        }
        Timber.d("Download complete")
    }

    suspend fun initializeModel() = withContext(Dispatchers.IO) {
        if (!isModelAvailable()) {
            throw IllegalStateException("Model not downloaded yet.")
        }
        val modelFile = File(context.filesDir, modelFileName)
        
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath("/data/local/tmp/llm/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task")
                .setMaxTokens(1024)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            Timber.d("LlmInference engine initialized successfully.")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize LlmInference")
            throw e
        }
    }

    suspend fun parseSms(smsText: String): TransactionData? = withContext(Dispatchers.IO) {
        if (llmInference == null) {
            Timber.w("LlmInference not initialized.")
            return@withContext null
        }

        val prompt = """
            Extract transaction details from this SMS.
            Respond ONLY using this JSON format: {"amount": Double, "merchant": "String", "date": "String", "type": "DEBIT/CREDIT"}.
            If it is not a transaction, respond with '{}'.
            SMS: $smsText
        """.trimIndent()

        Timber.d("Parsing SMS with LLM: %s", smsText)
        return@withContext try {
            val response = llmInference?.generateResponse(prompt) ?: ""
            Timber.d("LLM Response: %s", response)
            
            // Clean response string (sometimes models wrap json in markdown ```json ... ```)
            val cleanedResponse = response.replace("```json", "").replace("```", "").trim()
            val jsonObject = JSONObject(cleanedResponse)
            
            if (jsonObject.has("amount") && jsonObject.has("merchant")) {
                TransactionData(
                    amount = jsonObject.getDouble("amount"),
                    merchant = jsonObject.getString("merchant"),
                    date = jsonObject.optString("date", "Unknown"),
                    type = jsonObject.optString("type", "UNKNOWN")
                )
            } else null
        } catch (e: Exception) {
            Timber.e(e, "Error parsing LLM response")
            null
        }
    }
}
