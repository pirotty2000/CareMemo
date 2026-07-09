@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.BpAndPulseDao
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1cDao
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.HeightAndWeightDao
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class HealthRepositoryTest {

    private val heightAndWeightDao = mockk<HeightAndWeightDao>(relaxed = true)
    private val bpAndPulseDao = mockk<BpAndPulseDao>(relaxed = true)
    private val glucoseAndHbA1cDao = mockk<GlucoseAndHbA1cDao>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private lateinit var repository: HealthRepository

    @Before
    fun setup() {
        repository = HealthRepository(
            heightAndWeightDao,
            bpAndPulseDao,
            glucoseAndHbA1cDao,
            auditLogRepository
        )
    }

    @Test
    fun `insertHeightAndWeightを実行したとき、DAOのinsertとログ出力が行われること`() = runTest {
        val record = HeightAndWeight(id = 0, personId = 1, height = 170.0, weight = 60.0, recordTime = Instant.now())
        coEvery { heightAndWeightDao.insert(record) } returns 1L

        repository.insertHeightAndWeight(record, "画面", "保存")

        coVerify { heightAndWeightDao.insert(record) }
        coVerify {
            auditLogRepository.log(
                screenName = "画面",
                operation = "保存",
                tableName = "height_and_weight_db",
                actionType = "INSERT",
                affectedId = "1",
                details = match { it.contains("PersonId: 1") }
            )
        }
    }

    @Test
    fun `insertBpAndPulseを実行したとき、DAOのinsertとログ出力が行われること`() = runTest {
        val record = BpAndPulse(id = 0, personId = 1, bpSystolic = 120, bpDiastolic = 80, recordTime = Instant.now())
        coEvery { bpAndPulseDao.insert(record) } returns 2L

        repository.insertBpAndPulse(record, "画面", "保存")

        coVerify { bpAndPulseDao.insert(record) }
        coVerify {
            auditLogRepository.log(
                screenName = "画面",
                operation = "保存",
                tableName = "bp_and_pulse_db",
                actionType = "INSERT",
                affectedId = "2",
                details = match { it.contains("PersonId: 1") }
            )
        }
    }

    @Test
    fun `insertGlucoseAndHbA1cを実行したとき、DAOのinsertとログ出力が行われること`() = runTest {
        val record = GlucoseAndHbA1c(id = 0, personId = 1, glucose = 100, recordTime = Instant.now())
        coEvery { glucoseAndHbA1cDao.insert(record) } returns 3L

        repository.insertGlucoseAndHbA1c(record, "画面", "保存")

        coVerify { glucoseAndHbA1cDao.insert(record) }
        coVerify {
            auditLogRepository.log(
                screenName = "画面",
                operation = "保存",
                tableName = "glucose_and_hba1c_db",
                actionType = "INSERT",
                affectedId = "3",
                details = match { it.contains("PersonId: 1") }
            )
        }
    }
}
