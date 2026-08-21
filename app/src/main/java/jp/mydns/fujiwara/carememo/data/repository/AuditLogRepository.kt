package jp.mydns.fujiwara.carememo.data.repository

import android.content.Context
import android.util.Log
import jp.mydns.fujiwara.carememo.data.AuditLog
import jp.mydns.fujiwara.carememo.data.AuditLogDao
import jp.mydns.fujiwara.carememo.utils.SystemEmergencyLogger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Repository：AuditLogRepository
 *
 * 【役割】
 * アプリケーション内で行われた重要な操作の履歴（監査ログ）の永続化管理を担当します。
 *
 * 【設計指針：レイヤー責務】
 * 1. データアクセス専念：ログの永続化に特化し、ログ内容の解釈やフィルタリングロジック（Logic 層の責務）は含みません。
 * 2. 非干渉の徹底：業務ロジックの実行を妨げない独立した副作用として動作します。
 */
class AuditLogRepository(
    private val context: Context,
    private val auditLogDao: AuditLogDao
) {
    /**
     * 保存されているすべてのログを、日時の降順（新しい順）で取得するための Flow です。
     */
    val allLogs: Flow<List<AuditLog>> get() = auditLogDao.getAllLogs()

    /**
     * 保存されているすべてのログをリストで取得します。
     *
     * @return 監査ログの全件リスト
     */
    suspend fun allLogsRaw(): List<AuditLog> = auditLogDao.getAllLogsRaw()

    /**
     * ログの総件数を Flow で取得します。
     *
     * @return ログ件数を通知する Flow
     */
    fun getAuditLogCountFlow(): Flow<Int> = allLogs.map { it.size }

    /**
     * 新しい操作ログを記録します。
     * 
     * このメソッドは内部で `NonCancellable` を指定しているため、呼び出し元の
     * `CoroutineScope` がキャンセルされても、ログの書き込みは最後まで試行されます。
     * また、書き込み失敗時の例外は隠蔽され、呼び出し側の処理を妨げません。
     *
     * @param featureName 機能名（例：「利用者管理」「健康記録」）
     * @param operation 操作名（例：「新規登録」「PDF出力」）
     * @param tableName 対象のテーブル名
     * @param actionType 操作種別（INSERT, UPDATE, DELETE, etc.）
     * @param affectedId 影響を受けたデータのID
     * @param details 操作の詳細内容（任意）
     * @param resultType 処理結果（SUCCESS, FAILURE, UNKNOWN）
     */
    suspend fun log(
        featureName: String,
        operation: String,
        tableName: String,
        actionType: String,
        affectedId: String,
        details: String? = null,
        resultType: String = "UNKNOWN"
    ) {
        // NonCancellable を指定し、呼び出し元のキャンセル（画面遷移等）に影響されず
        // ログの書き込みを試行するようにする
        withContext(NonCancellable) {
            try {
                val entry = AuditLog(
                    featureName = featureName,
                    operation = operation,
                    tableName = tableName,
                    actionType = actionType,
                    affectedId = affectedId,
                    details = details,
                    resultType = resultType
                )
                auditLogDao.insert(entry)
            } catch (e: Exception) {
                // 1. Logcat への詳細出力
                Log.e("AuditLogRepository", "CRITICAL: Failed to record audit log. feature=$featureName, op=$operation", e)

                // 2. 緊急用ファイルログへの書き出し
                SystemEmergencyLogger.log(
                    context = context,
                    tag = "AuditLogRepository",
                    message = "Failed to insert audit log: feature=$featureName, operation=$operation, action=$actionType, affectedId=$affectedId, details=$details, result=$resultType",
                    throwable = e
                )
            }
        }
    }

    /**
     * 指定された日数より古いログを物理削除します。
     *
     * @param retentionDays 保持日数。0以上の整数を指定してください。
     */
    suspend fun deleteOldLogs(retentionDays: Int) {
        if (retentionDays < 0) return
        val threshold = Instant.now().minus(java.time.Duration.ofDays(retentionDays.toLong()))
        auditLogDao.deleteOldLogs(threshold)
    }

    /**
     * 全てのログをデータベースから物理削除します。
     * 同時に、緊急用ファイルログも削除します。
     */
    suspend fun deleteAllLogs() {
        auditLogDao.deleteAll()
        SystemEmergencyLogger.deleteLogFile(context)
    }
}
