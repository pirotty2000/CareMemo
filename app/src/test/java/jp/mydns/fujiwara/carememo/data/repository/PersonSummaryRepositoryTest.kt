@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.data.repository

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.BpAndPulseDao
import jp.mydns.fujiwara.carememo.data.ConditionAtVisitDao
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1cDao
import jp.mydns.fujiwara.carememo.data.HeightAndWeightDao
import jp.mydns.fujiwara.carememo.data.MedicationRecordDao
import jp.mydns.fujiwara.carememo.data.PersonDao
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PersonSummaryRepositoryTest {

    private val personDao = mockk<PersonDao>()
    private val heightAndWeightDao = mockk<HeightAndWeightDao>()
    private val bpAndPulseDao = mockk<BpAndPulseDao>()
    private val glucoseAndHbA1cDao = mockk<GlucoseAndHbA1cDao>()
    private val conditionAtVisitDao = mockk<ConditionAtVisitDao>()
    private val medicationRecordDao = mockk<MedicationRecordDao>()
    
    private lateinit var repository: PersonSummaryRepository

    @Before
    fun setup() {
        repository = PersonSummaryRepository(
            personDao,
            heightAndWeightDao,
            bpAndPulseDao,
            glucoseAndHbA1cDao,
            conditionAtVisitDao,
            medicationRecordDao
        )
    }

    @Test
    fun `getPersonCategorySummaryByIdを実行したとき、各DAOの記録有無が統合されること`() = runTest {
        val personId = "1"
        every { heightAndWeightDao.hasDataForPerson(personId) } returns flowOf(true)
        every { bpAndPulseDao.hasDataForPerson(personId) } returns flowOf(false)
        every { glucoseAndHbA1cDao.hasDataForPerson(personId) } returns flowOf(true)
        every { conditionAtVisitDao.hasDataForPerson(personId) } returns flowOf(false)
        every { medicationRecordDao.hasDataForPerson(personId) } returns flowOf(true)

        repository.getPersonCategorySummaryById(personId).test {
            val summary = awaitItem()
            assertEquals(true, summary.hasHeightWeight)
            assertEquals(false, summary.hasBpAndPulse)
            assertEquals(true, summary.hasGlucoseAndHbA1c)
            assertEquals(false, summary.hasCondition)
            assertEquals(true, summary.hasMedication)
            awaitComplete()
        }
    }
}
