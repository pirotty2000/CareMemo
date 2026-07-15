@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * ViewModel層テスト：PersonHealthViewModel (4. ロジック・安全性)
 * 仕様書項目: LG-01 〜 LG-02
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonHealthViewModelTest {

    private val healthRepository = mockk<HealthRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)

    private lateinit var viewModel: PersonHealthViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testPerson = Person(
        id = 1, lastName = "健康", firstName = "太郎",
        lastNameFurigana = "ケンコウ", firstNameFurigana = "タロウ",
        birthday = Instant.now()
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        
        // 重複チェックをパスさせるための初期設定
        coEvery { healthRepository.findBpAndPulseAtTime(any(), any()) } returns null
        coEvery { healthRepository.findHeightAndWeightAtTime(any(), any()) } returns null
        coEvery { healthRepository.findGlucoseAndHbA1cAtTime(any(), any()) } returns null
        
        viewModel = PersonHealthViewModel(
            healthRepository,
            personRepository,
            summaryRepository,
            userSettingsRepository,
            auditLogRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun lg01_data_load_failure_safety() = runTest {
        every { healthRepository.getBpAndPulseByPersonId(any()) } returns flow {
            throw RuntimeException("Load Failure")
        }

        viewModel.loadPerson(1)
        viewModel.setCategory(Category.BP_AND_PULSE)

        viewModel.records.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(false, viewModel.isLoading.value)
        coVerify(exactly = 1) { 
            auditLogRepository.log(
                "PersonHealth",
                "recordsFlow",
                "health_db",
                "ERROR",
                any(),
                any(), // details
                "OTHER_ERROR" // resultType
            ) 
        }
    }

    @Test
    fun lg02_save_failure_safety() = runTest {
        val record = BpAndPulse(id = 0, personId = 1, bpSystolic = 120, bpDiastolic = 80, pulse = 70, recordTime = Instant.now())
        coEvery { healthRepository.insertBpAndPulse(any(), any(), any()) } throws RuntimeException("Save Failure")

        viewModel.saveRecord(record)
        
        // 重要：safeLaunch で起動されたコルーチンの完了（例外ハンドリング含む）を待機
        advanceUntilIdle()

        assertEquals(false, viewModel.isLoading.value)
        coVerify(exactly = 1) { 
            auditLogRepository.log(
                "PersonHealth",
                "saveRecord",
                "health_db",
                "ERROR",
                any(),
                any(),
                "OTHER_ERROR"
            ) 
        }
    }

    @Test
    fun additional_set_category_calls_correct_repository() = runTest {
        viewModel.loadPerson(1)
        
        viewModel.setCategory(Category.HEIGHT_AND_WEIGHT)
        viewModel.records.test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        verify { healthRepository.getHeightAndWeightByPersonId(1) }

        viewModel.setCategory(Category.GLUCOSE_AND_HBA1C)
        viewModel.records.test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        verify { healthRepository.getGlucoseAndHbA1cByPersonId(1) }
    }
}
