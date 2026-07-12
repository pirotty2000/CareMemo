package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository

/**
 * ViewModel における例外発生時の標準的な振る舞い（監査ログ記録・UI通知）を実装したハンドラ。
 */
class ViewModelCoroutineErrorHandler(
    private val auditLogRepository: AuditLogRepository,
    private val showError: (titleRes: Int, messageRes: Int, args: Array<out Any>) -> Unit
) : CoroutineErrorHandler {

    override suspend fun handleException(e: Throwable, context: ErrorContext) {
        // 1. Logcat への出力
        Log.e(context.featureName, "Error in ${context.operation}", e)

        // 2. 監査ログへの記録（AuditLogRepository 内部で NonCancellable 保護されている）
        auditLogRepository.log(
            featureName = context.featureName,
            operation = context.operation,
            tableName = context.tableName ?: "unknown",
            actionType = "ERROR",
            affectedId = context.affectedId ?: "0",
            details = e.toString()
        )

        // 3. UI 通知（Error 系の場合はベストエフォート）
        try {
            val titleRes = context.errorTitleRes ?: R.string.common_error_title_error
            val messageRes = context.errorMessageRes ?: R.string.common_error_unknown
            
            // 例外メッセージを引数として渡す（リソース側で %s 等が定義されていることを期待）
            showError(titleRes, messageRes, arrayOf(e.localizedMessage ?: ""))
        } catch (t: Throwable) {
            // UI通知自体の失敗（OOM等）はログに残せない可能性があるが、
            // ハンドラ自体で例外を投げないようにガードする
            if (t is Error) throw t
        }
    }
}
