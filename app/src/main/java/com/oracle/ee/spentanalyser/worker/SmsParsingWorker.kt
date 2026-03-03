package com.oracle.ee.spentanalyser.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

        if (!llmEngine.isInitialized()) {
            Timber.w("Worker ending: LLM engine not initialized (no model active).")
            return Result.success()
        }

        return try {
            container.systemMonitor.setCurrentTask("Background SMS parsing…")
            container.parseSmsUseCase.invoke(limit = 50)
            container.systemMonitor.setCurrentTask("Idle")

            Timber.d("SmsParsingWorker finished successfully")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Worker failed to process SMS")
            container.systemMonitor.setCurrentTask("Idle")
            container.systemMonitor.recordInference(success = false, error = e.message)
            Result.retry()
        }
    }
}
