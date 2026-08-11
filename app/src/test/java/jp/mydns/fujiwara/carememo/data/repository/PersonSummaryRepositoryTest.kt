package jp.mydns.fujiwara.carememo.data.repository

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit Test: PersonSummaryRepository
 */
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

    // region 2. 個別サマリー取得テスト (getPersonCategorySummaryById)

    @Test
    fun IND_01_getPersonCategorySummaryById_combinesFlows() = runTest {
        val personId = "u1"
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

    // endregion

    // region 3. 全利用者サマリー取得テスト (getPersonCategorySummaries)

    @Test
    fun ALL_01_getPersonCategorySummaries_associatesById() = runTest {
        val queryResults = listOf(
            PersonSummaryQueryResult("u1", true, false, false, true, false),
            PersonSummaryQueryResult("u2", false, true, true, false, true)
        )
        every { personDao.getPersonCategorySummaries() } returns flowOf(queryResults)

        repository.getPersonCategorySummaries().test {
            val map = awaitItem()
            assertEquals(2, map.size)
            
            val s1 = map["u1"]!!
            assertEquals(true, s1.hasHeightWeight)
            assertEquals(true, s1.hasCondition)
            assertEquals(false, s1.hasBpAndPulse)

            val s2 = map["u2"]!!
            assertEquals(true, s2.hasBpAndPulse)
            assertEquals(true, s2.hasMedication)
            assertEquals(false, s2.hasHeightWeight)
            
            awaitComplete()
        }
    }

    // endregion
}
