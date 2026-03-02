package com.oracle.ee.spentanalyser.data

import android.content.Context
import timber.log.Timber

data class SmsMessage(
    val sender: String,
    val body: String,
    val timestamp: Long
)

class SmsRepository(private val context: Context) {
    // Scaffolded for setting up later
    
    fun readRecentBankSms(limit: Int = 50): List<SmsMessage> {
        Timber.d("Reading recent SMS messages")
        val messages = mutableListOf<SmsMessage>()
        
        try {
            val cursor = context.contentResolver.query(
                android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    android.provider.Telephony.Sms.Inbox.ADDRESS,
                    android.provider.Telephony.Sms.Inbox.BODY,
                    android.provider.Telephony.Sms.Inbox.DATE
                ),
                null,
                null,
                android.provider.Telephony.Sms.Inbox.DEFAULT_SORT_ORDER + " LIMIT $limit"
            )

            cursor?.use {
                val addressColumn = it.getColumnIndexOrThrow(android.provider.Telephony.Sms.Inbox.ADDRESS)
                val bodyColumn = it.getColumnIndexOrThrow(android.provider.Telephony.Sms.Inbox.BODY)
                val dateColumn = it.getColumnIndexOrThrow(android.provider.Telephony.Sms.Inbox.DATE)

                while (it.moveToNext()) {
                    val sender = it.getString(addressColumn) ?: ""
                    val body = it.getString(bodyColumn) ?: ""
                    val date = it.getLong(dateColumn)
                    
                    // Simple heuristic to grab potential bank messages.
                    // Customize this to specific bank names or message formats.
                    if (body.contains("debited", ignoreCase = true) || 
                        body.contains("credited", ignoreCase = true) ||
                        body.contains("spent", ignoreCase = true) ||
                        body.contains("available bal", ignoreCase = true)) {
                        messages.add(SmsMessage(sender, body, date))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading SMS using ContentResolver")
        }

        Timber.d("Found %d bank-related messages", messages.size)
        return messages
    }
}
