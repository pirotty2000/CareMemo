package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.AuditLog
import jp.mydns.fujiwara.carememo.data.AuditLogDao
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Repository：AuditLogRepository
 *
 * 【役割】
 * アプリケーション内で行われた重要な操作（データの追加、変更、削除、インポート等）の履歴（監査ログ）を管理します。
 * セキュリティおよび証跡管理のためのデータ永続化を担当します。
 *
 * 【主な機能】
 * ・操作ログの記録（非同期・キャンセル不可実行）。
 * ・全ログおよびログ件数の取得（監視用 Flow）。
 * ・保持期間に基づいた古いログの自動・手動削除（ローテーション）。
 * ・ログの全件物理削除。
 *
 * 【設計指針】
 * 1. 堅牢性：ログの記録処理は `NonCancellable` コンテキストで実行し、画面遷移やバックボタンによる
 *    コルーチンのキャンセルに影響されず、確実に書き込みを完了させる。
 * 2. 非干渉：ログ記録中の例外は内部でキャッチし、ログの失敗が本来の業務処理（保存等）を中断させないようにする。
 * 3. 効率性：ログ件数の監視には Flow を使用し、UI側でリアルタイムに件数表示を行えるようにする。
 */
class AuditLogRepository(
    private val auditLogDao: AuditLogDao
) {
    /**
     * 保存されているすべてのログを、日時の降順（新しい順）で取得するための Flow です。
     */
    val allLogs: Flow<List<AuditLog>> get() = auditLogDao.getAllLogs()

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
            } catch (_: Exception) {
                // ログ記録の失敗は業務処理を中断させないよう、例外をキャッチする。
                // ログ自体の失敗によりアプリがクラッシュまたは中断することを防ぐ。
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
     */
    suspend fun deleteAllLogs() = auditLogDao.deleteAll()
}
