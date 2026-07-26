package com.example.mediaplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val BACKGROUND_PLAY_KEY = booleanPreferencesKey("background_play_enabled")
    private val PIP_ENABLED_KEY = booleanPreferencesKey("pip_enabled")

    val isBackgroundPlayEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[BACKGROUND_PLAY_KEY] ?: true
        }

    val isPipEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PIP_ENABLED_KEY] ?: true
        }

    suspend fun setBackgroundPlayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BACKGROUND_PLAY_KEY] = enabled
        }
    }

    suspend fun setPipEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PIP_ENABLED_KEY] = enabled
        }
    }
}
