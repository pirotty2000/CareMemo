package jp.mydns.fujiwara.carememo.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod
import net.lingala.zip4j.progress.ProgressMonitor
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Utility：ZipUtils
 *
 * 【役割】
 * Zip4j ライブラリを使用して、パスワード付き Zip 圧縮および解凍を行うユーティリティです。
 * アプリデータのバックアップ（エクスポート）および復元（インポート）の基盤として機能します。
 *
 * 【主な機能】
 * ・パスワード付き圧縮：AES 暗号化を用いたセキュアな Zip ファイル作成。
 * ・解凍処理：パスワード検証を含む一括解凍機能。
 * ・進捗通知：非同期モードによるリアルタイムな処理状況（0-100%）のフィードバック。
 * ・バリデーション：Zip ファイルの暗号化有無、およびパスワードの妥当性チェック。
 *
 * 【全体像：Zip 処理フロー】
 *
 * [圧縮時]
 * 1. Dispatchers.IO への切り替え
 * 2. ZipParameters (AES) の設定
 * 3. 非同期モード起動 (isRunInThread = true)
 * 4. ファイル/フォルダの追加 ➔ 完了待機 (ProgressMonitor 監視)
 *
 * [解凍時]
 * 1. Dispatchers.IO への切り替え
 * 2. パスワード設定（必要な場合）
 * 3. 全ファイル抽出 ➔ 完了待機 (ProgressMonitor 監視)
 *
 * 【このコンポーネントでは行わないこと】
 * ・UI スレッドでの直接実行（必ず coroutine で呼び出すこと）。
 */
object ZipUtils {

    /**
     * 指定されたファイルやフォルダをZip圧縮する。
     * @param files 圧縮対象のファイルリスト
     * @param zipFile 出力先Zipファイル
     * @param password パスワード（nullまたは空文字でパスワードなし）
     * @param onProgress 進捗通知コールバック (0-100)
     * @throws IOException 圧縮に失敗した場合
     */
    suspend fun zip(
        files: List<File>,
        zipFile: File,
        password: String? = null,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val zip = ZipFile(zipFile)
        val isEncrypted = !password.isNullOrEmpty()

        val parameters = ZipParameters().apply {
            if (isEncrypted) {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
            }
        }

        if (isEncrypted) {
            zip.setPassword(password.toCharArray())
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

    /**
     * Zipファイルを指定されたディレクトリに解凍する。
     * @param zipFile 解凍元Zipファイル
     * @param targetDir 解凍先ディレクトリ
     * @param password パスワード
     * @param onProgress 進捗通知コールバック (0-100)
     * @throws IOException 解凍に失敗した場合
     */
    suspend fun unzip(
        zipFile: File,
        targetDir: File,
        password: String? = null,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val zip = ZipFile(zipFile)
        if (zip.isEncrypted && !password.isNullOrEmpty()) {
            zip.setPassword(password.toCharArray())
        }

        zip.isRunInThread = true
        zip.extractAll(targetDir.absolutePath)
        
        waitForCompletion(zip, onProgress)
    }

    /**
     * Zipファイルがパスワード保護されているか確認する。
     */
    suspend fun isEncrypted(zipFile: File): Boolean = withContext(Dispatchers.IO) {
        ZipFile(zipFile).isEncrypted
    }

    /**
     * パスワードが正しいか確認する。
     * @throws IOException ファイルが見つからない、またはアクセスできない場合
     */
    suspend fun isValidPassword(zipFile: File, password: String): Boolean = withContext(Dispatchers.IO) {
        if (!zipFile.exists()) throw FileNotFoundException("Zipファイルが見つかりません。")

        try {
            val zip = ZipFile(zipFile)
            if (!zip.isEncrypted) return@withContext true

            zip.setPassword(password.toCharArray())
            val firstFileHeader = zip.fileHeaders.find { !it.isDirectory }
            if (firstFileHeader != null) {
                zip.getInputStream(firstFileHeader).use { it.read() }
                true
            } else {
                zip.fileHeaders.isNotEmpty()
            }
        } catch (e: ZipException) {
            // パスワード相違に起因する例外の場合は false、それ以外の致命的なエラーは再スロー
            if (e.message?.contains("password", ignoreCase = true) == true || 
                e.message?.contains("WRONG_PASSWORD", ignoreCase = true) == true) {
                false
            } else {
                throw IOException("Zipファイルの検証中にエラーが発生しました。", e)
            }
        } catch (e: Exception) {
            throw IOException("Zipファイルの読み込みに失敗しました。", e)
        }
    }

    /**
     * Zip4jのProgressMonitorを監視し、タスクの完了を待機する。
     */
    private suspend fun waitForCompletion(zip: ZipFile, onProgress: (Int) -> Unit) {
        val monitor = zip.progressMonitor
        while (monitor.state == ProgressMonitor.State.BUSY) {
            onProgress(monitor.percentDone)
            delay(100.milliseconds) // 100ms間隔でポーリング
        }

        // エラーが発生した場合は例外をスローして runCatching に捕捉させる
        if (monitor.result == ProgressMonitor.Result.ERROR) {
            throw monitor.exception ?: Exception("Zip operation failed with unknown error")
        }
        
        // 最終的な完了通知
        onProgress(100)
    }
}
