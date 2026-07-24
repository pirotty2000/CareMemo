package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.DatabaseInconsistency
import jp.mydns.fujiwara.carememo.data.ThemeSetting

/**
 * インポートデータの検証結果（事実）
 */
enum class ImportValidationResult {
    SUCCESS,
    NOT_A_ZIP,
    INCOMPATIBLE
}

/**
 * ストレージ容量の検証結果（事実）
 */
enum class StorageValidationResult {
    SUCCESS,
    INSUFFICIENT_SPACE
}

/**
 * 設定画面全体の表示状態
 */
data class SettingsUiState(
    // 1. 基本設定（UserSettingsRepository 由来）
    val isNameMaskingEnabled: Boolean = true,
    val defaultRecorderName: String = "",
    val isBiometricEnabled: Boolean = false,
    val lockTimeoutMinutes: Int = 5,
    val isBackupPasswordEnabled: Boolean = false,
    val backupPassword: String = "",
    val themeSetting: ThemeSetting = ThemeSetting.SYSTEM,

    // 2. 管理情報（各 Repository からの統計）
    val auditLogRetentionDays: Int = AppSpecifications.Constraints.System.AuditLog.DEFAULT_RETENTION_DAYS,
    val auditLogCount: Int = 0,
    val endedUserCount: Int = 0,
    val inconsistencies: List<DatabaseInconsistency> = emptyList(),

    // 3. 制御状態
    val isLoading: Boolean = false,      // 画面の初期ロード用
    val isProcessing: Boolean = false,   // バックアップ等の重い処理用
    val processingProgress: Int = 0,     // 実行進捗
    val isDeveloperModeEnabled: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 設定画面固有のイベント
 */
sealed interface SettingsViewEvent {
    /** インポート等でパスワード入力を要求する */
    object RequestImportPassword : SettingsViewEvent
    /** データの書き出しが完了した */
    object ExportSuccess : SettingsViewEvent
    /** データの復元が完了した */
    object ImportSuccess : SettingsViewEvent
}

/**
 * 設定画面・バックアップ管理に関するドメインロジック。
 */
object SettingsLogic {

    /**
     * ファイルヘッダーが Zip 形式（マジックナンバー）に合致するか判定します。
     */
    fun validateImportFormat(header: ByteArray): ImportValidationResult {
        if (header.size < 4) return ImportValidationResult.NOT_A_ZIP
        
        val isZip = header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
        
        return if (isZip) ImportValidationResult.SUCCESS else ImportValidationResult.NOT_A_ZIP
    }

    /**
     * バックアップデータが現在のアプリバージョンと互換性があるか判定します。
     */
    fun validateVersion(backupVersionCode: Int, currentVersionCode: Int): ImportValidationResult {
        return if (backupVersionCode <= currentVersionCode) {
            ImportValidationResult.SUCCESS
        } else {
            ImportValidationResult.INCOMPATIBLE
        }
    }

    /**
     * 要求されたバイト数以上の空き容量があるか判定します。
     * 依存性を排除するため、数値の比較のみを行います。
     */
    fun validateStorageSpace(availableBytes: Long, requiredBytes: Long): StorageValidationResult {
        return if (availableBytes >= requiredBytes) {
            StorageValidationResult.SUCCESS
        } else {
            StorageValidationResult.INSUFFICIENT_SPACE
        }
    }

    /**
     * 開発者モードを有効にすべきか判定します。
     */
    fun shouldEnableDeveloperMode(tapCount: Int): Boolean {
        return tapCount >= AppSpecifications.Constraints.System.Security.DEVELOPER_MODE_TAP_COUNT
    }

    /**
     * 再ロック時間の表示用ラベルを取得します。
     */
    fun getTimeoutLabel(minutes: Int): String {
        return AppSpecifications.Settings.LOCK_TIMEOUT_OPTIONS.find { it.first == minutes }?.second
            ?: "${minutes}分"
    }

    /**
     * 監査ログ保持期間の表示用ラベルを取得します。
     */
    fun getRetentionLabel(days: Int): String {
        return AppSpecifications.Settings.AUDIT_LOG_RETENTION_OPTIONS.find { it.first == days }?.second
            ?: "${days}日間"
    }
}
