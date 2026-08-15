package jp.mydns.fujiwara.carememo.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import jp.mydns.fujiwara.carememo.BuildConfig
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.logic.common.MedicationLogic
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.ZoneOffset
import java.util.UUID

/**
 * Repository：AppMaintenanceRepository
 *
 * 【役割】
 * アプリケーションのシステムメンテナンス（データのバックアップ、復元、全消去、および整合性修復）を担当します。
 *
 * 【設計指針：レイヤー責務】
 * 1. データアクセス専念：システム全般のデータ永続化操作に特化します。
 * 2. 依存方向の管理：現在は Logic レイヤーへの依存が含まれていますが、本来は Repository 層として独立しているべきであり、
 *    将来的なリファクタリング（Logic 層への処理委譲）が推奨されます。
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
    private val auditLogDao: AuditLogDao,
) {
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
     * 処理ステップ：
     * 1. 既存臨床データの全消去。
     * 2. 利用者データのクレンジング（生年月日の正規化と、名前の重複に対する自動救済）。
     * 3. 各健康記録および写真情報のバルクインサート。
     * 4. 服薬記録のバリデーションフィルタリングと保存。
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

            // 3. 利用者データのクレンジング
            val cleansedPersons = cleansePersonData(persons)
            personDao.insertAll(cleansedPersons)

            // 4. 各データの保存
            heightAndWeightDao.insertAll(heightAndWeights)
            bpAndPulseDao.insertAll(bpAndPulses)
            glucoseAndHbA1cDao.insertAll(glucoseAndHbA1cs)
            conditionAtVisitDao.insertAll(conditionAtVisits)
            conditionPhotoDao.insertAll(conditionPhotos)
            emergencyContactDao.insertAll(emergencyContacts)

            // 5. 服薬記録のインポート（クレンジングを Logic へ委譲）
            val validMedicationRecords = MedicationLogic.filterValidRecords(medicationRecords)
            medicationRecordDao.insertAll(validMedicationRecords)
        }
    }

    /**
     * アプリ内のすべてのデータを完全に消去します。
     * 臨床記録だけでなく、操作履歴（監査ログ）も対象となります。
     */
    suspend fun clearAllData() {
        database.withTransaction {
            auditLogDao.deleteAll()
            clearClinicalData()
        }
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
     * 利用者データのクレンジングを行います。
     * 
     * 【処理内容】
     * 1. 生年月日の時分秒を 00:00:00 (UTC) に正規化します。
     * 2. 正規化の結果、SQLite の一意制約（姓, 名, 生年月日, メモ）に違反するデータが発生した場合、
     *    識別用メモ（[識別子:xxxx]）を自動設定してインポートの失敗を防ぎます。
     *
     * @param persons 処理対象の利用者リスト
     * @return クレンジング後の利用者リスト
     */
    private fun cleansePersonData(persons: List<Person>): List<Person> {
        val seen = mutableSetOf<String>()
        return persons.map { p ->
            // 1. 生年月日の正規化 (UTC 00:00:00)
            val normalizedBirthday = p.birthday.atZone(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()

            // 2. ユニーク制約 (姓, 名, 生年月日, メモ) の重複チェック
            var finalNote = p.note
            var key = "${p.lastName}|${p.firstName}|${normalizedBirthday.toEpochMilli()}|$finalNote"

            if (seen.contains(key)) {
                // 重複が発生した場合、救済措置としてメモに短い UUID を付記
                val identifier = UUID.randomUUID().toString().take(4)
                val suffix = context.getString(R.string.common_identifier_suffix, identifier)
                finalNote = if (finalNote.length + suffix.length <= 255) {
                    finalNote + suffix
                } else {
                    // 万が一メモが長すぎる場合は末尾を削って付記
                    finalNote.take(255 - suffix.length) + suffix
                }
                key = "${p.lastName}|${p.firstName}|${normalizedBirthday.toEpochMilli()}|$finalNote"
            }

            seen.add(key)
            p.copy(birthday = normalizedBirthday, note = finalNote)
        }
    }

    /**
     * データベースの不整合（未割り当てレコード）をスキャンします。
     * 外部キー制約がありながら、論理削除等により親が事実上存在しなくなったレコードを特定します。
     *
     * @return 検出された不整合情報のリスト
     */
    suspend fun scanInconsistencies(): List<DatabaseInconsistency> {
        val result = mutableListOf<DatabaseInconsistency>()

        // 各テーブルから親のいない（利用者が存在しない）レコードを DAO 経由で取得
        heightAndWeightDao.getUnassignedRecords().forEach {
            result.add(DatabaseInconsistency("height_and_weight_db", it.id, it.personId, it.recordTime, R.string.maintenance_err_unassigned_height_weight))
        }
        bpAndPulseDao.getUnassignedRecords().forEach {
            result.add(DatabaseInconsistency("bp_and_pulse_db", it.id, it.personId, it.recordTime, R.string.maintenance_err_unassigned_vital))
        }
        glucoseAndHbA1cDao.getUnassignedRecords().forEach {
            result.add(DatabaseInconsistency("glucose_and_hba1c_db", it.id, it.personId, it.recordTime, R.string.maintenance_err_unassigned_glucose))
        }
        conditionAtVisitDao.getUnassignedRecords().forEach {
            result.add(DatabaseInconsistency("condition_at_visit_db", it.id, it.personId, it.recordTime, R.string.maintenance_err_unassigned_condition))
        }
        medicationRecordDao.getUnassignedRecords().forEach {
            result.add(DatabaseInconsistency("medication_record_db", it.id, it.personId, it.recordTime, R.string.maintenance_err_unassigned_medication))
        }
        emergencyContactDao.getUnassignedRecords().forEach {
            result.add(DatabaseInconsistency("emergency_contact_db", it.id, it.personId, it.updatedAt, R.string.maintenance_err_unassigned_contact))
        }
        conditionPhotoDao.getUnassignedPhotos().forEach {
            result.add(DatabaseInconsistency("condition_photo_db", it.id, null, it.capturedAt, R.string.maintenance_err_unassigned_photo))
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
        } finally {
            // 一時ディレクトリの清掃
            tempDir.deleteRecursively()
        }
    }

    /**
     * 外部の ZIP バックアップからデータをインポートし、現在のアプリ状態を復元します。
     *
     * @param uri 読み込み元の Uri
     * @param password ZIP 解凍用のパスワード
     * @param isDeveloperMode 開発者モードが有効か（バージョン不一致時の強制復元に関係）
     * @param onProgress 進捗状況を通知するコールバック (0-100)
     * @throws IOException ファイル読み込み失敗、解析失敗、またはバージョン非互換時にスロー
     */
    suspend fun importData(
        uri: Uri,
        password: String?,
        isDeveloperMode: Boolean = false,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        // 1. Uri から一時ファイルへ ZIP をコピー
        val tempZipFile = File(context.cacheDir, "import.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempZipFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException(context.getString(R.string.maintenance_err_file_read))
        
        val tempDir = File(context.cacheDir, "import_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        try {
            // 2. ZIP の解凍
            ZipUtils.unzip(tempZipFile, tempDir, password, onProgress)
            
            // 3. データの探索（解凍後のディレクトリ構造に対応）
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
            
            if (dataFile == null) throw IOException(context.getString(R.string.maintenance_err_no_json))
            
            // 4. JSON のパースとバージョンチェック
            val jsonString = dataFile.readText()
            val backup = try {
                json.decodeFromString(CareMemoBackup.serializer(), jsonString)
            } catch (e: Exception) {
                throw IOException(context.getString(R.string.maintenance_err_json_parse), e)
            }
            
            val versionResult = jp.mydns.fujiwara.carememo.logic.feature.SettingsLogic.validateVersion(
                backupVersionCode = backup.appVersionCode,
                currentVersionCode = BuildConfig.VERSION_CODE,
                isDeveloperMode = isDeveloperMode
            )
            
            if (versionResult == jp.mydns.fujiwara.carememo.logic.feature.ImportValidationResult.INCOMPATIBLE) {
                throw IOException(context.getString(R.string.maintenance_err_newer_version, backup.appVersionCode, BuildConfig.VERSION_CODE))
            }

            // 5. DB データの全置換実行
            replaceAllData(backup)
            
            // 6. 写真ファイルの差し替え
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
        } finally {
            tempZipFile.delete()
            tempDir.deleteRecursively()
        }
    }
}
