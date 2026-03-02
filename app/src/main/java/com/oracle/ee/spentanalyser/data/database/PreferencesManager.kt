package com.oracle.ee.spentanalyser.data.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spent_analyzer_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val LAST_PROCESSED_TIMESTAMP = longPreferencesKey("last_processed_timestamp")
    }

    val lastProcessedTimestampFlow: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_PROCESSED_TIMESTAMP] ?: 0L
        }

    suspend fun updateLastProcessedTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_PROCESSED_TIMESTAMP] = timestamp
        }
    }
}
