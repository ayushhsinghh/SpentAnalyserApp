package com.oracle.ee.spentanalyser.di

import android.content.Context
import com.oracle.ee.spentanalyser.data.database.AppDatabase
import com.oracle.ee.spentanalyser.data.database.PreferencesManager
import com.oracle.ee.spentanalyser.data.datasource.SmsInboxDataSource
import com.oracle.ee.spentanalyser.data.datasource.SmsInboxDataSourceImpl
import com.oracle.ee.spentanalyser.data.api.ModelApiService
import com.oracle.ee.spentanalyser.data.engine.MediaPipeLlmEngine
import com.oracle.ee.spentanalyser.data.monitor.SystemMonitor
import com.oracle.ee.spentanalyser.data.repository.ModelRepositoryImpl
import com.oracle.ee.spentanalyser.data.repository.SmsLogRepositoryImpl
import com.oracle.ee.spentanalyser.data.repository.TransactionRepositoryImpl
import com.oracle.ee.spentanalyser.domain.engine.LlmInferenceEngine
import com.oracle.ee.spentanalyser.domain.repository.ModelRepository
import com.oracle.ee.spentanalyser.domain.repository.SmsLogRepository
import com.oracle.ee.spentanalyser.domain.repository.TransactionRepository
import com.oracle.ee.spentanalyser.domain.usecase.ParseSmsUseCase

class AppContainer(private val context: Context) {
    // Database
    val database by lazy { AppDatabase.getDatabase(context) }
    val appDao by lazy { database.appDao() }
    val preferencesManager by lazy { PreferencesManager(context) }
    val workManager by lazy { androidx.work.WorkManager.getInstance(context) }

    // Engine (singleton — shared across ViewModels and Worker)
    val llmEngine: LlmInferenceEngine by lazy { com.oracle.ee.spentanalyser.data.engine.DelegatingLlmEngine(context) }

    // Data Sources
    val smsInboxDataSource: SmsInboxDataSource by lazy {
        SmsInboxDataSourceImpl(context, appDao, preferencesManager)
    }

    // API
    val modelApiService by lazy { ModelApiService() }

    // Repositories
    val transactionRepository: TransactionRepository by lazy {
        TransactionRepositoryImpl(appDao)
    }
    val smsLogRepository: SmsLogRepository by lazy {
        SmsLogRepositoryImpl(appDao)
    }
    val modelRepository: ModelRepositoryImpl by lazy {
        ModelRepositoryImpl(context, preferencesManager, modelApiService)
    }

    // Monitor
    val systemMonitor: SystemMonitor by lazy {
        SystemMonitor(
            context = context,
            llmEngine = llmEngine,
            modelRepository = modelRepository
        )
    }

    // Use Cases
    val parseSmsUseCase by lazy {
        ParseSmsUseCase(smsInboxDataSource, llmEngine, transactionRepository, smsLogRepository)
    }
}
