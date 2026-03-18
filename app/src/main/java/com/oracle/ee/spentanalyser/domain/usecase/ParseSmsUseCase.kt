package com.oracle.ee.spentanalyser.domain.usecase

import com.oracle.ee.spentanalyser.data.TransactionData
import com.oracle.ee.spentanalyser.data.datasource.SmsInboxDataSource
import com.oracle.ee.spentanalyser.data.monitor.SystemMonitor
import com.oracle.ee.spentanalyser.domain.engine.LlmInferenceEngine
import com.oracle.ee.spentanalyser.domain.model.ParseStatus
import com.oracle.ee.spentanalyser.domain.model.SmsLog
import com.oracle.ee.spentanalyser.domain.model.Transaction
import com.oracle.ee.spentanalyser.domain.repository.SmsLogRepository
import com.oracle.ee.spentanalyser.domain.repository.TransactionRepository
import com.oracle.ee.spentanalyser.domain.util.MerchantNormalizer
import kotlinx.coroutines.yield
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
    private val smsLogRepository: SmsLogRepository,
    private val systemMonitor: SystemMonitor
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
            yield() // Check for cancellation
            systemMonitor.setCurrentTask("Parsing SMS ${index + 1}/${messages.size}")
            var attempt = 1
            val maxAttempts = 2
            var success = false
            var lastErrorMsg = ""
            var lastRawResponse = ""

            while (attempt <= maxAttempts && !success) {
                try {
                    val isRetry = attempt > 1
                    val prompt = buildPrompt(msg.body, isRetry)
                    val rawResponse = kotlinx.coroutines.withTimeout(60_000L) {
                        llmEngine.infer(prompt)
                    }

                    lastRawResponse = rawResponse
                    Timber.d("LLM Response (Attempt %d): %s", attempt, rawResponse)
                    
                    val cleanedResponse = rawResponse
                        .replace("```json", "")
                        .replace("```", "")
                        .trim()
                        .replace(Regex("\"(?i)(amount|merchant|category|type|date)\"")) { it.value.lowercase() }

                    try {
                        val transaction = jsonParser.decodeFromString<TransactionData>(cleanedResponse)
                        val sdf = java.text.SimpleDateFormat("dd-MMM-yy", java.util.Locale.getDefault())
                        val formattedDate = sdf.format(java.util.Date(msg.timestamp))
                        
                        val normalizedMerchant = transactionRepository.getNormalizedMerchantOrDefault(transaction.merchant)
                        transactionRepository.insertTransaction(
                            Transaction(
                                amount = transaction.amount,
                                merchant = normalizedMerchant,
                                category = transaction.category,
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
                        systemMonitor.recordInference(success = true)
                    } catch (e: Exception) {
                        lastErrorMsg = "JSON parsing failed: ${e.message}"
                        Timber.w(lastErrorMsg)
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    lastErrorMsg = "Model timeout."
                    Timber.e(e, "LLM inference timed out on attempt $attempt")
                    systemMonitor.recordInference(success = false, error = lastErrorMsg)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    Timber.d("Parsing cancelled manually.")
                    systemMonitor.setCurrentTask("Idle")
                    throw e // Re-throw to cancel the coroutine cleanly
                } catch (e: Exception) {
                    lastErrorMsg = "Exception: ${e.message}"
                    Timber.e(e, "Error during SMS parsing on attempt $attempt")
                    systemMonitor.recordInference(success = false, error = lastErrorMsg)
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
        
        systemMonitor.setCurrentTask("Idle")
        return messages.size
    }

    suspend fun parseSingleSms(log: SmsLog, additionalPrompt: String): Boolean {
        if (!llmEngine.isInitialized()) {
            Timber.w("LlmInferenceEngine not initialized, skipping single SMS parsing.")
            return false
        }

        systemMonitor.setCurrentTask("Retrying Parse")
        var attempt = 1
        val maxAttempts = 2
        var success = false
        var lastErrorMsg = ""
        var lastRawResponse = ""

        while (attempt <= maxAttempts && !success) {
            try {
                val isRetry = attempt > 1
                val prompt = buildPrompt(log.body, isRetry, additionalPrompt)
                val rawResponse = kotlinx.coroutines.withTimeout(60_000L) {
                    llmEngine.infer(prompt)
                }

                lastRawResponse = rawResponse
                Timber.d("LLM Response (Manual Retry, Attempt %d): %s", attempt, rawResponse)

                val cleanedResponse = rawResponse
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()
                    .replace(Regex("\"(?i)(amount|merchant|category|type|date)\"")) { it.value.lowercase() }

                try {
                    val transaction = jsonParser.decodeFromString<TransactionData>(cleanedResponse)
                    val sdf = java.text.SimpleDateFormat("dd-MMM-yy", java.util.Locale.getDefault())
                    val formattedDate = sdf.format(java.util.Date(log.timestamp))

                    val normalizedMerchant = transactionRepository.getNormalizedMerchantOrDefault(transaction.merchant)
                    transactionRepository.insertTransaction(
                        Transaction(
                            amount = transaction.amount,
                            merchant = normalizedMerchant,
                            category = transaction.category,
                            date = formattedDate,
                            type = transaction.type,
                            sourceSmsHash = log.uniqueHash,
                            timestamp = log.timestamp
                        )
                    )
                    
                    // Update log to SUCCESS
                    smsLogRepository.insertSmsLog(
                        log.copy(
                            status = ParseStatus.SUCCESS,
                            rawLlmOutput = rawResponse,
                            errorMessage = null
                        )
                    )
                    success = true
                    systemMonitor.recordInference(success = true)
                } catch (e: Exception) {
                    lastErrorMsg = "JSON parsing failed: ${e.message}"
                    Timber.w(lastErrorMsg)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                lastErrorMsg = "Model timeout."
                Timber.e(e, "LLM inference timed out during manual retry on attempt $attempt")
                systemMonitor.recordInference(success = false, error = lastErrorMsg)
            } catch (e: kotlinx.coroutines.CancellationException) {
                Timber.d("Manual retry parsing cancelled.")
                systemMonitor.setCurrentTask("Idle")
                throw e
            } catch (e: Exception) {
                lastErrorMsg = "Exception: ${e.message}"
                Timber.e(e, "Error during manual SMS parsing on attempt $attempt")
                systemMonitor.recordInference(success = false, error = lastErrorMsg)
            }

            attempt++
        }

        if (!success) {
            // Update log to ERROR
            smsLogRepository.insertSmsLog(
                log.copy(
                    status = ParseStatus.ERROR,
                    errorMessage = "Retry failed: $lastErrorMsg",
                    rawLlmOutput = lastRawResponse
                )
            )
        }

        systemMonitor.setCurrentTask("Idle")
        return success
    }

    private fun buildPrompt(smsText: String, isRetry: Boolean = false, additionalPrompt: String? = null): String {
        val basePrompt = """
Your strict task is to analyze bank SMS messages and Extract the transaction details from the following bank SMS in JSON format.
RULES:
1. Respond ONLY with valid JSON. Do not include any introductory text or explanations.
2. Data types MUST match these rules:
- "amount": Number (float). You MUST preserve the decimal point and exact cents. ONLY remove commas and currency symbols (e.g., if SMS says "1,234.78", output 1234.78).
- "merchant": String. The entity paid to or received from. If not found, use "Unknown".
- "category": Classify the merchant into a spending category
- "type": String. Must be exactly "DEBIT" or "CREDIT".
3. If the SMS is an OTP, promotional message, or NOT a bank transaction, output exactly: {}
4. If the SMS is a Credit Card Statement or payment reminder (contains "Statement", "min. due", or "Total due"), output exactly: {}

Valid Categories: Food, Grocery, Shopping, Travel, Fuel, Investment, EMI, Utilities, Recharge, Entertainment, Insurance, Subscription, Health, Education, Income, Credit Card, Payment.

Classification rules:
- Salary, refund, cashback, dividend, interest credit -> Income
- UPI transfer to person, bank transfer to person, NACH debit, generic transfer, unknown payee -> Payment
- Credit card bill payment, card statement, min due, total due -> {}
- Swiggy, Zomato, Dominos, McDonalds, KFC -> Food
- Blinkit, Zepto, Instamart, BigBasket, JioMart, DMart -> Grocery
- Amazon, Flipkart, Myntra, Ajio, Nykaa, Meesho -> Shopping
- Ola, Uber, Rapido, IRCTC, RedBus, Indigo, Air India -> Travel
- Indian Oil, HP, Bharat Petroleum, Shell -> Fuel
- Zerodha, Groww, Upstox, Angel One, Mutual Fund -> Investment
- Bajaj Finance, loan EMI, ECS EMI -> EMI
- Electricity, power, gas, water bill -> Utilities
- Jio, Airtel, VI, BSNL, DTH, broadband, fiber recharge -> Recharge
- Netflix, Hotstar, Prime, Spotify, BookMyShow, PVR, INOX -> Entertainment
- LIC, HDFC Life, SBI Life, ACKO, health insurance premium -> Insurance
- Google Play, Apple, Adobe, Microsoft, OpenAI, Canva, Zoom, GitHub, Figma -> Subscription
- Apollo, PharmEasy, 1mg, Netmeds, hospital, lab -> Health
- Unacademy, BYJUS, Udemy, Coursera, Physics Wallah, Scaler -> Education
    
EXAMPLES:

SMS: Spent Rs.287.50 On HDFC Bank Card XXXX At Instamart Grocery On 2026-03-02:10:13:40.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 6258 to 7308080808
{"amount": 287.50, "merchant": "Instamart Grocery", "category": "Grocery", "type": "DEBIT"}

SMS: NACH debit towards Indian Clearing Corp for INR 6,000.00 with UMRN UTIB7XXXXXXXX05176 has been successfully processed in A/c no. XXXX15 today - Axis Bank
{"amount": 6000.00, "merchant": "Indian Clearing Corp", "category": "Payment", "type": "DEBIT"}

SMS: Acct XX999 credited with INR 1,234.78 on 01-Mar-26 from EMPLOYER SALARY. Available Bal: INR 55,000.00.
{"amount": 1234.78, "merchant": "EMPLOYER SALARY", "category": "Income", "type": "CREDIT"}

SMS: Your delivery PIN OR SECRET OTP for order 9981 is 8842. Do not share OTP/PIN with anyone.
{}

SMS: HDFC Bank Credit Card XXXX31 Statement: Total due: Rs.17,083.00 Min.due: Rs.860.00 Pay by 21-03-2026
{}

ACTUAL TASK:
SMS: $smsText
""".trimIndent()

        

        val finalBasePrompt = if (!additionalPrompt.isNullOrBlank()) {
            "$basePrompt\n\nUSER HINT TO FOLLOW: $additionalPrompt"
        } else {
            basePrompt
        }
        
        return if (isRetry) {
            val randomSeed = (0..99999).random()
            "$finalBasePrompt\n[RANDOM_SEED: $randomSeed]"
        } else {
            finalBasePrompt
        }
    }
}