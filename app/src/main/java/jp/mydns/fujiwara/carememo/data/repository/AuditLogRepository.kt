package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.AuditLog
import jp.mydns.fujiwara.carememo.data.AuditLogDao
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * 監査ログ（操作履歴）を管理するリポジトリ
 */
class AuditLogRepository(
    private val auditLogDao: AuditLogDao
) {
    /**
     * ログを全件取得（新しい順）
     */
    val allLogs: Flow<List<AuditLog>> = auditLogDao.getAllLogs()

    /**
     * ログを記録する
     */
    suspend fun log(
        screenName: String,
        operation: String,
        tableName: String,
        actionType: String,
        affectedId: String,
        details: String? = null
    ) {
        val entry = AuditLog(
            screenName = screenName,
            operation = operation,
            tableName = tableName,
            actionType = actionType,
            affectedId = affectedId,
            details = details
        )
        auditLogDao.insert(entry)
    }

    /**
     * 指定された日数より古いログを削除する
     */
    suspend fun deleteOldLogs(retentionDays: Int) {
        if (retentionDays < 0) return
        val threshold = Instant.now().minus(java.time.Duration.ofDays(retentionDays.toLong()))
        auditLogDao.deleteOldLogs(threshold)
    }

    /**
     * ログの総件数を取得
     */
    suspend fun getLogCount(): Int = auditLogDao.getLogCount()

    /**
     * 全ログを物理削除
     */
    suspend fun deleteAllLogs() = auditLogDao.deleteAll()
}
