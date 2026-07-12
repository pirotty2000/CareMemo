package jp.mydns.fujiwara.carememo.viewmodel

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
    /** 継続監視用：原則として loadingState を制御しない */
    MONITORING
}
