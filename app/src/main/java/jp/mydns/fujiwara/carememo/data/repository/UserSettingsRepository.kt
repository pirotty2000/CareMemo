package jp.mydns.fujiwara.carememo.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Context 拡張プロパティ：DataStore のインスタンス定義（ファイル名: user_settings） */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

/**
 * Repository：UserSettingsRepository
 *
 * 【役割】
 * アプリケーションの動作設定、セキュリティ設定、およびユーザーの好みを永続化して管理します。
 * Jetpack DataStore (Preferences) を使用し、キー・バリュー形式で軽量な設定値を保持します。
 *
 * 【主な機能】
 * ・セキュリティ：生体認証の有効無効、自動ロックまでの時間、バックアップ用パスワード。
 * ・UX：氏名のマスキング設定、デフォルトの記録者名、健康記録の優先表示モード。
 * ・外観：テーマ（システム同期、ライト、ダーク）の設定。
 * ・システム：操作ログの保持期間、アプリ終了時の最終アクティブ時刻、ロックバイパス制御。
 *
 * 【設計指針】
 * 1. 非同期性：設定値の読み取りには Flow を使用し、設定変更がアプリ全体にリアクティブに伝播することを保証する。
 * 2. 安全性：アプリロックに関連する設定値（タイムアウト等）をリポジトリ層で一元管理する。
 * 3. 堅牢性：Enum 変換時などはフォールバック値を定義し、不正な設定値によるクラッシュを防止する。
 */
class UserSettingsRepository(private val context: Context) {
    companion object {
        /** 氏名のマスキング（伏せ字）を有効にするか */
        private val IS_NAME_MASKING_ENABLED = booleanPreferencesKey("is_name_masking_enabled")
        /** 新規登録時のデフォルト記録者名 */
        private val DEFAULT_RECORDER_NAME = stringPreferencesKey("default_recorder_name")
        /** 生体認証によるロックを有効にするか */
        private val IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")
        /** 自動ロックまでの分（0は即時） */
        private val LOCK_TIMEOUT_MINUTES = intPreferencesKey("lock_timeout_minutes")
        /** 最後にアプリを操作した時刻（ミリ秒） */
        private val LAST_ACTIVE_TIME = longPreferencesKey("last_active_time")
        /** バックアップ生成時のパスワード保護を有効にするか */
        private val IS_BACKUP_PASSWORD_ENABLED = booleanPreferencesKey("is_backup_password_enabled")
        /** バックアップ用パスワード（平文/暫定） */
        private val BACKUP_PASSWORD = stringPreferencesKey("backup_password")
        /** テーマ設定（SYSTEM, LIGHT, DARK） */
        private val THEME_SETTING = stringPreferencesKey("theme_setting")
        /** 監査ログを自動削除するまでの日数 */
        private val AUDIT_LOG_RETENTION_DAYS = intPreferencesKey("audit_log_retention_days")
        /** 最後に監査ログのローテーションを実行した日付 (yyyy-MM-dd) */
        private val LAST_AUDIT_LOG_ROTATION_DATE = stringPreferencesKey("last_audit_log_rotation_date")
        /** 健康記録詳細で「グラフ」ではなく「履歴」を優先表示するか */
        private val HEALTH_DISPLAY_MODE_IS_HISTORY = booleanPreferencesKey("health_display_mode_is_history")
    }

    /** 氏名のマスキング設定を取得する Flow */
    val isNameMaskingEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_NAME_MASKING_ENABLED] ?: true
        }

    /** 
     * 生体認証設定を取得する Flow。
     * デフォルトは false。起動時にハードウェアの状態とあわせて動的に判定されることを想定。
     */
    val isBiometricEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_BIOMETRIC_ENABLED] ?: false
        }

    /**
     * 生体認証設定が既にユーザーまたはシステムによって初期化済みかどうかを判定するための Flow。
     * まだ一度も設定が行われていない（キーが存在しない）場合は false を返します。
     */
    val isBiometricSettingInitialized: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences.contains(IS_BIOMETRIC_ENABLED)
        }

    /** ロックタイムアウト（分）を取得する Flow */
    val lockTimeoutMinutes: Flow<Int> = context.dataStore.data
        .map { preferences ->
            // デフォルトは 0分（即時ロック）
            preferences[LOCK_TIMEOUT_MINUTES] ?: 0
        }

    /** 最終アクティブ時刻を取得する Flow */
    val lastActiveTime: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_ACTIVE_TIME] ?: 0L
        }

    /** デフォルトの記録者名を取得する Flow */
    val defaultRecorderName: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[DEFAULT_RECORDER_NAME] ?: ""
        }

    /** バックアップのパスワード保護設定を取得する Flow */
    val isBackupPasswordEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_BACKUP_PASSWORD_ENABLED] ?: true
        }

    /** バックアップ用パスワードを取得する Flow */
    val backupPassword: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[BACKUP_PASSWORD] ?: ""
        }

    /** テーマ設定を Enum 形式で取得する Flow */
    val themeSetting: Flow<ThemeSetting> = context.dataStore.data
        .map { preferences ->
            val themeName = preferences[THEME_SETTING] ?: ThemeSetting.SYSTEM.name
            try {
                ThemeSetting.valueOf(themeName)
            } catch (_: Exception) {
                // 万が一保存値が不正な場合はシステム設定にフォールバック
                ThemeSetting.SYSTEM
            }
        }

    /** 監査ログ保持期間（日）を取得する Flow */
    val auditLogRetentionDays: Flow<Int> = context.dataStore.data
        .map { preferences ->
            // デフォルトは 30日間
            preferences[AUDIT_LOG_RETENTION_DAYS] ?: 30
        }

    /** 最後に監査ログローテーションを実行した日付を取得する Flow */
    val lastAuditLogRotationDate: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_AUDIT_LOG_ROTATION_DATE] ?: ""
        }

    /** 健康記録詳細のデフォルト表示モードが「履歴」かどうかを取得する Flow */
    val healthDisplayModeIsHistory: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[HEALTH_DISPLAY_MODE_IS_HISTORY] ?: true
        }

    /** 
     * 一時的にロックを無効化するためのフラグ（メモリ保持）。
     * PDF出力時のシステム共有シート連携など、アプリを一時離脱して戻る際の誤ロック防止に使用します。
     */
    var isLockBypassed: Boolean = false

    // --- 設定値の更新メソッド群 ---

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

    suspend fun setThemeSetting(theme: ThemeSetting) {
        context.dataStore.edit { preferences ->
            preferences[THEME_SETTING] = theme.name
        }
    }

    suspend fun setAuditLogRetentionDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[AUDIT_LOG_RETENTION_DAYS] = days
        }
    }

    suspend fun setLastAuditLogRotationDate(date: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_AUDIT_LOG_ROTATION_DATE] = date
        }
    }

    suspend fun setHealthDisplayModeIsHistory(isHistory: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HEALTH_DISPLAY_MODE_IS_HISTORY] = isHistory
        }
    }
}
