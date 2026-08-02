@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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

    @Test
    fun `insertHeightAndWeightを実行したとき、DAOのinsertとログ出力が行われること`() = runTest {
        val record = HeightAndWeight(id = "100", personId = "1", height = 170.0, weight = 60.0, recordTime = Instant.now())
        coEvery { heightAndWeightDao.insert(any()) } returns 1L

        val resultId = repository.insertHeightAndWeight(record, "画面", "保存")

        assertEquals("100", resultId)
        coVerify { heightAndWeightDao.insert(match { it.id == "100" && it.personId == "1" && it.height == 170.0 }) }
        coVerify {
            auditLogRepository.log(
                featureName = "画面",
                operation = "保存",
                tableName = "height_and_weight_db",
                actionType = "INSERT",
                affectedId = "100",
                details = match { it.contains("PersonId: 1") },
                resultType = "SUCCESS"
            )
        }
    }

    @Test
    fun `insertBpAndPulseを実行したとき、DAOのinsertとログ出力が行われること`() = runTest {
        val record = BpAndPulse(id = "200", personId = "1", bpSystolic = 120, bpDiastolic = 80, recordTime = Instant.now())
        coEvery { bpAndPulseDao.insert(any()) } returns 2L

        val resultId = repository.insertBpAndPulse(record, "画面", "保存")

        assertEquals("200", resultId)
        coVerify { bpAndPulseDao.insert(match { it.id == "200" && it.personId == "1" && it.bpSystolic == 120 }) }
        coVerify {
            auditLogRepository.log(
                featureName = "画面",
                operation = "保存",
                tableName = "bp_and_pulse_db",
                actionType = "INSERT",
                affectedId = "200",
                details = match { it.contains("PersonId: 1") },
                resultType = "SUCCESS"
            )
        }
    }

    @Test
    fun `insertGlucoseAndHbA1cを実行したとき、DAOのinsertとログ出力が行われること`() = runTest {
        val record = GlucoseAndHbA1c(id = "300", personId = "1", glucose = 100, recordTime = Instant.now())
        coEvery { glucoseAndHbA1cDao.insert(any()) } returns 3L

        val resultId = repository.insertGlucoseAndHbA1c(record, "画面", "保存")

        assertEquals("300", resultId)
        coVerify { glucoseAndHbA1cDao.insert(match { it.id == "300" && it.personId == "1" && it.glucose == 100 }) }
        coVerify {
            auditLogRepository.log(
                featureName = "画面",
                operation = "保存",
                tableName = "glucose_and_hba1c_db",
                actionType = "INSERT",
                affectedId = "300",
                details = match { it.contains("PersonId: 1") },
                resultType = "SUCCESS"
            )
        }
    }
}
