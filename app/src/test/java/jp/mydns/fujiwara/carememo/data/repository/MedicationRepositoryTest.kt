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

/**
 * Unit Test: MedicationRepository
 */
class MedicationRepositoryTest {

    private val medicationRecordDao = mockk<MedicationRecordDao>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private lateinit var repository: MedicationRepository

    @Before
    fun setup() {
        repository = MedicationRepository(medicationRecordDao, auditLogRepository)
    }

    // region 2. 服薬記録操作テスト (CRUD)

    @Test
    fun MED_01_insertMedicationRecord_new() = runTest {
        val record = createSampleRecord("500")
        coEvery { medicationRecordDao.insert(any()) } returns 1L

        repository.insertMedicationRecord(record, "Feature", "Op", isUpdate = false)

        coVerify { medicationRecordDao.insert(match { it.id == "500" && it.dosageDate == "2023-11-01" }) }
        coVerify { 
            auditLogRepository.log(
                featureName = "Feature",
                operation = "Op",
                tableName = "medication_record_db",
                actionType = "INSERT",
                affectedId = "500",
                details = match { 
                    it.contains("Date: 2023-11-01") && it.contains("Slot: 0") && it.contains("Status: 2")
                },
                resultType = "SUCCESS"
            ) 
        }
    }

    @Test
    fun MED_02_insertMedicationRecord_update() = runTest {
        val record = createSampleRecord("500")
        repository.insertMedicationRecord(record, "Feature", "Op", isUpdate = true)

        coVerify { 
            auditLogRepository.log(any(), any(), any(), "UPDATE", "500", any(), "SUCCESS") 
        }
    }

    @Test
    fun MED_03_deleteMedicationRecord() = runTest {
        val record = createSampleRecord("500")
        repository.deleteMedicationRecord(record, "Feature", "Op")

        coVerify { medicationRecordDao.delete(record) }
        coVerify { 
            auditLogRepository.log(any(), any(), any(), "DELETE", "500", any(), "SUCCESS") 
        }
    }

    // endregion

    // region 3. データ取得テスト (Query)

    @Test
    fun GET_01_getMedicationRecords() = runTest {
        repository.getMedicationRecords("u1")
        coVerify { medicationRecordDao.getByPersonId("u1") }
    }

    @Test
    fun GET_02_getMedicationRecordsByMonth() = runTest {
        repository.getMedicationRecordsByMonth("u1", "2023-11")
        coVerify { medicationRecordDao.getByMonth("u1", "2023-11") }
    }

    // endregion

    private fun createSampleRecord(id: String) = MedicationRecord(
        id = id,
        personId = "u1",
        dosageDate = "2023-11-01",
        timeSlot = 0,
        status = 2,
        recordTime = Instant.now()
    )
}
