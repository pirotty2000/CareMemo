@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PersonHealthViewModelTest {

    private val healthRepository = mockk<HealthRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)

    private lateinit var viewModel: PersonHealthViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testPerson = Person(
        id = 1,
        lastName = "健康",
        firstName = "太郎",
        lastNameFurigana = "けんこう",
        firstNameFurigana = "たろう",
        birthday = Instant.now()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        coEvery { healthRepository.findHeightAndWeightAtTime(any(), any()) } returns null
        coEvery { healthRepository.findBpAndPulseAtTime(any(), any()) } returns null
        coEvery { healthRepository.findGlucoseAndHbA1cAtTime(any(), any()) } returns null
        
        viewModel = PersonHealthViewModel(
            healthRepository,
            personRepository,
            summaryRepository,
            userSettingsRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setCategoryを実行したとき、対応するRepositoryのデータが取得されること`() = runTest {
        val hwList = listOf(HeightAndWeight(id = 1, personId = 1, height = 170.0, weight = 60.0, recordTime = Instant.now()))
        val bpList = listOf(BpAndPulse(id = 2, personId = 1, bpSystolic = 120, bpDiastolic = 80, pulse = 70, recordTime = Instant.now()))
        
        every { healthRepository.getHeightAndWeightByPersonId(1) } returns flowOf(hwList)
        every { healthRepository.getBpAndPulseByPersonId(1) } returns flowOf(bpList)

        viewModel.loadPerson(1)

        // 身長体重
        viewModel.setCategory(Category.HEIGHT_AND_WEIGHT)
        viewModel.records.test {
            assertEquals(hwList, awaitItem())
        }

        // バイタル
        viewModel.setCategory(Category.BP_AND_PULSE)
        viewModel.records.test {
            assertEquals(bpList, awaitItem())
        }
    }

    @Test
    fun `saveRecordでHeightAndWeightを渡したとき、insertHeightAndWeightが呼ばれること`() = runTest {
        val record = HeightAndWeight(id = 0, personId = 1, height = 170.0, weight = 60.0, recordTime = Instant.now())
        viewModel.saveRecord(record)
        coVerify { healthRepository.insertHeightAndWeight(record, any(), any()) }
    }

    @Test
    fun `saveRecordでBpAndPulseを渡したとき、insertBpAndPulseが呼ばれること`() = runTest {
        val record = BpAndPulse(id = 0, personId = 1, bpSystolic = 120, bpDiastolic = 80, pulse = 70, recordTime = Instant.now())
        viewModel.saveRecord(record)
        coVerify { healthRepository.insertBpAndPulse(record, any(), any()) }
    }

    @Test
    fun `saveRecordでGlucoseAndHbA1cを渡したとき、insertGlucoseAndHbA1cが呼ばれること`() = runTest {
        val record = GlucoseAndHbA1c(id = 0, personId = 1, glucose = 100, hba1c = 5.5, recordTime = Instant.now())
        viewModel.saveRecord(record)
        coVerify { healthRepository.insertGlucoseAndHbA1c(record, any(), any()) }
    }

    @Test
    fun `deleteRecordを実行したとき、対応するRepositoryの削除メソッドが呼ばれること`() = runTest {
        val record = HeightAndWeight(id = 1, personId = 1, height = 170.0, weight = 60.0, recordTime = Instant.now())
        viewModel.deleteRecord(record)
        coVerify { healthRepository.deleteHeightAndWeight(record, any(), any()) }
    }
}
