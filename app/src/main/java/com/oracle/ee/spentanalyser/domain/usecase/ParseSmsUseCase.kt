package com.oracle.ee.spentanalyser.domain.usecase

import com.oracle.ee.spentanalyser.data.TransactionData
import com.oracle.ee.spentanalyser.data.datasource.SmsInboxDataSource
import com.oracle.ee.spentanalyser.domain.engine.LlmInferenceEngine
import com.oracle.ee.spentanalyser.domain.model.ParseStatus
import com.oracle.ee.spentanalyser.domain.model.SmsLog
import com.oracle.ee.spentanalyser.domain.model.Transaction
import com.oracle.ee.spentanalyser.domain.repository.SmsLogRepository
import com.oracle.ee.spentanalyser.domain.repository.TransactionRepository
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Orchestrates the full SMS parsing pipeline:
 * 1. Reads unprocessed bank SMS from the device inbox
 * 2. Builds a prompt and runs each message through the LlmInferenceEngine
 * 3. Parses the JSON output and persists results
 *
 * Now depends on the abstract LlmInferenceEngine (Strategy Pattern)
 * instead of the concrete LlmDataSource.
 */
class ParseSmsUseCase(
    private val smsInboxDataSource: SmsInboxDataSource,
    private val llmEngine: LlmInferenceEngine,
    private val transactionRepository: TransactionRepository,
    private val smsLogRepository: SmsLogRepository
) {
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend operator fun invoke(limit: Int = 5, onProgress: ((processed: Int, total: Int) -> Unit)? = null): Int {
        if (!llmEngine.isInitialized()) {
            Timber.w("LlmInferenceEngine not initialized, skipping SMS parsing.")
            return 0
        }

        val messages = smsInboxDataSource.readRecentBankSms(limit)
        onProgress?.invoke(0, messages.size)
        if (messages.isEmpty()) {
            return 0
        }

        for ((index, msg) in messages.withIndex()) {
            var attempt = 1
            val maxAttempts = 2
            var success = false
            var lastErrorMsg = ""
            var lastRawResponse = ""

            while (attempt <= maxAttempts && !success) {
                try {
                    val isRetry = attempt > 1
                    val prompt = buildPrompt(msg.body, isRetry)
                    val rawResponse = kotlinx.coroutines.withTimeout(30_000L) {
                        llmEngine.infer(prompt)
                    }

                    lastRawResponse = rawResponse
                    Timber.d("LLM Response (Attempt %d): %s", attempt, rawResponse)
                    
                    val cleanedResponse = rawResponse
                        .replace("```json", "")
                        .replace("```", "")
                        .trim()

                    try {
                        val transaction = jsonParser.decodeFromString<TransactionData>(cleanedResponse)
                        val sdf = java.text.SimpleDateFormat("dd-MMM-yy", java.util.Locale.getDefault())
                        val formattedDate = sdf.format(java.util.Date(msg.timestamp))
                        
                        transactionRepository.insertTransaction(
                            Transaction(
                                amount = transaction.amount,
                                merchant = transaction.merchant,
                                date = formattedDate,
                                type = transaction.type,
                                sourceSmsHash = msg.uniqueHash,
                                timestamp = msg.timestamp
                            )
                        )
                        smsLogRepository.insertSmsLog(
                            SmsLog(
                                uniqueHash = msg.uniqueHash,
                                sender = msg.sender,
                                body = msg.body,
                                timestamp = msg.timestamp,
                                status = ParseStatus.SUCCESS,
                                rawLlmOutput = rawResponse
                            )
                        )
                        success = true 
                    } catch (e: Exception) {
                        lastErrorMsg = "JSON parsing failed: ${e.message}"
                        Timber.w(lastErrorMsg)
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    lastErrorMsg = "Model timeout."
                    Timber.e(e, "LLM inference timed out on attempt $attempt")
                } catch (e: Exception) {
                    lastErrorMsg = "Exception: ${e.message}"
                    Timber.e(e, "Error during SMS parsing on attempt $attempt")
                }
                
                attempt++
            }

            if (!success) {
                // All attempts failed, log the final error
                smsLogRepository.insertSmsLog(
                    SmsLog(
                        uniqueHash = msg.uniqueHash,
                        sender = msg.sender,
                        body = msg.body,
                        timestamp = msg.timestamp,
                        status = ParseStatus.ERROR,
                        errorMessage = lastErrorMsg,
                        rawLlmOutput = lastRawResponse
                    )
                )
            }

            onProgress?.invoke(index + 1, messages.size)
        }
        
        return messages.size
    }

    private fun buildPrompt(smsText: String, isRetry: Boolean = false): String {
        val basePrompt = """
            Your strict task is to analyze bank SMS messages and extract transaction details into valid JSON.
            RULES:
            1. Respond ONLY with valid JSON. Do not include any introductory text or explanations.
            2. Data types MUST match these rules:
            - "amount": Number (float). You MUST preserve the decimal point and exact cents. ONLY remove commas and currency symbols (e.g., if SMS says "1,234.78", output 1234.78).
            - "merchant": String. The entity paid to or received from. If not found, use "Unknown".
            - "type": String. Must be exactly "DEBIT" or "CREDIT".
            3. If the SMS is an OTP, promotional message, or NOT a bank transaction, output exactly: {}
            4. If the SMS is a Credit Card Statement or payment reminder (contains "Statement", "min. due", or "Total due"), output exactly: {}
    
            EXAMPLES:
    
            SMS: Spent Rs.287.50 On HDFC Bank Card XXXX At Instamart Grocery On 2026-03-02:10:13:40.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 6258 to 7308080808
            {"amount": 287.50, "merchant": "Instamart Grocery", "type": "DEBIT"}
    
            SMS: NACH debit towards Indian Clearing Corp for INR 6,000.00 with UMRN UTIB7XXXXXXXX05176 has been successfully processed in A/c no. XXXX15 today - Axis Bank
            {"amount": 6000.00, "merchant": "Indian Clearing Corp", "type": "DEBIT"}
    
            SMS: Acct XX999 credited with INR 1,234.78 on 01-Mar-26 from EMPLOYER SALARY. Available Bal: INR 55,000.00.
            {"amount": 1234.78, "merchant": "EMPLOYER SALARY", "type": "CREDIT"}
    
            SMS: Your delivery PIN OR SECRET OTP for order 9981 is 8842. Do not share OTP/PIN with anyone.
            {}
            
            SMS: HDFC Bank Credit Card XXXX31 Statement: Total due: Rs.17,083.00 Min.due: Rs.860.00 Pay by 21-03-2026
            {}
    
            ACTUAL TASK:
            SMS: $smsText
        """.trimIndent()
        
        return if (isRetry) {
            val randomSeed = (0..99999).random()
            "$basePrompt\n[RANDOM_SEED: $randomSeed]"
        } else {
            basePrompt
        }
    }
}