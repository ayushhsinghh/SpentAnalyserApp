package com.oracle.ee.spentanalyser.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.oracle.ee.spentanalyser.SpentAnalyserApplication
import com.oracle.ee.spentanalyser.data.database.ParseStatus
import com.oracle.ee.spentanalyser.data.database.SmsLogEntity
import com.oracle.ee.spentanalyser.data.database.TransactionEntity
import timber.log.Timber

class SmsParsingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Timber.d("SmsParsingWorker started")
        val container = (applicationContext as SpentAnalyserApplication).container
        val smsRepo = container.smsRepository
        val llmRepo = container.llmInferenceRepository
        val appDao = container.appDao

        if (!llmRepo.isModelAvailable()) {
            Timber.w("Worker ending: LLM Model not downloaded yet.")
            return Result.success()
        }

        try {
            val newMessages = smsRepo.readRecentBankSms(limit = 10)
            
            if (newMessages.isEmpty()) {
                Timber.d("Worker ending: No new bank SMS to parse.")
                return Result.success()
            }

            llmRepo.initializeModel()

            for (msg in newMessages) {
                Timber.d("Worker parsing SMS: ${msg.uniqueHash}")
                val attempt = llmRepo.parseSms(msg.body)
                
                if (attempt.success && attempt.data != null) {
                    appDao.insertTransaction(
                        TransactionEntity(
                            amount = attempt.data.amount,
                            merchant = attempt.data.merchant,
                            date = attempt.data.date,
                            type = attempt.data.type,
                            sourceSmsHash = msg.uniqueHash
                        )
                    )
                    appDao.insertSmsLog(SmsLogEntity(msg.uniqueHash, msg.sender, msg.body, msg.timestamp, ParseStatus.SUCCESS.name, rawLlmOutput = attempt.rawOutput))
                } else {
                    appDao.insertSmsLog(SmsLogEntity(msg.uniqueHash, msg.sender, msg.body, msg.timestamp, ParseStatus.ERROR.name, errorMessage = attempt.error, rawLlmOutput = attempt.rawOutput))
                }
            }

            Timber.d("SmsParsingWorker finished successfully")
            return Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Worker failed to process SMS")
            return Result.retry()
        }
    }
}
