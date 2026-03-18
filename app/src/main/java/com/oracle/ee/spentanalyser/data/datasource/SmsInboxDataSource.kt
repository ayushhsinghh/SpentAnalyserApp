package com.oracle.ee.spentanalyser.data.datasource

import com.oracle.ee.spentanalyser.data.SmsMessage

interface SmsInboxDataSource {
    suspend fun readRecentBankSms(limit: Int = 10): List<SmsMessage>
    
    suspend fun readSmsForInbox(
        days: Int = 30,
        additionalSenders: List<String> = emptyList(),
        additionalKeywords: List<String> = emptyList()
    ): List<SmsMessage>
}
