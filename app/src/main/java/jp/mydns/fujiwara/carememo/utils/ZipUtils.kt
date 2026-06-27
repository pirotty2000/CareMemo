package jp.mydns.fujiwara.carememo.utils

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File

/**
 * Zip4jを使用して、パスワード付きZip圧縮・解凍を行うユーティリティ
 */
object ZipUtils {

    /**
     * 指定されたファイルやフォルダをZip圧縮する
     * @param files 圧縮対象のファイルリスト
     * @param zipFile 出力先Zipファイル
     * @param password パスワード（nullまたは空文字でパスワードなし）
     */
    fun zip(files: List<File>, zipFile: File, password: String? = null) {
        val zip = ZipFile(zipFile)
        
        val parameters = ZipParameters().apply {
            if (!password.isNullOrEmpty()) {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
            }
        }

        if (!password.isNullOrEmpty()) {
            zip.setPassword(password.toCharArray())
        }

        files.forEach { file ->
            if (file.isDirectory) {
                zip.addFolder(file, parameters)
            } else {
                zip.addFile(file, parameters)
            }
        }
    }

    /**
     * Zipファイルがパスワード保護されているか確認する
     */
    fun isEncrypted(zipFile: File): Boolean {
        return ZipFile(zipFile).isEncrypted
    }

    /**
     * パスワードが正しいか確認する
     */
    fun isValidPassword(zipFile: File, password: String): Boolean {
        return try {
            val zip = ZipFile(zipFile)
            if (!zip.isEncrypted) return true

            zip.setPassword(password.toCharArray())
            // Zip4jでは中央ディレクトリが暗号化されていない場合、
            // ヘッダー取得だけではパスワードの正否を判定できない。
            // 最初のファイルの内容を1バイト読み取ってみることで、パスワードの正否を確実に判定する。
            val firstFileHeader = zip.fileHeaders.find { !it.isDirectory }
            if (firstFileHeader != null) {
                zip.getInputStream(firstFileHeader).use { it.read() }
                true
            } else {
                // ファイルが含まれていない場合はヘッダーが読めるだけで良しとする
                zip.fileHeaders.isNotEmpty()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Zipファイルを指定されたディレクトリに解凍する
     * @param zipFile 解凍元Zipファイル
     * @param targetDir 解凍先ディレクトリ
     * @param password パスワード
     */
    fun unzip(zipFile: File, targetDir: File, password: String? = null) {
        val zip = ZipFile(zipFile)
        if (zip.isEncrypted && !password.isNullOrEmpty()) {
            zip.setPassword(password.toCharArray())
        }
        zip.extractAll(targetDir.absolutePath)
    }
}
