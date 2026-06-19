package jp.mydns.fujiwara.carememo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class UserSettingsRepository(private val context: Context) {
    companion object {
        private val IS_NAME_MASKING_ENABLED = booleanPreferencesKey("is_name_masking_enabled")
        private val DEFAULT_RECORDER_NAME = stringPreferencesKey("default_recorder_name")
    }

    val isNameMaskingEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_NAME_MASKING_ENABLED] ?: false
        }

    val defaultRecorderName: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[DEFAULT_RECORDER_NAME] ?: ""
        }

    suspend fun setNameMaskingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_NAME_MASKING_ENABLED] = enabled
        }
    }

    suspend fun setDefaultRecorderName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_RECORDER_NAME] = name
        }
    }
}
