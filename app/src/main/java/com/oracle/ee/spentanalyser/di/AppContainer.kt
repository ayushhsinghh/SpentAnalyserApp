package com.oracle.ee.spentanalyser.di

import android.content.Context
import com.oracle.ee.spentanalyser.data.LlmInferenceRepository
import com.oracle.ee.spentanalyser.data.SmsRepository
import com.oracle.ee.spentanalyser.data.database.AppDatabase
import com.oracle.ee.spentanalyser.data.database.PreferencesManager

class AppContainer(private val context: Context) {
    val database by lazy { AppDatabase.getDatabase(context) }
    val appDao by lazy { database.appDao() }
    val preferencesManager by lazy { PreferencesManager(context) }
    
    val smsRepository by lazy { SmsRepository(context, appDao, preferencesManager) }
    val llmInferenceRepository by lazy { LlmInferenceRepository(context) }
}
