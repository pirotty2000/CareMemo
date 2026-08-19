package jp.mydns.fujiwara.carememo.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import jp.mydns.fujiwara.carememo.BuildConfig
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Repository：AppMaintenanceRepository
 *
 * 【役割】
 * アプリケーションのシステムメンテナンス（データのバックアップ、復元、全消去、および整合性修復）を担当します。
 * データベース全体の操作に加え、ZIP 圧縮・解凍や写真ファイルの物理配置などの副作用を伴う処理をカプセル化します。
 *
 * 【設計指針：レイヤー責務】
 * 1. データアクセスと物理操作の専念：DB 全体の永続化および `ContentResolver` / `cacheDir` を用いたファイル操作に特化します。
 * 2. ロジックの外部委譲：バリデーションやデータ変換（クレンジング）などの業務ルールは本層で持たず、
 *    引数やコールバックを介して Logic レイヤーへ委譲する構造を維持します。
 * 3. 監査ログの記録：(要改善) 現状、主要な操作完了時の成功ログ記録が漏れているため、順次 `AuditLogRepository` による記録を追加する必要があります。
 */
class AppMaintenanceRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val personDao: PersonDao,
    private val heightAndWeightDao: HeightAndWeightDao,
    private val bpAndPulseDao: BpAndPulseDao,
    private val glucoseAndHbA1cDao: GlucoseAndHbA1cDao,
    private val conditionAtVisitDao: ConditionAtVisitDao,
    private val conditionPhotoDao: ConditionPhotoDao,
    private val medicationRecordDao: MedicationRecordDao,
    private val emergencyContactDao: EmergencyContactDao,
    private val auditLogRepository: AuditLogRepository,
) {
    companion object {
        private const val FEATURE_NAME = "AppMaintenance"
    }
    /**
     * 現在の DB 状態からバックアップ用のデータセット（DTO）を生成します。
     *
     * @return アプリ内の全 clinical データを保持する CareMemoBackup オブジェクト
     */
    suspend fun getBackupData(): CareMemoBackup {
        return CareMemoBackup(
            version = 5, // UUID化に伴いバージョンアップ
            appVersionCode = BuildConfig.VERSION_CODE,
            persons = personDao.getAllRaw().map { it.toBackupDto() },
            heightAndWeights = heightAndWeightDao.getAllRaw().map { it.toBackupDto() },
            bpAndPulses = bpAndPulseDao.getAllRaw().map { it.toBackupDto() },
            glucoseAndHbA1cs = glucoseAndHbA1cDao.getAllRaw().map { it.toBackupDto() },
            conditionAtVisits = conditionAtVisitDao.getAllRaw().map { it.toBackupDto() },
            conditionPhotos = conditionPhotoDao.getAllRaw().map { it.toBackupDto() },
            medicationRecords = medicationRecordDao.getAllRaw().map { it.toBackupDto() },
            emergencyContacts = emergencyContactDao.getAllRaw().map { it.toBackupDto() },
        )
    }

    /**
     * バックアップデータをデータベースへ復元します。
     * 既存のすべての臨床データ（監査ログ以外）を消去した上で、バックアップの内容を登録します。
     *
     * 【設計指針】
     * 本メソッドは純粋なデータ永続化のみを担当します。バリデーションやクレンジング等の
     * 業務ロジックは、呼び出し側（ViewModel/Logic層）で事前に行う必要があります。
     *
     * @param backup 復元対象のバックアップデータ
     */
    suspend fun replaceAllData(backup: CareMemoBackup) {
        database.withTransaction {
            // 1. 既存データをクリア
            clearClinicalData()
            
            // 2. DTO から Entity へのマッピング（UUID は維持される）
            val persons = backup.persons.map { it.toEntity() }
            val heightAndWeights = backup.heightAndWeights.map { it.toEntity() }
            val bpAndPulses = backup.bpAndPulses.map { it.toEntity() }
            val glucoseAndHbA1cs = backup.glucoseAndHbA1cs.map { it.toEntity() }
            val conditionAtVisits = backup.conditionAtVisits.map { it.toEntity() }
            val conditionPhotos = backup.conditionPhotos.map { it.toEntity() }
            val medicationRecords = backup.medicationRecords.map { it.toEntity() }
            val emergencyContacts = backup.emergencyContacts.map { it.toEntity() }

            // 3. データの保存
            personDao.insertAll(persons)
            heightAndWeightDao.insertAll(heightAndWeights)
            bpAndPulseDao.insertAll(bpAndPulses)
            glucoseAndHbA1cDao.insertAll(glucoseAndHbA1cs)
            conditionAtVisitDao.insertAll(conditionAtVisits)
            conditionPhotoDao.insertAll(conditionPhotos)
            emergencyContactDao.insertAll(emergencyContacts)
            medicationRecordDao.insertAll(medicationRecords)
        }
        auditLogRepository.log(FEATURE_NAME, "replaceAllData", "all_db", "UPDATE", "0", resultType = "SUCCESS")
    }

    /**
     * アプリ内のすべてのデータを完全に消去します。
     * 臨床記録だけでなく、操作履歴（監査ログ）も対象となります。
     */
    suspend fun clearAllData() {
        database.withTransaction {
            auditLogRepository.deleteAllLogs()
            clearClinicalData()
        }
        auditLogRepository.log(FEATURE_NAME, "clearAllData", "all_db", "DELETE", "0", resultType = "SUCCESS")
    }

    /**
     * 利用者情報およびすべての臨床記録を消去します。
     * 監査ログは保持されます。
     */
    private suspend fun clearClinicalData() {
        emergencyContactDao.deleteAll()
        medicationRecordDao.deleteAll()
        conditionPhotoDao.deleteAll()
        conditionAtVisitDao.deleteAll()
        glucoseAndHbA1cDao.deleteAll()
        bpAndPulseDao.deleteAll()
        heightAndWeightDao.deleteAll()
        personDao.deleteAll()
    }

    /**
     * データベースの不整合（未割り当てレコード）をスキャンします。
     * 外部キー制約がありながら、論理削除等により親が事実上存在しなくなったレコードを特定します。
     *
     * 【制約】
     * 現状、戻り値の `DatabaseInconsistency` に UI リソース ID (`R.string`) が含まれています。
     * これは Repository 層が UI 表現に依存している状態であり、将来的に不整合の種類（Enum 等）のみを返し、
     * 翻訳は上位レイヤーで行う設計への変更が推奨されます。
     *
     * @return 検出された不整合情報のリスト
     */
    suspend fun scanInconsistencies(): List<DatabaseInconsistency> {
        val result = mutableListOf<DatabaseInconsistency>()

        // 各テーブルから親のいない（利用者が存在しない）レコードを DAO 経由で取得
        heightAndWeightDao.getUnassignedRecords().forEach {
            result.add(DatabaseInconsistency("height_and_weight_db", it.id, it.personId, it.recordTime, InconsistencyType.UNASSIGNED_HEIGHT_WEIGHT))
        }
        bpAndPulseDao.getUnassignedRecords().forEach {
            result.add(DatabaseInconsistency("bp_and_pulse_db", it.id, it.personId, it.recordTime, InconsistencyType.UNASSIGNED_VITAL))
        }
        glucoseAndHbA1cDao.getUnassignedRecords().forEach {
            result.add(DatabaseInconsistency("glucose_and_hba1c_db", it.id, it.personId, it.recordTime, InconsistencyType.UNASSIGNED_GLUCOSE))
        }
        conditionAtVisitDao.getUnassignedRecords().forEach {
            result.add(DatabaseInconsistency("condition_at_visit_db", it.id, it.personId, it.recordTime, InconsistencyType.UNASSIGNED_CONDITION))
        }
        medicationRecordDao.getUnassignedRecords().forEach {
            result.add(DatabaseInconsistency("medication_record_db", it.id, it.personId, it.recordTime, InconsistencyType.UNASSIGNED_MEDICATION))
        }
        emergencyContactDao.getUnassignedRecords().forEach {
            result.add(DatabaseInconsistency("emergency_contact_db", it.id, it.personId, it.updatedAt, InconsistencyType.UNASSIGNED_CONTACT))
        }
        conditionPhotoDao.getUnassignedPhotos().forEach {
            result.add(DatabaseInconsistency("condition_photo_db", it.id, null, it.capturedAt, InconsistencyType.UNASSIGNED_PHOTO))
        }

        return result
    }

    /**
     * 検出された不整合レコードを物理削除します。
     *
     * @param inconsistencies 削除対象の不整合情報リスト
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
                    "emergency_contact_db" -> emergencyContactDao.deleteById(inc.recordId)
                    "condition_photo_db" -> conditionPhotoDao.deleteById(inc.recordId)
                }
            }
        }
        auditLogRepository.log(FEATURE_NAME, "cleanInconsistencies", "all_db", "DELETE", "${inconsistencies.size} records", resultType = "SUCCESS")
    }

    /**
     * 【テスト用】意図的に外部キー制約に違反する不整合レコードを挿入します。
     * scanInconsistencies の動作確認に使用します。
     */
    suspend fun insertTestInconsistency() = withContext(Dispatchers.IO) {
        val now = java.time.Instant.now().toEpochMilli()
        val db = database.openHelper.writableDatabase
        
        // PRAGMA foreign_keys はトランザクション内では変更できないため、
        // 明示的な beginTransaction を使わずに実行
        try {
            // 一時的に外部キー制約を無効化
            db.execSQL("PRAGMA foreign_keys = OFF")
            
            // 存在しない personId = 'invalid-uuid' を使ってレコードを挿入
            db.execSQL(
                "INSERT INTO bp_and_pulse_db (id, person_id, bp_systolic, bp_diastolic, sat, pulse, record_time, deleted_at, updated_at, is_synced) VALUES (?, ?, 120, 80, 98, 70, ?, NULL, ?, 0)",
                arrayOf<Any>(UUID.randomUUID().toString(), "invalid-uuid", now, now),
            )
        } finally {
            // 制約を必ず元に戻す
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    /** バックアップ用の JSON コンフィギュレーション */
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
        encodeDefaults = true // デフォルト値も明示的に出力することで将来の構造変更に備える
    }

    /**
     * アプリ全体のデータを ZIP 形式で外部ストレージへエクスポートします。
     *
     * @param uri 保存先の Uri
     * @param password ZIP 圧縮用のパスワード（null の場合はパスワードなし）
     * @param onProgress 進捗状況を通知するコールバック (0-100)
     */
    suspend fun exportData(uri: Uri, password: String?, onProgress: (Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        val backup = getBackupData()
        val jsonString = json.encodeToString(CareMemoBackup.serializer(), backup)
        
        val tempDir = File(context.cacheDir, "export_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        try {
            // JSON データの出力
            val dataFile = File(tempDir, "backup.json")
            dataFile.writeText(jsonString)
            
            // 写真ディレクトリの取得と追加
            val photosDir = ImageUtils.getPhotosDirPublic(context)
            val filesToZip = mutableListOf(dataFile)
            if (photosDir.exists() && photosDir.listFiles()?.isNotEmpty() == true) {
                filesToZip.add(photosDir)
            }
            
            // ZIP アーカイブの生成
            val tempZipFile = File(context.cacheDir, "export.zip")
            ZipUtils.zip(filesToZip, tempZipFile, password, onProgress)
            
            // 外部ストレージへの書き出し
            context.contentResolver.openOutputStream(uri)?.use { output ->
                tempZipFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            tempZipFile.delete()
            auditLogRepository.log(FEATURE_NAME, "exportData", "all_db", "UPDATE", "0", resultType = "SUCCESS")
        } finally {
            // 一時ディレクトリの清掃
            tempDir.deleteRecursively()
        }
    }

    /**
     * 外部の ZIP バックアップからデータを読み込み、内容を検証した上で復元を実行します。
     *
     * 【設計指針】
     * 物理的なファイル操作（解凍、読み込み、写真配置）は本メソッドが担当しますが、
     * バージョン互換性チェックやデータのクレンジングといった業務ロジックは、
     * 引数として渡される `onValidateAndProcess` コールバックを介して外部（ViewModel/Logic層）へ委譲します。
     *
     * @param uri 読み込み元の Uri
     * @param password ZIP 解凍用のパスワード
     * @param onProgress 進捗状況を通知するコールバック (0-100)
     * @param onValidateAndProcess バックアップデータ読み込み直後に実行されるバリデーション・加工処理。
     *                             null を返した場合はインポート処理を中断します。
     * @throws IOException ファイル読み込み失敗、解析失敗、またはバリデーションエラー時にスロー
     */
    suspend fun importData(
        uri: Uri,
        password: String?,
        onProgress: (Int) -> Unit = {},
        onValidateAndProcess: (CareMemoBackup) -> CareMemoBackup?
    ) = withContext(Dispatchers.IO) {
        // 1. Uri から一時ファイルへ ZIP をコピー
        val tempZipFile = File(context.cacheDir, "import.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempZipFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("FILE_READ_ERROR")
        
        val tempDir = File(context.cacheDir, "import_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        try {
            // 2. ZIP の解凍
            ZipUtils.unzip(tempZipFile, tempDir, password, onProgress)
            
            // 3. データの探索
            val searchDirs = mutableListOf(tempDir)
            tempDir.listFiles()?.filter { it.isDirectory }?.let { searchDirs.addAll(it) }

            var dataFile: File? = null
            for (dir in searchDirs) {
                val f = File(dir, "backup.json").takeIf { it.exists() }
                    ?: File(dir, "data.json").takeIf { it.exists() }
                if (f != null) {
                    dataFile = f
                    break
                }
            }
            
            if (dataFile == null) throw IOException("NO_JSON_FILE")
            
            // 4. JSON のパース
            val jsonString = dataFile.readText()
            val rawBackup = try {
                json.decodeFromString(CareMemoBackup.serializer(), jsonString)
            } catch (e: Exception) {
                throw IOException("JSON_PARSE_ERROR", e)
            }
            
            // 5. バリデーションとクレンジングの委譲（Logic レイヤーの呼び出し）
            val processedBackup = onValidateAndProcess(rawBackup) 
                ?: return@withContext // バリデーション失敗時は中断

            // 6. DB データの全置換実行
            replaceAllData(processedBackup)
            
            // 7. 写真ファイルの差し替え
            val photosDir = ImageUtils.getPhotosDirPublic(context)
            val importedPhotosDir = File(dataFile.parentFile, AppSpecifications.Condition.Photo.DIR_NAME)
            
            if (importedPhotosDir.exists()) {
                ImageUtils.clearPhotosDir(context)
                importedPhotosDir.listFiles()?.forEach { file ->
                    if (!file.isDirectory) {
                        file.copyTo(File(photosDir, file.name), overwrite = true)
                    }
                }
            }
            auditLogRepository.log(FEATURE_NAME, "importData", "all_db", "UPDATE", "0", resultType = "SUCCESS")
        } finally {
            tempZipFile.delete()
            tempDir.deleteRecursively()
        }
    }
}
