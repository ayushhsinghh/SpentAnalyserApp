package com.oracle.ee.spentanalyser.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.oracle.ee.spentanalyser.worker.SmsParsingWorker
import timber.log.Timber

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                if (sms != null) {
                    val sender = sms.displayOriginatingAddress ?: ""
                    val body = sms.displayMessageBody ?: ""
                    Timber.d("Received SMS from: %s, Body: %s", sender, body)
                    
                    // Utilize shared constants from SmsRepository to match rules
                    val isBankSender = SmsRepository.KNOWN_BANK_SENDERS.any { sender.contains(it, ignoreCase = true) }
                    val hasKeyword = SmsRepository.TRANSACTION_KEYWORDS.any { body.contains(it, ignoreCase = true) }
                    
                    if (isBankSender && hasKeyword) {
                        Timber.d("Bank message detected from %s. Enqueueing parsing worker.", sender)
                        val workRequest = OneTimeWorkRequestBuilder<SmsParsingWorker>().build()
                        WorkManager.getInstance(context).enqueueUniqueWork(
                            "ImmediateSmsParse",
                            ExistingWorkPolicy.APPEND_OR_REPLACE,
                            workRequest
                        )
                        break // We only need to trigger the worker once per batch
                    }
                }
            }
        }
    }
}
