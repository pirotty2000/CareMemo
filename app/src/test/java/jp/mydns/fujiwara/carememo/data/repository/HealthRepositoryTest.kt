package jp.mydns.fujiwara.carememo.data.repository

import androidx.room.withTransaction
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.AppDatabase
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.BpAndPulseDao
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1cDao
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.HeightAndWeightDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Unit Test: HealthRepository
 */
class HealthRepositoryTest {

    private val database = mockk<AppDatabase>(relaxed = true)
    private val heightAndWeightDao = mockk<HeightAndWeightDao>(relaxed = true)
    private val bpAndPulseDao = mockk<BpAndPulseDao>(relaxed = true)
    private val glucoseAndHbA1cDao = mockk<GlucoseAndHbA1cDao>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private lateinit var repository: HealthRepository

    @Before
    fun setup() {
        repository = HealthRepository(
            database,
            heightAndWeightDao,
            bpAndPulseDao,
            glucoseAndHbA1cDao,
            auditLogRepository
        )
    }

    // region 2. 身長・体重操作テスト (HeightAndWeight)

    @Test
    fun HW_01_insertHeightAndWeight_new() = runTest {
        val record = HeightAndWeight(id = "100", personId = "u1", height = 170.0, weight = 60.0, recordTime = Instant.now())
        coEvery { heightAndWeightDao.insert(any()) } returns 1L

        val resultId = repository.insertHeightAndWeight(record, "Feature", "Op", isUpdate = false)

        assertEquals("100", resultId)
        coVerify { heightAndWeightDao.insert(match { it.id == "100" }) }
        coVerify { auditLogRepository.log(any(), any(), "height_and_weight_db", "INSERT", "100", any(), "SUCCESS") }
    }

    @Test
    fun HW_02_insertHeightAndWeight_update() = runTest {
        val record = HeightAndWeight(id = "100", personId = "u1", height = 171.0, weight = 61.0, recordTime = Instant.now())
        repository.insertHeightAndWeight(record, "Feature", "Op", isUpdate = true)

        coVerify { auditLogRepository.log(any(), any(), "height_and_weight_db", "UPDATE", "100", any(), "SUCCESS") }
    }

    @Test
    fun HW_03_deleteHeightAndWeight() = runTest {
        val record = HeightAndWeight(id = "100", personId = "u1", height = 170.0, weight = 60.0, recordTime = Instant.now())
        repository.deleteHeightAndWeight(record, "F", "O")

        coVerify { heightAndWeightDao.delete(record) }
        coVerify { auditLogRepository.log(any(), any(), "height_and_weight_db", "DELETE", "100", any(), "SUCCESS") }
    }

    // endregion

    // region 3. バイタル操作テスト (BpAndPulse)

    @Test
    fun VT_01_insertBpAndPulse_new() = runTest {
        val record = BpAndPulse(id = "200", personId = "u1", bpSystolic = 120, bpDiastolic = 80, recordTime = Instant.now())
        coEvery { bpAndPulseDao.insert(any()) } returns 1L

        val resultId = repository.insertBpAndPulse(record, "Feature", "Op", isUpdate = false)

        assertEquals("200", resultId)
        coVerify { bpAndPulseDao.insert(match { it.id == "200" }) }
        coVerify { auditLogRepository.log(any(), any(), "bp_and_pulse_db", "INSERT", "200", any(), "SUCCESS") }
    }

    @Test
    fun VT_03_deleteBpAndPulse() = runTest {
        val record = BpAndPulse(id = "200", personId = "u1", bpSystolic = 120, bpDiastolic = 80, recordTime = Instant.now())
        repository.deleteBpAndPulse(record, "F", "O")

        coVerify { bpAndPulseDao.delete(record) }
        coVerify { auditLogRepository.log(any(), any(), "bp_and_pulse_db", "DELETE", "200", any(), "SUCCESS") }
    }

    // endregion

    // region 4. 血糖値・HbA1c操作テスト (GlucoseAndHbA1c)

    @Test
    fun GL_01_insertGlucoseAndHbA1c_new() = runTest {
        val record = GlucoseAndHbA1c(id = "300", personId = "u1", glucose = 100, recordTime = Instant.now())
        coEvery { glucoseAndHbA1cDao.insert(any()) } returns 1L

        val resultId = repository.insertGlucoseAndHbA1c(record, "Feature", "Op", isUpdate = false)

        assertEquals("300", resultId)
        coVerify { glucoseAndHbA1cDao.insert(match { it.id == "300" }) }
        coVerify { auditLogRepository.log(any(), any(), "glucose_and_hba1c_db", "INSERT", "300", any(), "SUCCESS") }
    }

    // endregion

    // region 5. 一括保存テスト (Batch)

    @Test
    fun BAT_01_insertHealthDataBatch() = runTest {
        mockkStatic("androidx.room.RoomDatabaseKt")
        val hw = HeightAndWeight(id = "100", personId = "u1", height = 170.0, weight = 60.0, recordTime = Instant.now())
        val vt = BpAndPulse(id = "200", personId = "u1", bpSystolic = 120, bpDiastolic = 80, recordTime = Instant.now())
        val items = listOf(hw, vt)

        coEvery { database.withTransaction<Any>(any()) } coAnswers {
            val block = it.invocation.args[0] as suspend () -> Any
            block()
        }

        repository.insertHealthDataBatch(items, "Feature", "BatchOp")

        coVerify { heightAndWeightDao.insert(any()) }
        coVerify { bpAndPulseDao.insert(any()) }
        coVerify { auditLogRepository.log(any(), any(), "height_and_weight_db", any(), "100", any(), any()) }
        coVerify { auditLogRepository.log(any(), any(), "bp_and_pulse_db", any(), "200", any(), any()) }
        
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    // endregion
}
