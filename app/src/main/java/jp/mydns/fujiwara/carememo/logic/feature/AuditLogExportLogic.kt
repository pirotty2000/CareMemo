package jp.mydns.fujiwara.carememo.logic.feature

import android.content.Context
import jp.mydns.fujiwara.carememo.data.AuditLog
import jp.mydns.fujiwara.carememo.ui.mapping.toActionLabelRes
import jp.mydns.fujiwara.carememo.ui.mapping.toFeatureLabelRes
import jp.mydns.fujiwara.carememo.ui.mapping.toResultLabelRes
import kotlinx.serialization.json.Json
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Logic: AuditLogExportLogic
 *
 * 【役割】
 * 監査ログを外部出力（CSV / JSON）するためのデータ変換ロジックを司ります。
 * RFC 4180 に準拠した CSV 生成や、構造化された JSON の生成を行います。
 */
object AuditLogExportLogic {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    /**
     * 監査ログのリストを JSON 文字列に変換します。
     */
    fun toJson(logs: List<AuditLog>): String {
        return json.encodeToString(kotlinx.serialization.builtins.ListSerializer(AuditLog.serializer()), logs)
    }

    /**
     * 監査ログのリストを CSV 文字列に変換します。
     * Excel での閲覧を考慮し、BOM 付与の判定（呼び出し元で行う）を前提とした UTF-8 互換形式で生成します。
     * 「生の値(Raw)」と「表示用ラベル(Local)」の両方の列を出力します。
     */
    fun toCsv(context: Context, logs: List<AuditLog>): String {
        val header = listOf(
            "ID", "Timestamp", 
            "Feature (Raw)", "Feature (Local)", 
            "Operation", "Table", 
            "Action (Raw)", "Action (Local)", 
            "AffectedID", 
            "Result (Raw)", "Result (Local)", 
            "Details"
        )
        
        return buildString {
            // ヘッダー
            appendLine(header.joinToString(",") { escapeCsv(it) })
            
            // データ行
            logs.forEach { log ->
                val fRes = log.featureName.toFeatureLabelRes
                val aRes = log.actionType.toActionLabelRes
                val rRes = log.resultType.toResultLabelRes

                val row = listOf(
                    log.id.toString(),
                    dateFormatter.format(log.timestamp),
                    log.featureName,
                    if (fRes != 0) context.getString(fRes) else log.featureName,
                    log.operation,
                    log.tableName,
                    log.actionType,
                    if (aRes != 0) context.getString(aRes) else log.actionType,
                    log.affectedId,
                    log.resultType,
                    if (rRes != 0) context.getString(rRes) else log.resultType,
                    log.details ?: ""
                )
                appendLine(row.joinToString(",") { escapeCsv(it) })
            }
        }
    }

    /**
     * CSV の特殊文字（カンマ、改行、ダブルクォート）をエスケープします。
     */
    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            val escaped = value.replace("\"", "\"\"")
            return "\"$escaped\""
        }
        return value
    }
}
