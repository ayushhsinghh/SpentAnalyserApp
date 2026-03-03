package com.oracle.ee.spentanalyser

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.oracle.ee.spentanalyser.di.AppContainer
import com.oracle.ee.spentanalyser.worker.SmsParsingWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit

class SpentAnalyserApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        
        container = AppContainer(this)
        
        // Initialize Timber for logging throughout the application
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        setupBackgroundWork()
    }

    private fun setupBackgroundWork() {
        // Enforce strict constraints since the 1B LLM takes ~1.5GB RAM and heavy CPU
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(true) // Wait until plugged in
            .setRequiresDeviceIdle(true) // Ensure user isn't actively using the phone
            .build()
            
        val workRequest = PeriodicWorkRequestBuilder<SmsParsingWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SmsParsingWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
