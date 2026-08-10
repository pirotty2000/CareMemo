package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Logic Test: PersonMedicationViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonMedicationViewModelTest {

    private val medicationRepository = mockk<MedicationRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private val personId = "u1"
    private val testPerson = Person(
        id = personId, lastName = "服薬", firstName = "太郎",
        lastNameFurigana = "ふくやく", firstNameFurigana = "たろう",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)

        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        coEvery { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        coEvery { summaryRepository.getPersonCategorySummaryById(any()) } returns flowOf(PersonCategorySummary())
        every { medicationRepository.getMedicationRecordsByMonth(any(), any()) } returns flowOf(emptyList())
        every { medicationRepository.getMedicationRecords(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun createViewModel(): PersonMedicationViewModel {
        return PersonMedicationViewModel(
            medicationRepository, personRepository, summaryRepository, 
            userSettingsRepository, auditLogRepository, 
            SavedStateHandle(mapOf("personId" to personId))
        )
    }

    // region 2. 初期化・データロードテスト (Initialization)

    @Test
    fun INI_01_initialLoad_success() = runTest {
        val viewModel = createViewModel()
        
        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.isLoading)
            
            advanceUntilIdle()
            
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(personId, state.personId)
            assertEquals(YearMonth.now(), state.selectedMonth)
        }
    }

    // endregion

    // region 3. 月次表示・期間管理テスト (Month & Navigation)

    @Test
    fun MON_01_nextMonth_triggersReload() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val currentMonth = viewModel.uiState.value.selectedMonth
        val nextMonth = currentMonth.plusMonths(1)
        
        viewModel.nextMonth()
        advanceUntilIdle()

        assertEquals(nextMonth, viewModel.uiState.value.selectedMonth)
        verify { medicationRepository.getMedicationRecordsByMonth(personId, nextMonth.toString()) }
    }

    // endregion

    // region 4. 服薬同期テスト (Sync)

    @Test
    fun SYN_01_syncMedicationDay_insertNewRecord() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val date = LocalDate.now().toString()
        val record = MedicationRecord(personId = personId, dosageDate = date, timeSlot = 0, status = 2, recordTime = Instant.now())
        
        viewModel.syncMedicationDay(date, listOf(record, null, null, null))
        advanceUntilIdle()

        coVerify { medicationRepository.insertMedicationRecord(any(), any(), any(), any()) }
    }

    @Test
    fun SYN_02_syncMedicationDay_deleteRecord() = runTest {
        val date = LocalDate.now().toString()
        val existingRecord = MedicationRecord(id = "c1", personId = personId, dosageDate = date, timeSlot = 0, status = 2, recordTime = Instant.now())
        
        // Mock existing data for that day
        every { medicationRepository.getMedicationRecordsByMonth(personId, any()) } returns flowOf(listOf(existingRecord))
        
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Clear all slots for that day
        viewModel.syncMedicationDay(date, listOf(null, null, null, null))
        advanceUntilIdle()

        coVerify { medicationRepository.deleteMedicationRecord(match { it.id == "c1" }, any(), any()) }
    }

    @Test
    fun SYN_04_syncMedicationDay_futureDateBlocked() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val futureDate = LocalDate.now().plusDays(1).toString()
        val record = MedicationRecord(personId = personId, dosageDate = futureDate, timeSlot = 0, status = 2, recordTime = Instant.now())

        viewModel.uiEventFlow.test {
            viewModel.syncMedicationDay(futureDate, listOf(record, null, null, null))
            val event = awaitItem()
            assertTrue(event is BaseUiStateViewModel.UiEvent.ShowErrorDialogRes)
        }
    }

    // endregion

    // region 5. 安全性・例外テスト (Safety)

    @Test
    fun ERR_01_loadFailure_safety() = runTest {
        every { medicationRepository.getMedicationRecordsByMonth(any(), any()) } returns flow { throw RuntimeException("Fetch Error") }

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        coVerify { auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("Fetch Error") }, any()) }
    }

    @Test
    fun ERR_02_syncFailure_safety() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { medicationRepository.insertMedicationRecord(any(), any(), any(), any()) } throws RuntimeException("Sync Error")
        
        val date = LocalDate.now().toString()
        val record = MedicationRecord(personId = personId, dosageDate = date, timeSlot = 0, status = 2, recordTime = Instant.now())

        viewModel.syncMedicationDay(date, listOf(record, null, null, null))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        coVerify { auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("Sync Error") }, any()) }
    }

    // endregion
}
