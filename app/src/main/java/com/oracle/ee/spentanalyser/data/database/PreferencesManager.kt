package com.oracle.ee.spentanalyser.data.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spent_analyzer_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val LAST_PROCESSED_TIMESTAMP = longPreferencesKey("last_processed_timestamp")
        val ACTIVE_MODEL_ID = stringPreferencesKey("active_model_id")
        val ACTIVE_MODEL_FILE_NAME = stringPreferencesKey("active_model_file_name")
        val ACTIVE_MODEL_NAME = stringPreferencesKey("active_model_name")
        val USE_GPU = booleanPreferencesKey("use_gpu")
        val AUTO_PARSING_ENABLED = booleanPreferencesKey("auto_parsing_enabled")
        val IS_INITIAL_HISTORY_PROCESSED = booleanPreferencesKey("is_initial_history_processed")
    }

    // ── SMS Parsing ──
    val lastProcessedTimestampFlow: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_PROCESSED_TIMESTAMP] ?: 0L
        }

    suspend fun updateLastProcessedTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_PROCESSED_TIMESTAMP] = timestamp
        }
    }

    // ── Model Management ──
    val activeModelIdFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[ACTIVE_MODEL_ID]
        }
        
    val activeModelFileNameFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[ACTIVE_MODEL_FILE_NAME]
        }
        
    val activeModelNameFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[ACTIVE_MODEL_NAME]
        }

    suspend fun setActiveModel(modelId: String, fileName: String, modelName: String) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_MODEL_ID] = modelId
            preferences[ACTIVE_MODEL_FILE_NAME] = fileName
            preferences[ACTIVE_MODEL_NAME] = modelName
        }
    }

    val useGpuFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[USE_GPU] ?: false  // Default to CPU
        }

    suspend fun setUseGpu(useGpu: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_GPU] = useGpu
        }
    }

    // ── App Settings ──
    val isAutoParsingEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_PARSING_ENABLED] ?: true // Default to true
        }

    suspend fun setAutoParsingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_PARSING_ENABLED] = enabled
        }
    }

    val isInitialHistoryProcessedFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_INITIAL_HISTORY_PROCESSED] ?: false
        }

    suspend fun setInitialHistoryProcessed(processed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_INITIAL_HISTORY_PROCESSED] = processed
        }
    }
}
