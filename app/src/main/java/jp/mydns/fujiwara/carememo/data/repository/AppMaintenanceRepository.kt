package jp.mydns.fujiwara.carememo.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import jp.mydns.fujiwara.carememo.BuildConfig
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.logic.common.MedicationLogic
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * システムメンテナンス（バックアップ、リストア、全消去）を担当するリポジトリ
 */
class AppMaintenanceRepository(
    private val database: AppDatabase,
    private val personDao: PersonDao,
    private val heightAndWeightDao: HeightAndWeightDao,
    private val bpAndPulseDao: BpAndPulseDao,
    private val glucoseAndHbA1cDao: GlucoseAndHbA1cDao,
    private val conditionAtVisitDao: ConditionAtVisitDao,
    private val conditionPhotoDao: ConditionPhotoDao,
    private val medicationRecordDao: MedicationRecordDao,
    private val auditLogDao: AuditLogDao,
) {
    suspend fun getBackupData(): CareMemoBackup {
        return CareMemoBackup(
            appVersionCode = BuildConfig.VERSION_CODE,
            persons = personDao.getAllRaw(),
            heightAndWeights = heightAndWeightDao.getAllRaw(),
            bpAndPulses = bpAndPulseDao.getAllRaw(),
            glucoseAndHbA1cs = glucoseAndHbA1cDao.getAllRaw(),
            conditionAtVisits = conditionAtVisitDao.getAllRaw(),
            conditionPhotos = conditionPhotoDao.getAllRaw(),
            medicationRecords = medicationRecordDao.getAllRaw(),
        )
    }

    suspend fun replaceAllData(backup: CareMemoBackup) {
        database.withTransaction {
            // バックアップからの復元時、操作ログは保持したまま臨床データのみを差し替える
            clearClinicalData()
            
            personDao.insertAll(backup.persons)
            heightAndWeightDao.insertAll(backup.heightAndWeights)
            bpAndPulseDao.insertAll(backup.bpAndPulses)
            glucoseAndHbA1cDao.insertAll(backup.glucoseAndHbA1cs)
            conditionAtVisitDao.insertAll(backup.conditionAtVisits)
            conditionPhotoDao.insertAll(backup.conditionPhotos)

            // 服薬記録のインポート（クレンジングを Logic へ委譲）
            val validMedicationRecords = MedicationLogic.filterValidRecords(backup.medicationRecords)
            medicationRecordDao.insertAll(validMedicationRecords)
        }
    }

    /**
     * アプリ内のすべてのデータを消去します（監査ログを含む）
     */
    suspend fun clearAllData() {
        database.withTransaction {
            auditLogDao.deleteAll()
            clearClinicalData()
        }
    }

    /**
     * 利用者情報およびすべての臨床記録を消去します（監査ログは保持）
     */
    private suspend fun clearClinicalData() {
        medicationRecordDao.deleteAll()
        conditionPhotoDao.deleteAll()
        conditionAtVisitDao.deleteAll()
        glucoseAndHbA1cDao.deleteAll()
        bpAndPulseDao.deleteAll()
        heightAndWeightDao.deleteAll()
        personDao.deleteAll()
    }

    /**
     * データベースの不整合（孤立レコード）をスキャンします。
     */
    suspend fun scanInconsistencies(): List<DatabaseInconsistency> {
        val result = mutableListOf<DatabaseInconsistency>()

        // 各テーブルから親のいないレコードを取得
        heightAndWeightDao.getOrphanedRecords().forEach {
            result.add(DatabaseInconsistency("height_and_weight_db", it.id, it.personId, it.recordTime, "利用者が存在しない身長・体重データ"))
        }
        bpAndPulseDao.getOrphanedRecords().forEach {
            result.add(DatabaseInconsistency("bp_and_pulse_db", it.id, it.personId, it.recordTime, "利用者が存在しないバイタルデータ"))
        }
        glucoseAndHbA1cDao.getOrphanedRecords().forEach {
            result.add(DatabaseInconsistency("glucose_and_hba1c_db", it.id, it.personId, it.recordTime, "利用者が存在しない血糖値データ"))
        }
        conditionAtVisitDao.getOrphanedRecords().forEach {
            result.add(DatabaseInconsistency("condition_at_visit_db", it.id, it.personId, it.recordTime, "利用者が存在しない所見メモ"))
        }
        medicationRecordDao.getOrphanedRecords().forEach {
            result.add(DatabaseInconsistency("medication_record_db", it.id, it.personId, it.recordTime, "利用者が存在しない服薬記録"))
        }
        conditionPhotoDao.getOrphanedPhotos().forEach {
            result.add(DatabaseInconsistency("condition_photo_db", it.id, null, it.capturedAt, "所見メモが存在しない写真データ"))
        }

        return result
    }

    /**
     * 検出された不整合レコードをすべて物理削除します。
     */
    suspend fun cleanInconsistencies(inconsistencies: List<DatabaseInconsistency>) {
        database.withTransaction {
            inconsistencies.forEach { inc ->
                when (inc.tableName) {
                    "height_and_weight_db" -> heightAndWeightDao.deleteById(inc.recordId)
                    "bp_and_pulse_db" -> bpAndPulseDao.deleteById(inc.recordId)
                    "glucose_and_hba1c_db" -> glucoseAndHbA1cDao.deleteById(inc.recordId)
                    "condition_at_visit_db" -> conditionAtVisitDao.deleteById(inc.recordId)
                    "medication_record_db" -> medicationRecordDao.deleteById(inc.recordId)
                    "condition_photo_db" -> conditionPhotoDao.deleteById(inc.recordId)
                }
            }
        }
    }

    /**
     * 【テスト用】あえて親のいない不整合レコードを挿入します。
     */
    suspend fun insertTestInconsistency() = withContext(Dispatchers.IO) {
        val now = java.time.Instant.now().toEpochMilli()
        val db = database.openHelper.writableDatabase
        
        // PRAGMA foreign_keys はトランザクション内では変更できないため、
        // 明示的な beginTransaction を使わずに実行します。
        try {
            // 一時的に外部キー制約を無効化
            db.execSQL("PRAGMA foreign_keys = OFF")
            
            // 存在しない personId = -999 を使ってレコードを挿入
            db.execSQL(
                "INSERT INTO bp_and_pulse_db (person_id, bp_systolic, bp_diastolic, sat, pulse, record_time, deleted_at) VALUES (-999, 120, 80, 98, 70, ?, NULL)",
                arrayOf(now),
            )
        } finally {
            // 制約を必ず元に戻す
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * アプリ全体のデータをZIP形式でエクスポートします。
     */
    suspend fun exportData(context: Context, uri: Uri, password: String?, onProgress: (Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        val backup = getBackupData()
        val jsonString = json.encodeToString(CareMemoBackup.serializer(), backup)
        
        val tempDir = File(context.cacheDir, "export_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        try {
            // 元々の仕様に合わせて backup.json という名称で出力
            val dataFile = File(tempDir, "backup.json")
            dataFile.writeText(jsonString)
            
            val photosDir = ImageUtils.getPhotosDirPublic(context)
            val filesToZip = mutableListOf(dataFile)
            if (photosDir.exists() && photosDir.listFiles()?.isNotEmpty() == true) {
                filesToZip.add(photosDir)
            }
            
            val tempZipFile = File(context.cacheDir, "export.zip")
            ZipUtils.zip(filesToZip, tempZipFile, password, onProgress)
            
            context.contentResolver.openOutputStream(uri)?.use { output ->
                tempZipFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            tempZipFile.delete()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * ZIP形式のバックアップからデータをインポート（復元）します。
     */
    suspend fun importData(context: Context, uri: Uri, password: String?, onProgress: (Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        val tempZipFile = File(context.cacheDir, "import.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempZipFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("ファイルの読み込みに失敗しました。")
        
        val tempDir = File(context.cacheDir, "import_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        try {
            ZipUtils.unzip(tempZipFile, tempDir, password, onProgress)
            
            // 探索対象のディレクトリ（ZIPの圧縮のされ方によって階層が深くなる場合があるため）
            val searchDirs = mutableListOf(tempDir)
            tempDir.listFiles()?.filter { it.isDirectory }?.let { searchDirs.addAll(it) }

            // 互換性のため backup.json を優先し、なければ data.json を探す
            var dataFile: File? = null
            for (dir in searchDirs) {
                val f = File(dir, "backup.json").takeIf { it.exists() }
                    ?: File(dir, "data.json").takeIf { it.exists() }
                if (f != null) {
                    dataFile = f
                    break
                }
            }
            
            if (dataFile == null) throw IOException("バックアップデータ(backup.json)が見つかりません。")
            
            val jsonString = dataFile.readText()
            val backup = try {
                json.decodeFromString(CareMemoBackup.serializer(), jsonString)
            } catch (e: Exception) {
                throw IOException("データの解析に失敗しました。ファイルが破損しているか、形式が異なります。", e)
            }
            
            // アプリバージョンの互換性チェック
            if (backup.appVersionCode > BuildConfig.VERSION_CODE) {
                throw IOException("バックアップの作成バージョン(${backup.appVersionCode})が現在のアプリ(${BuildConfig.VERSION_CODE})より新しいため復元できません。")
            }

            // データの置き換え実行
            replaceAllData(backup)
            
            // 写真の差し替え
            val photosDir = ImageUtils.getPhotosDirPublic(context)
            // JSONファイルと同じ階層にある photos ディレクトリを探す
            val importedPhotosDir = File(dataFile.parentFile, AppThresholds.PHOTOS_DIR_NAME)
            
            if (importedPhotosDir.exists()) {
                ImageUtils.clearPhotosDir(context)
                importedPhotosDir.listFiles()?.forEach { file ->
                    if (!file.isDirectory) {
                        file.copyTo(File(photosDir, file.name), overwrite = true)
                    }
                }
            }
        } finally {
            tempZipFile.delete()
            tempDir.deleteRecursively()
        }
    }
}
