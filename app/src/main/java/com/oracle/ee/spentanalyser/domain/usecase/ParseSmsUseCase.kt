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

    suspend operator fun invoke(limit: Int = 5) {
        if (!llmEngine.isInitialized()) {
            Timber.w("LlmInferenceEngine not initialized, skipping SMS parsing.")
            return
        }

        val messages = smsInboxDataSource.readRecentBankSms(limit)

        for (msg in messages) {
            try {
                val prompt = buildPrompt(msg.body)
                val rawResponse = kotlinx.coroutines.withTimeout(30_000L) {
                    llmEngine.infer(prompt)
                }

                Timber.d("LLM Response: %s", rawResponse)
                val cleanedResponse = rawResponse
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                try {
                    val transaction = jsonParser.decodeFromString<TransactionData>(cleanedResponse)
                    transactionRepository.insertTransaction(
                        Transaction(
                            amount = transaction.amount,
                            merchant = transaction.merchant,
                            date = transaction.date,
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
                } catch (e: Exception) {
                    Timber.w("JSON parsing failed: ${e.message}")
                    smsLogRepository.insertSmsLog(
                        SmsLog(
                            uniqueHash = msg.uniqueHash,
                            sender = msg.sender,
                            body = msg.body,
                            timestamp = msg.timestamp,
                            status = ParseStatus.ERROR,
                            errorMessage = "JSON parsing failed: ${e.message}",
                            rawLlmOutput = rawResponse
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Timber.e(e, "LLM inference timed out")
                smsLogRepository.insertSmsLog(
                    SmsLog(
                        uniqueHash = msg.uniqueHash, sender = msg.sender, body = msg.body,
                        timestamp = msg.timestamp, status = ParseStatus.ERROR,
                        errorMessage = "Model timeout."
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Error during SMS parsing")
                smsLogRepository.insertSmsLog(
                    SmsLog(
                        uniqueHash = msg.uniqueHash, sender = msg.sender, body = msg.body,
                        timestamp = msg.timestamp, status = ParseStatus.ERROR,
                        errorMessage = "Exception: ${e.message}"
                    )
                )
            }
        }
    }

    private fun buildPrompt(smsText: String): String = """
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
        SMS: $smsText
    """.trimIndent()
}