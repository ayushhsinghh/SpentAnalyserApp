package com.oracle.ee.spentanalyser.data

import android.content.Context
import com.oracle.ee.spentanalyser.data.database.AppDao
import com.oracle.ee.spentanalyser.data.database.ParseStatus
import com.oracle.ee.spentanalyser.data.database.PreferencesManager
import com.oracle.ee.spentanalyser.data.database.SmsLogEntity
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.security.MessageDigest

data class SmsMessage(
    val uniqueHash: String,
    val sender: String,
    val body: String,
    val timestamp: Long
)

class SmsRepository(
    private val context: Context,
    private val appDao: AppDao,
    private val preferencesManager: PreferencesManager
) {
    
    companion object {
        // Constant list of Bank Sender IDs (Extendable)
        val KNOWN_BANK_SENDERS = listOf("AXISBK", "HDFCBK", "ICICIB", "SBINB", "SBI", "PNB")

        // Keywords to aggressively filter in SQLite before fetching into memory
        val TRANSACTION_KEYWORDS = listOf("debited", "debit", "credited", "credit", "spent", "available bal")

        fun generateHash(sender: String, body: String, timestamp: Long): String {
            val input = "$sender|$body|$timestamp"
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    suspend fun readRecentBankSms(limit: Int = 10): List<SmsMessage> {
        val lastProcessedTime = preferencesManager.lastProcessedTimestampFlow.first()
        Timber.d("Reading recent SMS messages since timestamp: $lastProcessedTime")
        
        val messages = mutableListOf<SmsMessage>()
        var newestSmsTimestamp = lastProcessedTime
        
        try {
            // Build the SQL selection arguments
            // 1. Must be newer than our last synced SMS (minus 1 second for overlap/drift).
            // 2. Must be from a known bank sender.
            // 3. Must contain transactional keywords.
            
            val selectionArgsList = mutableListOf<String>()
            // Subtract 1000ms to combat millisecond overlaps
            val safeSearchTime = if (lastProcessedTime > 1000L) lastProcessedTime - 1000L else 0L
            selectionArgsList.add(safeSearchTime.toString())

            val senderClauses = KNOWN_BANK_SENDERS.joinToString(" OR ") { 
                selectionArgsList.add("%$it%")
                "${android.provider.Telephony.Sms.Inbox.ADDRESS} LIKE ?" 
            }

            val keywordClauses = TRANSACTION_KEYWORDS.joinToString(" OR ") {
                selectionArgsList.add("%$it%")
                "${android.provider.Telephony.Sms.Inbox.BODY} LIKE ?"
            }

            val selection = "${android.provider.Telephony.Sms.Inbox.DATE} >= ? AND ($senderClauses) AND ($keywordClauses)"
            val sortOrder = "${android.provider.Telephony.Sms.Inbox.DATE} DESC LIMIT $limit"

            val cursor = context.contentResolver.query(
                android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    android.provider.Telephony.Sms._ID,
                    android.provider.Telephony.Sms.Inbox.ADDRESS,
                    android.provider.Telephony.Sms.Inbox.BODY,
                    android.provider.Telephony.Sms.Inbox.DATE
                ),
                selection,
                selectionArgsList.toTypedArray(),
                sortOrder
            )

            cursor?.use {
                val addressColumn = it.getColumnIndexOrThrow(android.provider.Telephony.Sms.Inbox.ADDRESS)
                val bodyColumn = it.getColumnIndexOrThrow(android.provider.Telephony.Sms.Inbox.BODY)
                val dateColumn = it.getColumnIndexOrThrow(android.provider.Telephony.Sms.Inbox.DATE)

                while (it.moveToNext()) {
                    val sender = it.getString(addressColumn) ?: ""
                    val body = it.getString(bodyColumn) ?: ""
                    val date = it.getLong(dateColumn)
                    
                    if (date > newestSmsTimestamp) {
                        newestSmsTimestamp = date
                    }

                    val hash = generateHash(sender, body, date)

                    // Skip if already parsed/logged/ignored in DB
                    // Because we use Date > lastProcessed check natively, we should very rarely hit this duplicate check.
                    if (appDao.doesSmsLogExist(hash)) {
                        continue
                    }
                    
                    messages.add(SmsMessage(hash, sender, body, date))
                }
            }
            
            // Persist the newest processed timestamp so the next run skips these entirely
            if (newestSmsTimestamp > lastProcessedTime) {
                preferencesManager.updateLastProcessedTimestamp(newestSmsTimestamp)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error reading SMS using optimized ContentResolver queries")
        }

        Timber.d("Found %d unprocessed bank-related messages natively", messages.size)
        return messages
    }
}
