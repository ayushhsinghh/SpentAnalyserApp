package com.oracle.ee.spentanalyser.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import timber.log.Timber

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                if (sms != null) {
                    val sender = sms.displayOriginatingAddress
                    val body = sms.displayMessageBody
                    Timber.d("Received SMS from: %s, Body: %s", sender, body)
                    
                    // Simple heuristic to grab potential bank messages.
                    if (body.contains("debited", ignoreCase = true) || 
                        body.contains("credited", ignoreCase = true) ||
                        body.contains("spent", ignoreCase = true) ||
                        body.contains("available bal", ignoreCase = true)) {
                        
                        // TODO: Notify ViewModel or save to local database for LLM inference
                        Timber.d("Bank message detected from %s", sender)
                    }
                }
            }
        }
    }
}
