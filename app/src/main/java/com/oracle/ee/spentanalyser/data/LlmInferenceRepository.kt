package com.oracle.ee.spentanalyser.data

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TransactionData( 
    val amount: Double,
    val merchant: String,
    val date: String,
    val type: String // E.g: "DEBIT", "CREDIT"
)

data class ParseResult(
    val success: Boolean,
    val data: TransactionData? = null,
    val rawOutput: String = "",
    val error: String? = null
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
        val finalModelFile = File(context.filesDir, modelFileName)
        val tempModelFile = File(context.filesDir, "$modelFileName.tmp")

        try {
            connection.inputStream.use { input ->
                tempModelFile.outputStream().use { output ->
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

            // If we reached here without an exception, the download finished.
            // Atomically rename the temp file to the final destination.
            if (tempModelFile.exists() && tempModelFile.length() > 0) {
                if (!tempModelFile.renameTo(finalModelFile)) {
                    throw Exception("Failed to rename temporary model file.")
                }
                Timber.d("Download complete and file secured.")
            } else {
                throw Exception("Downloaded model file is empty or missing.")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error downloading model")
            if (tempModelFile.exists()) {
                tempModelFile.delete() // Clean up corrupted/partial files
            }
            throw e
        }
    }

    suspend fun initializeModel() = withContext(Dispatchers.IO) {
        if (!isModelAvailable()) {
            throw IllegalStateException("Model not downloaded yet.")
        }
        val modelFile = File(context.filesDir, modelFileName)
        
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(2048)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            Timber.d("LlmInference engine initialized successfully.")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize LlmInference")
            throw e
        }
    }

    suspend fun parseSms(smsText: String): ParseResult = withContext(Dispatchers.IO) {
        if (llmInference == null) {
            Timber.w("LlmInference not initialized.")
            return@withContext ParseResult(false, error = "Model not loaded.")
        }

        val prompt = """
            Your strict task is to analyze bank SMS messages and extract transaction details into valid JSON.

            RULES:
            1. Respond ONLY with valid JSON. Do not include any introductory text, explanations.
            2. Data types MUST match these rules:
               - "amount": Number (float). Remove all commas and currency symbols (e.g., output 1500.50, not "1,500.50").
               - "merchant": String. The entity paid to or received from. If not found, use "Unknown".
               - "date": String. Extract the date exactly as it appears in the text.
               - "type": String. Must be exactly "DEBIT" or "CREDIT".
            3. If the SMS is an OTP, promotional message, or NOT a bank transaction, output exactly: {}
            4. If the SMS is from a Bank but for Credit Card Statement It will have texts like "Statement due or min due" in it, output exactly: {}

            EXAMPLES:

            SMS: Spent Rs.287 On HDFC Bank Card XXXX At Instamart Grocery On 2026-03-02:10:13:40.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 6258 to 7308080808
            {"amount": 287.00, "merchant": "Instamart Grocery", "date": "02-Mar-26", "type": "DEBIT"}
            
            SMS: NACH debit towards Indian Clearing Corp for INR 6,000.00 with UMRN UTIB7XXXXXXXX05176 has been successfully processed in A/c no. XXXX15 today(Add Today's Date) - Axis Bank
            {"amount": 6000.0, "merchant": "Indian Clearing Corp", "date": "02-Mar-26", "type": "DEBIT"}

            SMS: Your delivery PIN OR SECRET OTP for order 9981 is 8842. Do not share OTP/PIN with anyone.
            {}

            ACTUAL TASK:
            SMS: ${smsText}
        """.trimIndent()

        Timber.d("Parsing SMS with LLM: %s", smsText)
        return@withContext try {
            val response = kotlinx.coroutines.withTimeout(30000L) {
                llmInference?.generateResponse(prompt)
            } ?: return@withContext ParseResult(false, error = "Inference failed.")
            
            Timber.d("LLM Response: %s", response)
            
            // Clean response string (sometimes models wrap json in markdown ```json ... ```)
            val cleanedResponse = response.replace("```json", "").replace("```", "").trim()
            
            // 1. Try Strict Parsing
            val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }
            try {
                val transaction = jsonParser.decodeFromString<TransactionData>(cleanedResponse)
                return@withContext ParseResult(true, data = transaction, rawOutput = response)
            } catch (e: Exception) {
                Timber.w("Strict JSON parsing failed: ${e.message}")
                return@withContext ParseResult(false, rawOutput = response, error = "JSON parsing failed: ${e.message}")
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Timber.e(e, "LLM parsing timed out after 30 seconds.")
            ParseResult(false, error = "Model timeout.")
        } catch (e: Exception) {
            Timber.e(e, "Error parsing LLM response")
            ParseResult(false, error = "Exception: ${e.message}")
        }
    }
}
