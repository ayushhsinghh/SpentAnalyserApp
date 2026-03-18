package com.oracle.ee.spentanalyser.data.datasource

import android.content.Context
import com.oracle.ee.spentanalyser.data.SmsMessage
import com.oracle.ee.spentanalyser.data.database.PreferencesManager
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.security.MessageDigest

class SmsInboxDataSourceImpl(
    private val context: Context,
    private val appDao: com.oracle.ee.spentanalyser.data.database.AppDao,
    private val preferencesManager: PreferencesManager
) : SmsInboxDataSource {

    companion object {
        val KNOWN_BANK_SENDERS = listOf("AXISBK", "HDFCBK", "ICICIB", "SBINB", "SBI", "PNB")
        val TRANSACTION_KEYWORDS = listOf("debited", "credited", "spent", "debit")

        fun generateHash(sender: String, body: String, timestamp: Long): String {
            val input = "$sender|$body|$timestamp"
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    override suspend fun readRecentBankSms(limit: Int): List<SmsMessage> {
        var lastProcessedTime = preferencesManager.lastProcessedTimestampFlow.first()

        if (lastProcessedTime == 0L) {
            lastProcessedTime = System.currentTimeMillis() - (10L * 24 * 60 * 60 * 1000)
            Timber.d("First run detected. Setting last processed time to 10 days ago.")
        }

        Timber.d("Reading recent SMS messages since timestamp: $lastProcessedTime")

        val messages = mutableListOf<SmsMessage>()
        var newestSmsTimestamp = lastProcessedTime

        try {
            val selectionArgsList = mutableListOf<String>()
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
            val sortOrder = if (limit == Int.MAX_VALUE) {
                "${android.provider.Telephony.Sms.Inbox.DATE} ASC"
            } else {
                "${android.provider.Telephony.Sms.Inbox.DATE} ASC LIMIT $limit"
            }

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

                    if (appDao.doesSmsLogExist(hash)) {
                        continue
                    }

                    messages.add(SmsMessage(hash, sender, body, date))
                }
            }

            if (newestSmsTimestamp > lastProcessedTime) {
                preferencesManager.updateLastProcessedTimestamp(newestSmsTimestamp)
            }

        } catch (e: Exception) {
            Timber.e(e, "Error reading SMS using optimized ContentResolver queries")
        }

        Timber.d("Found %d unprocessed bank-related messages natively", messages.size)
        return messages
    }

    override suspend fun readSmsForInbox(
        days: Int,
        additionalSenders: List<String>,
        additionalKeywords: List<String>
    ): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val timeLimit = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000)

        val activeSenders = (KNOWN_BANK_SENDERS + additionalSenders).filter { it.isNotBlank() }.distinct()
        val activeKeywords = additionalKeywords.filter { it.isNotBlank() }.distinct()

        try {
            val selectionArgsList = mutableListOf<String>()
            selectionArgsList.add(timeLimit.toString())

            val senderClauses = if (activeSenders.isNotEmpty()) {
                activeSenders.joinToString(" OR ") {
                    selectionArgsList.add("%$it%")
                    "${android.provider.Telephony.Sms.Inbox.ADDRESS} LIKE ?"
                }
            } else "1=1"

            val keywordClauses = if (activeKeywords.isNotEmpty()) {
                activeKeywords.joinToString(" OR ") {
                    selectionArgsList.add("%$it%")
                    "${android.provider.Telephony.Sms.Inbox.BODY} LIKE ?"
                }
            } else "1=1"

            val selection = "${android.provider.Telephony.Sms.Inbox.DATE} >= ? AND ($senderClauses) AND ($keywordClauses)"
            val sortOrder = "${android.provider.Telephony.Sms.Inbox.DATE} DESC"

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

                    val hash = generateHash(sender, body, date)
                    messages.add(SmsMessage(hash, sender, body, date))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading SMS for Inbox via ContentResolver")
        }

        return messages
    }
}
