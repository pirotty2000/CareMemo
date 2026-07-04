package jp.mydns.fujiwara.carememo.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod
import net.lingala.zip4j.progress.ProgressMonitor
import java.io.File

/**
 * Zip4jを使用して、パスワード付きZip圧縮・解凍を行うユーティリティ。
 * 全ての重い処理は Dispatchers.IO で実行され、進捗状況の取得に対応している。
 */
object ZipUtils {

    /**
     * 指定されたファイルやフォルダをZip圧縮する。
     * @param files 圧縮対象のファイルリスト
     * @param zipFile 出力先Zipファイル
     * @param password パスワード（nullまたは空文字でパスワードなし）
     * @param onProgress 進捗通知コールバック (0-100)
     * @return 処理結果
     */
    suspend fun zip(
        files: List<File>,
        zipFile: File,
        password: String? = null,
        onProgress: (Int) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val zip = ZipFile(zipFile)
            val isEncrypted = !password.isNullOrEmpty()

            val parameters = ZipParameters().apply {
                if (isEncrypted) {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                }
            }

            if (isEncrypted) {
                zip.setPassword(password!!.toCharArray())
            }

            // 非同期モードを有効にして進捗を監視可能にする
            zip.isRunInThread = true
            
            files.forEach { file ->
                if (file.isDirectory) {
                    zip.addFolder(file, parameters)
                } else {
                    zip.addFile(file, parameters)
                }
                // 各ファイル追加ごとに完了を待機し、進捗を報告する
                waitForCompletion(zip, onProgress)
            }
        }
    }

    /**
     * Zipファイルを指定されたディレクトリに解凍する。
     * @param zipFile 解凍元Zipファイル
     * @param targetDir 解凍先ディレクトリ
     * @param password パスワード
     * @param onProgress 進捗通知コールバック (0-100)
     * @return 処理結果
     */
    suspend fun unzip(
        zipFile: File,
        targetDir: File,
        password: String? = null,
        onProgress: (Int) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val zip = ZipFile(zipFile)
            if (zip.isEncrypted && !password.isNullOrEmpty()) {
                zip.setPassword(password.toCharArray())
            }

            zip.isRunInThread = true
            zip.extractAll(targetDir.absolutePath)
            
            waitForCompletion(zip, onProgress)
        }
    }

    /**
     * Zipファイルがパスワード保護されているか確認する。
     */
    suspend fun isEncrypted(zipFile: File): Boolean = withContext(Dispatchers.IO) {
        ZipFile(zipFile).isEncrypted
    }

    /**
     * パスワードが正しいか確認する。
     */
    suspend fun isValidPassword(zipFile: File, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val zip = ZipFile(zipFile)
            if (!zip.isEncrypted) return@withContext true

            zip.setPassword(password.toCharArray())
            // 中央ディレクトリが暗号化されていない場合、ヘッダー取得だけではパスワードの正否を判定できない。
            // 最初のファイルの内容を1バイト読み取ってみることで、確実に判定する。
            val firstFileHeader = zip.fileHeaders.find { !it.isDirectory }
            if (firstFileHeader != null) {
                zip.getInputStream(firstFileHeader).use { it.read() }
                true
            } else {
                zip.fileHeaders.isNotEmpty()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Zip4jのProgressMonitorを監視し、タスクの完了を待機する。
     */
    private suspend fun waitForCompletion(zip: ZipFile, onProgress: (Int) -> Unit) {
        val monitor = zip.progressMonitor
        while (monitor.state == ProgressMonitor.State.BUSY) {
            onProgress(monitor.percentDone)
            delay(100) // 100ms間隔でポーリング
        }

        // エラーが発生した場合は例外をスローして runCatching に捕捉させる
        if (monitor.result == ProgressMonitor.Result.ERROR) {
            throw monitor.exception ?: Exception("Zip operation failed with unknown error")
        }
        
        // 最終的な完了通知
        onProgress(100)
    }
}
