package com.oracle.ee.spentanalyser

import android.app.Application
import timber.log.Timber

class SpentAnalyserApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging throughout the application
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
