package com.oracle.ee.spentanalyser.data.datasource

import com.oracle.ee.spentanalyser.data.SmsMessage

interface SmsInboxDataSource {
    suspend fun readRecentBankSms(limit: Int = 10): List<SmsMessage>
}
