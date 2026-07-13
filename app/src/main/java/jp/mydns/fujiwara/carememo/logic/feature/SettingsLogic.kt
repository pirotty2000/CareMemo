package jp.mydns.fujiwara.carememo.logic.feature

import android.os.StatFs
import jp.mydns.fujiwara.carememo.data.AuditLog
import java.io.File

/**
 * 設定画面・バックアップ管理に関するドメインロジック。
 */
object SettingsLogic {

    /**
     * 監査ログを条件に応じてフィルタリングおよび並び替えします。
     */
    fun filterAuditLogs(
        logs: List<AuditLog>,
        feature: String?,
        result: String?,
        ascending: Boolean
    ): List<AuditLog> {
        val filtered = logs.filter { log ->
            ((feature == null) || (log.featureName == feature)) &&
                    ((result == null) || (log.resultType == result))
        }
        return if (ascending) filtered.reversed() else filtered
    }

    /**
     * ログリストから存在する機能名の一覧を重複なく抽出します。
     */
    fun extractAvailableFeatures(logs: List<AuditLog>): List<String> {
        return logs.asSequence().map { it.featureName }.distinct().sorted().toList()
    }

    /**
     * ログリストから存在する結果種別の一覧を重複なく抽出します。
     */
    fun extractAvailableResults(logs: List<AuditLog>): List<String> {
        return logs.asSequence().map { it.resultType }.distinct().sorted().toList()
    }

    /**
     * ファイルヘッダーが Zip 形式（マジックナンバー）に合致するか判定します。
     */
    fun isValidZipHeader(header: ByteArray): Boolean {
        if (header.size < 4) return false
        return header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
    }

    /**
     * バックアップデータが現在のアプリバージョンと互換性があるか判定します。
     */
    fun isVersionCompatible(backupVersionCode: Int, currentVersionCode: Int): Boolean {
        return backupVersionCode <= currentVersionCode
    }

    /**
     * 指定されたディレクトリに、要求されたバイト数以上の空き容量があるか判定します。
     */
    fun hasAvailableSpace(dir: File, requiredBytes: Long): Boolean {
        return try {
            val stats = StatFs(dir.absolutePath)
            val available = stats.availableBlocksLong * stats.blockSizeLong
            available > requiredBytes
        } catch (_: Exception) {
            true // 取得に失敗した場合は念のため通すが、通常は失敗しない
        }
    }
}
