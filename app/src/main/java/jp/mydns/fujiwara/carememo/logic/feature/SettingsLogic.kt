package jp.mydns.fujiwara.carememo.logic.feature

import android.os.StatFs
import java.io.File

/**
 * 設定画面・バックアップ管理に関するドメインロジック。
 */
object SettingsLogic {

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
