package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.*
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputViewEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/**
 * Logic Test: BatchInputViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchInputViewModelTest {

    private val healthRepository = mockk<HealthRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private val testDispatcher = StandardTestDispatcher()
    
    private val testPerson = Person(
        id = "u1",
        lastName = "健康",
        firstName = "太郎",
        lastNameFurigana = "ケンコウ",
        firstNameFurigana = "タロウ",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )
    
    private val isNameMaskingEnabledFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)

        every { userSettingsRepository.isNameMaskingEnabled } returns isNameMaskingEnabledFlow
        coEvery { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        coEvery { summaryRepository.getPersonCategorySummaryById(any()) } returns flowOf(PersonCategorySummary())
        
        coEvery { healthRepository.findHeightAndWeightAtTime(any(), any()) } returns null
        coEvery { healthRepository.findBpAndPulseAtTime(any(), any()) } returns null
        coEvery { healthRepository.findGlucoseAndHbA1cAtTime(any(), any()) } returns null
        coEvery { healthRepository.findHistoryRecordAtTime(any(), any(), any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun createViewModel(personId: String? = "u1"): BatchInputViewModel {
        return BatchInputViewModel(
            healthRepository,
            personRepository,
            summaryRepository,
            userSettingsRepository,
            auditLogRepository,
            SavedStateHandle(if (personId != null) mapOf("personId" to personId) else emptyMap())
        )
    }

    // region 2. 初期化・データロードテスト (Initialization)

    @Test
    fun INI_01_initialLoad_success() = runTest {
        val viewModel = createViewModel()
        
        viewModel.uiState.test {
            // Skip intermediate state transitions during initialization
            advanceUntilIdle()
            
            val loaded = expectMostRecentItem()
            assertFalse(loaded.isLoading)
            assertEquals("u1", loaded.personId)
            // Name is masked by default in state due to initial values in BaseViewModel
            assertEquals("健○　太○", loaded.currentPersonName)
            assertTrue(loaded.weight.isEmpty())
        }
    }

    @Test
    fun INI_02_maskingSettingReflection() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        
        isNameMaskingEnabledFlow.value = true
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.isNameMaskingEnabled)
        assertEquals("健○\u3000太○", viewModel.uiState.value.currentPersonName)
    }

    // endregion

    // region 3. 入力・状態管理テスト (Input & State)

    @Test
    fun INP_01_inputUpdate_reactivity() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateWeight("60")
        
        val state = viewModel.uiState.value
        assertEquals("60", state.weight)
        assertTrue(state.isChanged)
        assertTrue(state.isValid)
    }

    @Test
    fun INP_02_timeUpdate_reactivity() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val currentYear = viewModel.uiState.value.year
        val nextYear = (currentYear.toInt() + 1).toString()
        viewModel.updateYear(nextYear)
        
        val state = viewModel.uiState.value
        assertTrue("State should be marked as changed after year update", state.isChanged)
        assertEquals(nextYear, state.year)
        // B-2: Verify recordTime is calculated and held as a val property
        assertNotNull("recordTime should be non-null after valid year update", state.recordTime)
        
        // Invalid date test
        viewModel.updateDay("32")
        assertNull("recordTime should be null for invalid date", viewModel.uiState.value.recordTime)
    }

    // endregion

    // region 4. 一括保存テスト (Batch Saving)

    @Test
    fun SAV_01_saveBatch_success() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateWeight("60")
        viewModel.saveBatch()
        advanceUntilIdle()

        coVerify { healthRepository.saveHealthDataBatch(any(), any(), any()) }
        // Verify input cleared
        assertTrue(viewModel.uiState.value.weight.isEmpty())
        assertFalse(viewModel.uiState.value.isChanged)
    }

    @Test
    fun SAV_03_saveBatch_validationError() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateWeight("abc") // Invalid format
        viewModel.saveBatch()
        advanceUntilIdle()

        coVerify(exactly = 0) { healthRepository.saveHealthDataBatch(any(), any(), any()) }
        coVerify { 
            auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("INVALID_VALUE") }, "VALIDATION_ERROR")
        }
    }

    @Test
    fun SAV_04_saveBatch_duplicateBlocked() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateWeight("60")
        // Simulate duplicate
        coEvery { healthRepository.findHistoryRecordAtTime(any(), any(), any()) } returns mockk<HeightAndWeight>()

        viewModel.saveBatch()
        advanceUntilIdle()

        coVerify(exactly = 0) { healthRepository.saveHealthDataBatch(any(), any(), any()) }
        coVerify {
            auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("Duplicate detected") }, "VALIDATION_ERROR")
        }
    }

    @Test
    fun SAV_05_saveBatch_repositoryException() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateWeight("60")
        coEvery { healthRepository.saveHealthDataBatch(any(), any(), any()) } throws RuntimeException("DB Error")

        viewModel.saveBatch()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        coVerify {
            auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("DB Error") }, "OTHER_ERROR")
        }
    }

    @Test
    fun SAV_06_saveBatch_doubleClickPrevention() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateWeight("60")

        // Mock repository with delay
        coEvery { healthRepository.saveHealthDataBatch(any(), any(), any()) } coAnswers {
            delay(1000.milliseconds)
        }

        // Call saveBatch twice
        viewModel.saveBatch()
        viewModel.saveBatch()

        advanceUntilIdle()

        // Verify repository was called ONLY once
        coVerify(exactly = 1) { healthRepository.saveHealthDataBatch(any(), any(), any()) }
    }

    @Test
    fun SAV_07_saveBatch_failure_dataRetention() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateWeight("60")
        val originalState = viewModel.uiState.value

        // Mock repository with exception
        coEvery { healthRepository.saveHealthDataBatch(any(), any(), any()) } throws RuntimeException("DB Error")

        viewModel.saveBatch()
        advanceUntilIdle()

        // Verify that data is STILL there
        assertEquals("60", viewModel.uiState.value.weight)
        assertTrue(viewModel.uiState.value.isChanged)
        // Check that initial values (which determine isChanged) were NOT updated to current
        assertEquals(originalState.initialYear, viewModel.uiState.value.initialYear)
    }

    // endregion

    // region 5. ナビゲーションテスト (Navigation)

    @Test
    fun NAV_01_navigateBack() = runTest {
        val viewModel = createViewModel()
        viewModel.viewEvent.test {
            viewModel.navigateBack()
            assertEquals(BatchInputViewEvent.NavigateBack, awaitItem())
        }
    }

    // endregion
}
