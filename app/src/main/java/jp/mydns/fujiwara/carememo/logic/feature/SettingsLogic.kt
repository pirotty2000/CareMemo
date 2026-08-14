package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.runtime.Immutable
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.DatabaseInconsistency
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * インポートデータのファイル形式検証結果。
 */
enum class ImportValidationResult {
    /** 正常（Zip形式） */
    SUCCESS,
    /** Zip形式ではない */
    NOT_A_ZIP,
    /** アプリバージョンに互換性がない（バックアップが新しすぎる） */
    INCOMPATIBLE
}

/**
 * ストレージ空き容量の検証結果。
 */
enum class StorageValidationResult {
    /** 十分な空き容量がある */
    SUCCESS,
    /** 空き容量が不足している */
    INSUFFICIENT_SPACE
}

/**
 * 設定画面全体の表示状態。
 * ユーザー設定、システム統計、および内部の処理状態を一括管理します。
 *
 * @param isNameMaskingEnabled 氏名のマスキング設定
 * @param defaultRecorderName デフォルトの記録者名
 * @param isBiometricEnabled 生体認証の利用設定
 * @param lockTimeoutMinutes 自動ロックまでの時間（分）
 * @param isBackupPasswordEnabled バックアップのパスワード保護設定
 * @param backupPassword バックアップ用パスワード
 * @param themeSetting テーマ設定（システム同期、ライト、ダーク）
 * @param auditLogRetentionDays 監査ログの保持期間（日）
 * @param auditLogCount 現在の総ログ数
 * @param endedUserCount 利用終了済みの利用者数
 * @param inconsistencies 検出されたデータベースの不整合リスト
 * @param isLoading 初期ロード中フラグ
 * @param isProcessing バックアップ・復元等の実行中フラグ
 * @param processingProgress 実行中の進捗率 (0-100)
 * @param isDeveloperModeEnabled 開発者モードが有効かどうか
 * @param isForceImportEnabled バージョン互換性を無視した強制インポートを許可するか
 * @param errorMessage 画面に表示するエラーメッセージ
 */
@Immutable
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
    val inconsistencies: ImmutableList<DatabaseInconsistency> = persistentListOf(),

    // 3. 制御状態
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val processingProgress: Int = 0,
    val isDeveloperModeEnabled: Boolean = false,
    val isForceImportEnabled: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 設定画面固有の一過性イベント。
 */
sealed interface SettingsViewEvent {
    /** インポート開始前にパスワード入力を要求する */
    data object RequestImportPassword : SettingsViewEvent
    /** データの書き出し（エクスポート）が完了した */
    data object ExportSuccess : SettingsViewEvent
    /** データの復元（インポート）が完了した */
    data object ImportSuccess : SettingsViewEvent
    /** アーカイブ管理画面へ遷移 */
    data class NavigateToArchiveManagement(val mode: jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel.OperationMode) : SettingsViewEvent
    /** 監査ログ画面へ遷移 */
    data object NavigateToAuditLog : SettingsViewEvent
    /** 未割り当て写真管理画面へ遷移 */
    data object NavigateToUnassignedPhotos : SettingsViewEvent
    /** 前の画面に戻る */
    data object NavigateBack : SettingsViewEvent
}

/**
 * Logic：SettingsLogic
 *
 * 【役割】
 * 設定画面、バックアップ管理、およびシステムメンテナンスに関するドメインロジックを提供します。
 * ファイル形式の検証、バージョン互換性の判定、リソース不足のチェックを担当します。
 *
 * 【主な機能】
 * ・バックアップファイル (Zip) のマジックナンバーによる形式検証。
 * ・バックアップデータと現アプリバージョンの互換性判定。
 * ・エクスポート/インポートに必要なストレージ容量の計算と検証。
 * ・開発者モードの有効化判定（規定タップ数）。
 * ・設定項目の表示用ラベル（時間、期間等）の生成。
 *
 * 【設計指針】
 * 1. ファイル検証はバイナリレベルで行い、拡張子偽装を防止する。
 * 2. 互換性判定では、開発者モードが有効な場合に限り「将来バージョン」からのインポートを許容する柔軟性を持たせる。
 * 3. 本クラスは Context や File I/O に依存せず、計算と判定の「事実」のみを扱う。
 */
object SettingsLogic {

    /**
     * ファイルヘッダーを読み取り、Zip 形式（マジックナンバー 'PK'）に合致するか判定します。
     *
     * @param header ファイルの先頭バイト配列
     * @return 検証結果
     */
    fun validateImportFormat(header: ByteArray): ImportValidationResult {
        if (header.size < 4) return ImportValidationResult.NOT_A_ZIP
        
        // Zip ファイルのマジックナンバー: 50 4B 03 04 (PK..)
        val isZip = header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
        
        return if (isZip) ImportValidationResult.SUCCESS else ImportValidationResult.NOT_A_ZIP
    }

    /**
     * バックアップデータが現在のアプリバージョンと互換性があるか判定します。
     *
     * @param backupVersionCode バックアップに含まれる versionCode
     * @param currentVersionCode 現在のアプリの versionCode
     * @param isDeveloperMode 開発者モードが有効かどうか
     * @return 検証結果（基本的には バックアップ <= 現在 なら SUCCESS）
     */
    fun validateVersion(backupVersionCode: Int, currentVersionCode: Int, isDeveloperMode: Boolean = false): ImportValidationResult {
        // 開発者モードが有効な場合は、バージョンが新しくても「強制インポート」を可能にする
        return if (isDeveloperMode || backupVersionCode <= currentVersionCode) {
            ImportValidationResult.SUCCESS
        } else {
            ImportValidationResult.INCOMPATIBLE
        }
    }

    /**
     * 要求されたバイト数以上の空き容量があるか判定します。
     *
     * @param availableBytes デバイスの利用可能な空きバイト数
     * @param requiredBytes 処理に最低限必要な予測バイト数
     * @return 検証結果
     */
    fun validateStorageSpace(availableBytes: Long, requiredBytes: Long): StorageValidationResult {
        return if (availableBytes >= requiredBytes) {
            StorageValidationResult.SUCCESS
        } else {
            StorageValidationResult.INSUFFICIENT_SPACE
        }
    }

    /**
     * 指定されたタップ回数が、開発者モードを有効にする閾値に達しているか判定します。
     *
     * @param tapCount 現在の連続タップ数
     * @return 有効にすべきなら true
     */
    fun shouldEnableDeveloperMode(tapCount: Int): Boolean {
        return tapCount >= AppSpecifications.Constraints.System.Security.DEVELOPER_MODE_TAP_COUNT
    }

    /**
     * ロックタイムアウト時間の表示用ラベルを取得します。
     *
     * @param minutes タイムアウト分
     * @return 「5分」などのラベル。未定義の場合はデフォルト値を生成。
     */
    fun getTimeoutLabel(minutes: Int): String {
        return AppSpecifications.Settings.LOCK_TIMEOUT_OPTIONS.find { it.first == minutes }?.second
            ?: "${minutes}分"
    }

    /**
     * 監査ログ保持期間の表示用ラベルを取得します。
     *
     * @param days 保持日数
     * @return 「30日間」などのラベル。
     */
    fun getRetentionLabel(days: Int): String {
        return AppSpecifications.Settings.AUDIT_LOG_RETENTION_OPTIONS.find { it.first == days }?.second
            ?: "${days}日間"
    }
}
