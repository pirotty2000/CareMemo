package jp.mydns.fujiwara.carememo.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Utility: SystemEmergencyLogger
 *
 * 【役割】
 * データベース（監査ログ）への記録が失敗した際、最終的な証跡を残すための「緊急用ファイルログ」を司ります。
 * DB破損やストレージの問題など、通常のログ記録が不可能な場合でも物理ファイルとしてエラーを保持します。
 */
object SystemEmergencyLogger {
    private const val TAG = "SystemEmergencyLogger"
    private const val LOG_FILE_NAME = "audit_emergency.log"
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    /**
     * 緊急ログをファイルに追記します。
     *
     * @param context コンテキスト
     * @param tag 識別タグ
     * @param message ログメッセージ
     * @param throwable 発生した例外（任意）
     */
    fun log(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = formatter.format(Instant.now())
        val logEntry = buildString {
            append("[$timestamp] [$tag] $message")
            throwable?.let {
                append("\n")
                append(Log.getStackTraceString(it))
            }
            append("\n---\n")
        }

        try {
            val logFile = getLogFile(context)
            FileOutputStream(logFile, true).use {
                it.write(logEntry.toByteArray())
            }
            Log.d(TAG, "Successfully wrote emergency log to file.")
        } catch (e: Exception) {
            // ファイルへの書き込みすら失敗した場合は Logcat のみに頼る
            Log.e(TAG, "CRITICAL: Failed to write emergency log to file.", e)
        }
    }

    /**
     * 緊急ログファイルを取得します。
     */
    fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    /**
     * 緊急ログファイルを削除します。
     */
    fun deleteLogFile(context: Context) {
        try {
            val file = getLogFile(context)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Emergency log file deleted.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete emergency log file.", e)
        }
    }

    /**
     * 指定された日数より古い緊急ログファイルを削除します。
     *
     * @param days 保持日数
     */
    fun deleteOldLogs(context: Context, days: Int) {
        try {
            val file = getLogFile(context)
            if (file.exists()) {
                val lastModified = file.lastModified()
                val threshold = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
                if (lastModified < threshold) {
                    deleteLogFile(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate emergency log file.", e)
        }
    }
}
