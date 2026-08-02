package jp.mydns.fujiwara.carememo.viewmodel

/**
 * Class：AppException
 *
 * 【役割】
 * CareMemo アプリにおける「意味のある例外」の基底クラスです。
 * 単なるエラーメッセージだけでなく、UI（ダイアログ等）で表示するためのリソースIDを保持できることが特徴です。
 *
 * 【主な機能】
 * ・ダイアログタイトルおよびメッセージの多言語化対応（Resource ID）。
 * ・動的なメッセージ構築のための引数リスト（args）の保持。
 * ・監査ログへの記録を目的とした詳細なログメッセージの保持。
 *
 * @param titleResId UIに表示するタイトルのリソースID（null の場合はデフォルトを使用）
 * @param messageResId UIに表示するメッセージのリソースID
 * @param args メッセージリソースに埋め込む動的な引数リスト
 * @param logMessage Logcat や監査ログに記録する、開発者向けの非公開メッセージ
 * @param cause 原因となった低レベルの例外（Throwable）
 */
open class AppException(
    val titleResId: Int? = null,
    val messageResId: Int? = null,
    val args: List<Any> = emptyList(),
    logMessage: String,
    cause: Throwable? = null
) : Exception(logMessage, cause)

/**
 * Class：AppValidationException
 *
 * 【役割】
 * バリデーション失敗（入力不備、必須チェック漏れ、論理的な重複など）を表します。
 * 主に `save` メソッド実行前のチェック処理などでスローされます。
 */
class AppValidationException(
    titleResId: Int? = null,
    messageResId: Int? = null,
    args: List<Any> = emptyList(),
    logMessage: String
) : AppException(titleResId, messageResId, args, logMessage)

/**
 * Class：AppIOException
 *
 * 【役割】
 * 入出力（ファイル操作、画像保存、バックアップのエクスポート/インポート等）の失敗を表します。
 * ストレージの空き容量不足や権限エラーなどもこの範疇に含まれます。
 */
class AppIOException(
    titleResId: Int? = null,
    messageResId: Int? = null,
    args: List<Any> = emptyList(),
    logMessage: String,
    cause: Throwable? = null
) : AppException(titleResId, messageResId, args, logMessage, cause)

/**
 * Class：AppDataException
 *
 * 【役割】
 * データベース（SQLite/Room）の操作失敗や、取得したデータの不整合など、データ層の異常を表します。
 */
class AppDataException(
    titleResId: Int? = null,
    messageResId: Int? = null,
    args: List<Any> = emptyList(),
    logMessage: String,
    cause: Throwable? = null
) : AppException(titleResId, messageResId, args, logMessage, cause)

/**
 * Class：AppExternalException
 *
 * 【役割】
 * 外部アプリ連携（インテント呼び出し等）や、ハードウェア機能（カメラ、バイオメトリクス等）の予期せぬエラーを表します。
 */
class AppExternalException(
    titleResId: Int? = null,
    messageResId: Int? = null,
    args: List<Any> = emptyList(),
    logMessage: String,
    cause: Throwable? = null
) : AppException(titleResId, messageResId, args, logMessage, cause)

/**
 * Class：AppSecurityException
 *
 * 【役割】
 * セキュリティに関連するエラー（生体認証の失敗、暗号化データのデコード失敗、権限不足等）を表します。
 */
class AppSecurityException(
    titleResId: Int? = null,
    messageResId: Int? = null,
    args: List<Any> = emptyList(),
    logMessage: String,
    cause: Throwable? = null
) : AppException(titleResId, messageResId, args, logMessage, cause)

/**
 * Data Class：ErrorContext
 *
 * 【役割】
 * 例外が発生した際の「文脈」を保持するイミュータブルなデータクラスです。
 * 監査ログの記録や、UIへのエラー通知時に必要なメタ情報を一括で管理します。
 *
 * @property featureName 機能名（"PersonList", "Settings" 等）
 * @property operation 操作内容（"save", "load" 等）
 * @property tableName 関連する DB テーブル名（省略可）
 * @property affectedId 操作対象となったデータの主キー ID（省略可）
 * @property errorTitleRes UI表示用：デフォルトのタイトルリソースID（省略可）
 * @property errorMessageRes UI表示用：デフォルトのメッセージリソースID（省略可）
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
 * Class：ErrorContextBuilder
 *
 * 【役割】
 * `ErrorContext` を型安全かつ簡潔に構築するための DSL Builder です。
 * `BaseUiStateViewModel` の `safeLaunch` 等で使用されます。
 */
class ErrorContextBuilder(private val featureName: String, private val operation: String) {
    /** 関連するテーブル名 */
    var tableName: String? = null
    /** 影響を受けたデータのID */
    var affectedId: String? = null
    /** エラー発生時の共通タイトルリソース */
    var errorTitleRes: Int? = null
    /** エラー発生時の共通メッセージリソース */
    var errorMessageRes: Int? = null

    /**
     * 現在の設定内容に基づいて ErrorContext を生成します。
     */
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
 * Interface：CoroutineErrorHandler
 *
 * 【役割】
 * コルーチン内で発生した例外の「後処理」を定義するための抽象インターフェースです。
 * 具体的なログ出力（Logcat/DB記録）や UI 通知のロジックは実装クラスに委ねることで、
 * ViewModel 基底クラスからの関心の分離を実現します。
 */
interface CoroutineErrorHandler {
    /**
     * 発生した例外をハンドルします。
     *
     * @param e 発生した例外
     * @param context 例外発生時のコンテキスト情報
     */
    suspend fun handleException(e: Throwable, context: ErrorContext)
}

/**
 * Enum：CollectMode
 *
 * 【役割】
 * `BaseUiStateViewModel.safeCollect` における Flow 購読の振る舞いを定義します。
 */
enum class CollectMode {
    /**
     * 初回ロード用。
     * 購読開始時に `loadingState` を true にし、最初のデータ受信またはエラー発生時に false に戻します。
     */
    INITIAL,
    /**
     * 継続監視用。
     * 原則として `loadingState` の自動制御を行いません。
     * バックグラウンドでのデータ同期など、ユーザーの操作を妨げない購読に使用します。
     */
    @Suppress("unused")
    MONITORING
}
