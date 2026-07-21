package jp.mydns.fujiwara.carememo.viewmodel

/**
 * アプリ固有の例外の基底クラス。
 * 監査ログ用のメッセージに加え、UIに表示するためのリソース情報を保持します。
 */
open class AppException(
    val titleResId: Int? = null,
    val messageResId: Int? = null,
    val args: List<Any> = emptyList(),
    logMessage: String,
    cause: Throwable? = null
) : Exception(logMessage, cause)

/**
 * バリデーション失敗（入力不備、重複など）を表す例外
 */
class AppValidationException(
    titleResId: Int? = null,
    messageResId: Int? = null,
    args: List<Any> = emptyList(),
    logMessage: String
) : AppException(titleResId, messageResId, args, logMessage)

/**
 * 入出力（ファイル操作、バックアップ等）の失敗を表す例外
 */
class AppIOException(
    titleResId: Int? = null,
    messageResId: Int? = null,
    args: List<Any> = emptyList(),
    logMessage: String,
    cause: Throwable? = null
) : AppException(titleResId, messageResId, args, logMessage, cause)

/**
 * データ整合性・DBエラーを表す例外
 */
class AppDataException(
    titleResId: Int? = null,
    messageResId: Int? = null,
    args: List<Any> = emptyList(),
    logMessage: String,
    cause: Throwable? = null
) : AppException(titleResId, messageResId, args, logMessage, cause)

/**
 * 外部アプリ連携・ハードウェア（カメラ等）エラーを表す例外
 */
class AppExternalException(
    titleResId: Int? = null,
    messageResId: Int? = null,
    args: List<Any> = emptyList(),
    logMessage: String,
    cause: Throwable? = null
) : AppException(titleResId, messageResId, args, logMessage, cause)

/**
 * セキュリティ・権限（生体認証失敗等）エラーを表す例外
 */
class AppSecurityException(
    titleResId: Int? = null,
    messageResId: Int? = null,
    args: List<Any> = emptyList(),
    logMessage: String,
    cause: Throwable? = null
) : AppException(titleResId, messageResId, args, logMessage, cause)

/**
 * 例外発生時の付随情報を保持するイミュータブルなデータクラス
 */
data class ErrorContext(
    val featureName: String,
    val operation: String,
    val tableName: String? = null,
    val affectedId: String? = null,
    val errorTitleRes: Int? = null,
    val errorMessageRes: Int? = null
)

/**
 * ErrorContext 構築用の DSL Builder
 */
class ErrorContextBuilder(private val featureName: String, private val operation: String) {
    var tableName: String? = null
    var affectedId: String? = null
    var errorTitleRes: Int? = null
    var errorMessageRes: Int? = null

    fun build() = ErrorContext(
        featureName = featureName,
        operation = operation,
        tableName = tableName,
        affectedId = affectedId,
        errorTitleRes = errorTitleRes,
        errorMessageRes = errorMessageRes
    )
}

/**
 * 例外発生時の具体的振る舞い（ログ出力・UI通知）を定義するインターフェース
 */
interface CoroutineErrorHandler {
    /**
     * 例外を処理する。
     * ログ記録やUI通知の具体的手段は実装クラスに委ねられる。
     */
    suspend fun handleException(e: Throwable, context: ErrorContext)
}

/**
 * Flow 購読のモード
 */
enum class CollectMode {
    /** 初回ロード用：データ受信またはエラーで loadingState を解除する */
    INITIAL,
    /** 継続監視用：原則として loadingState を制御しない（将来のサーバー同期等で使用予定） */
    @Suppress("unused")
    MONITORING
}
