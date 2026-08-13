package jp.mydns.fujiwara.carememo.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import jp.mydns.fujiwara.carememo.CareMemoApplication
import jp.mydns.fujiwara.carememo.data.CareMemoBackup
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * テストシナリオ用のサンプルデータを読み込み、アプリの DB およびファイルシステムへ展開するクラス。
 */
object ScenarioTestDataLoader {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * assets/backup.json からデータを読み込み、DB を完全に置き換えます。
     * また、assets/photos 配下のファイルをアプリの写真ディレクトリへコピーします。
     */
    suspend fun restoreFromBackup() = withContext(Dispatchers.IO) {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CareMemoApplication

        // 1. JSON の読み込み
        val backupJsonString = instrumentationContext.assets.open("backup.json").use { 
            it.bufferedReader().readText()
        }
        val backup = json.decodeFromString(CareMemoBackup.serializer(), backupJsonString)

        // 2. DB データの置換
        appContext.appMaintenanceRepository.replaceAllData(backup)

        // 3. 写真ファイルのコピー
        copyPhotosFromAssets(instrumentationContext, appContext)
    }

    /**
     * テスト用 assets からアプリの内部ストレージへ写真をコピーします。
     */
    private suspend fun copyPhotosFromAssets(instrumentationContext: Context, appContext: Context) = withContext(Dispatchers.IO) {
        val photosDir = ImageUtils.getPhotosDirPublic(appContext)
        // 既存の写真をクリア（クリーンな状態でのテストを保証）
        ImageUtils.clearPhotosDir(appContext)

        try {
            val assetList = instrumentationContext.assets.list("photos") ?: return@withContext
            for (fileName in assetList) {
                instrumentationContext.assets.open("photos/$fileName").use { input ->
                    File(photosDir, fileName).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
