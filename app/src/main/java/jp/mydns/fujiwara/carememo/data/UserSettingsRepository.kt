package jp.mydns.fujiwara.carememo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class UserSettingsRepository(private val context: Context) {
    companion object {
        private val IS_NAME_MASKING_ENABLED = booleanPreferencesKey("is_name_masking_enabled")
        private val DEFAULT_RECORDER_NAME = stringPreferencesKey("default_recorder_name")
        private val IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")
        private val LOCK_TIMEOUT_MINUTES = intPreferencesKey("lock_timeout_minutes")
        private val LAST_ACTIVE_TIME = longPreferencesKey("last_active_time")
        private val IS_BACKUP_PASSWORD_ENABLED = booleanPreferencesKey("is_backup_password_enabled")
        private val BACKUP_PASSWORD = stringPreferencesKey("backup_password")
    }

    val isNameMaskingEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_NAME_MASKING_ENABLED] ?: false
        }

    val isBiometricEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_BIOMETRIC_ENABLED] ?: false
        }

    val lockTimeoutMinutes: Flow<Int> = context.dataStore.data
        .map { preferences ->
            // デフォルトは0分（即時）とする
            preferences[LOCK_TIMEOUT_MINUTES] ?: 0
        }

    val lastActiveTime: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_ACTIVE_TIME] ?: 0L
        }

    val defaultRecorderName: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[DEFAULT_RECORDER_NAME] ?: ""
        }

    val isBackupPasswordEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_BACKUP_PASSWORD_ENABLED] ?: false
        }

    val backupPassword: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[BACKUP_PASSWORD] ?: ""
        }

    // 一時的にロックを無効化するためのフラグ（外部アプリ連携時など）
    var isLockBypassed: Boolean = false

    suspend fun setNameMaskingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_NAME_MASKING_ENABLED] = enabled
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setLockTimeoutMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[LOCK_TIMEOUT_MINUTES] = minutes
        }
    }

    suspend fun setLastActiveTime(timeMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_ACTIVE_TIME] = timeMillis
        }
    }

    suspend fun setDefaultRecorderName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_RECORDER_NAME] = name
        }
    }

    suspend fun setBackupPasswordEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_BACKUP_PASSWORD_ENABLED] = enabled
        }
    }

    suspend fun setBackupPassword(password: String) {
        context.dataStore.edit { preferences ->
            preferences[BACKUP_PASSWORD] = password
        }
    }
}
