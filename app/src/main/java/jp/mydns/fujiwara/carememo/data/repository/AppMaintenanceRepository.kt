package jp.mydns.fujiwara.carememo.data.repository

import androidx.room.withTransaction
import jp.mydns.fujiwara.carememo.data.*

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
    private val medicationRecordDao: MedicationRecordDao
) {
    suspend fun getBackupData(): CareMemoBackup {
        return CareMemoBackup(
            persons = personDao.getAllRaw(),
            heightAndWeights = heightAndWeightDao.getAllRaw(),
            bpAndPulses = bpAndPulseDao.getAllRaw(),
            glucoseAndHbA1cs = glucoseAndHbA1cDao.getAllRaw(),
            conditionAtVisits = conditionAtVisitDao.getAllRaw(),
            conditionPhotos = conditionPhotoDao.getAllRaw(),
            medicationRecords = medicationRecordDao.getAllRaw()
        )
    }

    suspend fun replaceAllData(backup: CareMemoBackup) {
        database.withTransaction {
            clearAllData()
            personDao.insertAll(backup.persons)
            heightAndWeightDao.insertAll(backup.heightAndWeights)
            bpAndPulseDao.insertAll(backup.bpAndPulses)
            glucoseAndHbA1cDao.insertAll(backup.glucoseAndHbA1cs)
            conditionAtVisitDao.insertAll(backup.conditionAtVisits)
            conditionPhotoDao.insertAll(backup.conditionPhotos)
            medicationRecordDao.insertAll(backup.medicationRecords)
        }
    }

    suspend fun clearAllData() {
        database.withTransaction {
            medicationRecordDao.deleteAll()
            conditionPhotoDao.deleteAll()
            conditionAtVisitDao.deleteAll()
            glucoseAndHbA1cDao.deleteAll()
            bpAndPulseDao.deleteAll()
            heightAndWeightDao.deleteAll()
            personDao.deleteAll()
        }
    }
}
