@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.data.MedicationRecordDao
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class MedicationRepositoryTest {

    private val medicationRecordDao = mockk<MedicationRecordDao>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private lateinit var repository: MedicationRepository

    @Before
    fun setup() {
        repository = MedicationRepository(medicationRecordDao, auditLogRepository)
    }

    @Test
    fun `insertMedicationRecordを実行したとき、DAOのinsertとログ出力が行われること`() = runTest {
        val record = MedicationRecord(
            id = 0,
            personId = 1,
            dosageDate = "2023-10-27",
            timeSlot = 0,
            status = 2,
            recordTime = Instant.now()
        )
        coEvery { medicationRecordDao.insert(record) } returns 500L

        repository.insertMedicationRecord(record, "画面", "服薬登録")

        coVerify { medicationRecordDao.insert(record) }
        coVerify {
            auditLogRepository.log(
                featureName = "画面",
                operation = "服薬登録",
                tableName = "medication_record_db",
                actionType = "INSERT",
                affectedId = "500",
                details = match { it.contains("PersonId: 1") && it.contains("Date: 2023-10-27") }
            )
        }
    }

    @Test
    fun `deleteMedicationRecordを実行したとき、DAOのdeleteとログ出力が行われること`() = runTest {
        val record = MedicationRecord(
            id = 500,
            personId = 1,
            dosageDate = "2023-10-27",
            timeSlot = 0,
            status = 2,
            recordTime = Instant.now()
        )
        
        repository.deleteMedicationRecord(record, "画面", "削除")

        coVerify { medicationRecordDao.delete(record) }
        coVerify {
            auditLogRepository.log(
                featureName = "画面",
                operation = "削除",
                tableName = "medication_record_db",
                actionType = "DELETE",
                affectedId = "500",
                details = match { it.contains("PersonId: 1") }
            )
        }
    }
}
