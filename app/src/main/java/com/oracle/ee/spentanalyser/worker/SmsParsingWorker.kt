package com.oracle.ee.spentanalyser.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.OutOfQuotaPolicy
import kotlinx.coroutines.flow.first
import com.oracle.ee.spentanalyser.SpentAnalyserApplication
import timber.log.Timber

class SmsParsingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Timber.d("SmsParsingWorker started")
        val container = (applicationContext as SpentAnalyserApplication).container
        val llmEngine = container.llmEngine

        val isAutoParsingEnabled = container.preferencesManager.isAutoParsingEnabledFlow.first()
        if (!isAutoParsingEnabled) {
            Timber.i("Worker ending: Auto parsing is disabled in settings.")
            return Result.success()
        }

        if (!llmEngine.isInitialized()) {
            Timber.w("Worker ending: LLM engine not initialized (no model active).")
            return Result.success()
        }

        return try {
            val limit = 50
            container.systemMonitor.setCurrentTask("Background SMS parsing…")
            val processedCount = container.parseSmsUseCase.invoke(limit = limit)
            container.systemMonitor.setCurrentTask("Idle")

            Timber.d("SmsParsingWorker finished successfully. Processed: $processedCount")
            
            // If we processed an entire batch, there might be more waiting. Chained execution!
            if (processedCount >= limit) {
                Timber.d("Batch limit reached. Enqueuing next batch of SMS parsing...")
                val workRequest = OneTimeWorkRequestBuilder<SmsParsingWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                    
                WorkManager.getInstance(applicationContext).enqueue(workRequest)
            }
            
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Worker failed to process SMS")
            container.systemMonitor.setCurrentTask("Idle")
            container.systemMonitor.recordInference(success = false, error = e.message)
            Result.retry()
        }
    }
}
