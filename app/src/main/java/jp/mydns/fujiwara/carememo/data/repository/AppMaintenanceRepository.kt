package jp.mydns.fujiwara.carememo.data.repository

import androidx.room.withTransaction
import jp.mydns.fujiwara.carememo.BuildConfig
import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            
            // 服薬記録のインポート（有効なステータスを持つものだけを保存するガード）
            val validMedicationRecords = backup.medicationRecords.filter { (it.status in (0..2)) }
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
}
