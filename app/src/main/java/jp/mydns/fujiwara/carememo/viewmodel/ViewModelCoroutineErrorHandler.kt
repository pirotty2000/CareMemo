package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import android.database.sqlite.SQLiteException
import java.io.IOException
import kotlinx.serialization.SerializationException
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository

/**
 * Class：ViewModelCoroutineErrorHandler
 *
 * 【役割】
 * ViewModel におけるコルーチン実行中の例外発生時の「標準的な振る舞い」を定義・実装するハンドラです。
 *
 * 【設計指針：レイヤー責務】
 * 1. エラー処理の統合：個別の ViewModel から例外ハンドリングの関心を分離し、ログ記録と UI 通知を一箇所で集中管理します。
 * 2. 証跡の保証：例外発生時には必ず監査ログ (AuditLogRepository) への記録を行い、不具合調査の透明性を確保します。
 *
 * 【このクラスでは行わないこと】
 * ・UI の直接的な描画（引数で渡された showError ラムダを通じて通知を依頼するのみ）。
 */
class ViewModelCoroutineErrorHandler(
    private val auditLogRepository: AuditLogRepository,
    private val showError: (titleRes: Int, messageRes: Int, args: Array<out Any>) -> Unit
) : CoroutineErrorHandler {

    /**
     * 発生した例外をハンドルします。
     *
     * 1. Logcat 出力
     * 2. 例外の解析とカテゴリ分け
     * 3. 監査ログ記録
     * 4. UI へのエラー通知
     *
     * @param e 発生した例外（Throwable）
     * @param context エラーが発生したコンテキスト情報（機能名、操作名、対象テーブル、ID等）
     */
    override suspend fun handleException(e: Throwable, context: ErrorContext) {
        // 1. Logcat への出力：開発・デバッグ時の一次情報として記録
        Log.e(context.featureName, "Error in ${context.operation}", e)

        // 2. 例外の種類に基づいて監査ログ用の resultType を決定
        // AppException の各サブクラスや、標準的な Java/Kotlin 例外を分類する
        val resultType = when (e) {
            is SQLiteException, is AppDataException -> "DB_ERROR"
            is AppIOException, is IOException -> "IO_ERROR"
            is SerializationException -> "FORMAT_ERROR"
            is AppValidationException, is IllegalArgumentException -> "VALIDATION_ERROR"
            is AppSecurityException -> "SECURITY_ERROR"
            is AppExternalException -> "EXTERNAL_ERROR"
            else -> "OTHER_ERROR"
        }

        // 3. 監査ログへの記録
        // AuditLogRepository 内部で NonCancellable なスコープで実行されることが期待される
        auditLogRepository.log(
            featureName = context.featureName,
            operation = context.operation,
            tableName = context.tableName ?: "unknown",
            actionType = "ERROR",
            affectedId = context.affectedId ?: "0",
            details = e.toString(),
            resultType = resultType
        )

        // 4. UI 通知（エラーダイアログの表示）
        // 通知処理自体の失敗（メモリ不足等）でエラー処理が中断されないようガードする
        try {
            val titleRes: Int
            val messageRes: Int
            val errorArgs: Array<out Any>

            // アプリ固有の例外 (AppException) の場合は、例外自体が持つリソース情報を優先する
            if (e is AppException) {
                // タイトル：例外保持値 ＞ コンテキスト保持値 ＞ デフォルト
                titleRes = e.titleResId ?: context.errorTitleRes ?: R.string.common_error_title_error
                // メッセージ：例外保持値 ＞ コンテキスト保持値 ＞ デフォルト
                messageRes = e.messageResId ?: context.errorMessageRes ?: R.string.common_error_unknown
                // 引数：例外の引数リスト ＞ 例外のメッセージ
                errorArgs = if (e.args.isNotEmpty()) e.args.toTypedArray() else arrayOf(e.localizedMessage ?: "")
            } else {
                // システム例外の場合は、コンテキスト情報の指定に従う
                titleRes = context.errorTitleRes ?: R.string.common_error_title_error
                messageRes = context.errorMessageRes ?: R.string.common_error_unknown
                errorArgs = arrayOf(e.localizedMessage ?: "")
            }
            
            // UIレイヤ（ViewModel経由で通知されるSharedFlow等）へエラー表示を依頼
            showError(titleRes, messageRes, errorArgs)
        } catch (t: Throwable) {
            // UI通知自体の失敗（OOM等）はログに残せない可能性があるが、
            // ハンドラ自体で例外を投げないようにガードする。
            // ただし、JVMの致命的な Error (OutOfMemoryError等) は再送出する。
            if (t is Error) throw t
        }
    }
}
