package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow

/**
 * (C)系統: 服薬管理のデータ管理を担当するリポジトリ
 */
class MedicationRepository(
    private val medicationRecordDao: MedicationRecordDao
) {
    fun getMedicationRecords(personId: Int): Flow<List<MedicationRecord>> =
        medicationRecordDao.getByPersonId(personId)

    fun getMedicationRecordsByMonth(personId: Int, month: String): Flow<List<MedicationRecord>> =
        medicationRecordDao.getByMonth(personId, month)

    suspend fun insertMedicationRecord(item: MedicationRecord) = medicationRecordDao.insert(item)

    suspend fun deleteMedicationRecord(item: MedicationRecord) = medicationRecordDao.delete(item)
}
