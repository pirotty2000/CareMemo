package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
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
import java.time.YearMonth

/**
 * PersonMedicationViewModel (服薬管理) のユニットテスト
 * 
 * 仕様書：doc/test/screen/TEST_SPEC_SCR-PM-001_PersonMedicationScreen.md に準拠
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonMedicationViewModelTest {

    private val medicationRepository = mockk<MedicationRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)

    private lateinit var viewModel: PersonMedicationViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val fixedInstant = Instant.parse("2023-10-27T10:00:00Z")

    private val testPerson = Person(
        id = "1", lastName = "服薬", firstName = "太郎",
        lastNameFurigana = "ふくやく", firstNameFurigana = "たろう",
        birthday = fixedInstant
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        every { summaryRepository.getPersonCategorySummaryById(any()) } returns flowOf(PersonCategorySummary())
        
        viewModel = PersonMedicationViewModel(
            medicationRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun BH_03_nextMonth_updatesMonth() = runTest {
        val initialMonth = viewModel.uiState.value.selectedMonth
        viewModel.nextMonth()
        
        assertEquals(initialMonth.plusMonths(1), viewModel.uiState.value.selectedMonth)
    }

    @Test
    fun LG_01_load_failure_safety() = runTest {
        every { medicationRepository.getMedicationRecordsByMonth(any(), any()) } returns flow {
            throw RuntimeException("Flow Error")
        }
        
        viewModel.loadPerson("1")

        // isLoading が false に戻ること
        assertEquals(false, viewModel.uiState.value.isLoading)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonMedication",
                operation = "monthlyRecordsFlow",
                tableName = "medication_db",
                actionType = "ERROR",
                affectedId = any(),
                details = match { it.contains("Flow Error") },
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    fun LG_02_save_failure_safety() = runTest {
        coEvery { medicationRepository.insertMedicationRecord(any(), any(), any(), any()) } throws RuntimeException("Sync Error")
        
        val date = "2023-10-27"
        val record = MedicationRecord(id = "", personId = "1", dosageDate = date, timeSlot = 0, status = 2, recordTime = fixedInstant)

        viewModel.loadPerson("1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.syncMedicationDay(date, listOf(record, null, null, null))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonMedication",
                operation = "syncMedicationDay",
                tableName = "medication_db",
                actionType = "ERROR",
                affectedId = "1",
                details = match { it.contains("Sync Error") },
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    @Suppress("USELESS_IS_CHECK")
    fun LG_03_month_switch_atomicity() = runTest {
        val initialMonth = YearMonth.now()
        val nextMonth = initialMonth.plusMonths(1)
        
        viewModel.loadPerson("1")
        
        // 月を次へ
        viewModel.nextMonth()
        
        // 即座に UiState の月が更新され、該当月のリポジトリが呼ばれていること
        assertEquals(nextMonth, viewModel.uiState.value.selectedMonth)
        verify { medicationRepository.getMedicationRecordsByMonth("1", nextMonth.toString()) is kotlinx.coroutines.flow.Flow<*> }
    }
}
