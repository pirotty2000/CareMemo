package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.*
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputOperation
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputScreenState
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
    private val securitySession = SecuritySession()
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
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        every { summaryRepository.getPersonCategorySummaryById(any()) } returns flowOf(PersonCategorySummary())
        
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
            securitySession,
            auditLogRepository,
            SavedStateHandle(if (personId != null) mapOf("personId" to personId) else emptyMap())
        )
    }

    // region 2. 初期化・データロードテスト (Initialization)

    @Test
    fun INI_01_initialLoad_success() = runTest {
        val viewModel = createViewModel()
        
        viewModel.uiState.test {
            advanceUntilIdle()
            
            val loaded = expectMostRecentItem()
            assertTrue(loaded.screenState is BatchInputScreenState.Active)
            assertEquals("u1", loaded.personId)
            assertTrue(loaded.input.weight.isEmpty())
        }
    }

    @Test
    fun INI_02_maskingSettingReflection() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        
        isNameMaskingEnabledFlow.value = true
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.isNameMaskingEnabled)
    }

    // endregion

    // region 3. 入力・状態管理テスト (Input & State)

    @Test
    fun INP_01_inputUpdate_reactivity() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateWeight("60")
        
        val input = viewModel.uiState.value.input
        assertEquals("60", input.weight)
        assertTrue(input.isChanged)
        assertTrue(input.isValid)
    }

    @Test
    fun INP_02_timeUpdate_reactivity() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val currentYear = viewModel.uiState.value.input.year
        val nextYear = (currentYear.toInt() + 1).toString()
        viewModel.updateYear(nextYear)
        
        val input = viewModel.uiState.value.input
        assertTrue("State should be marked as changed after year update", input.isChanged)
        assertEquals(nextYear, input.year)
        assertNotNull("recordTime should be non-null after valid year update", input.recordTime)
        
        // Invalid date test
        viewModel.updateDay("32")
        assertNull("recordTime should be null for invalid date", viewModel.uiState.value.input.recordTime)
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
        assertTrue(viewModel.uiState.value.input.weight.isEmpty())
        assertFalse(viewModel.uiState.value.input.isChanged)
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

        assertEquals(BatchInputOperation.Idle, viewModel.uiState.value.operation)
        coVerify {
            auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("DB Error") }, "OTHER_ERROR")
        }
    }

    @Test
    fun SAV_06_saveBatch_doubleClickPrevention() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateWeight("60")

        coEvery { healthRepository.saveHealthDataBatch(any(), any(), any()) } coAnswers {
            delay(1000.milliseconds)
        }

        viewModel.saveBatch()
        viewModel.saveBatch()

        advanceUntilIdle()

        coVerify(exactly = 1) { healthRepository.saveHealthDataBatch(any(), any(), any()) }
    }

    @Test
    fun SAV_07_saveBatch_failure_dataRetention() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateWeight("60")
        val originalInput = viewModel.uiState.value.input

        coEvery { healthRepository.saveHealthDataBatch(any(), any(), any()) } throws RuntimeException("DB Error")

        viewModel.saveBatch()
        advanceUntilIdle()

        assertEquals("60", viewModel.uiState.value.input.weight)
        assertTrue(viewModel.uiState.value.input.isChanged)
        assertEquals(originalInput.initialYear, viewModel.uiState.value.input.initialYear)
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

    // region 6. 状態復元テスト (State Restoration)

    @Test
    fun RST_01_restore_inputs() = runTest {
        val handle = SavedStateHandle(mapOf(
            "restoration_version" to 1,
            "restoration_in_weight" to "70.5",
            "restoration_in_year" to "2024",
            "restoration_in_month" to "12",
            "restoration_in_day" to "31"
        ))

        val viewModel = BatchInputViewModel(
            healthRepository, personRepository, summaryRepository,
            userSettingsRepository, securitySession, auditLogRepository, handle
        )
        advanceUntilIdle()

        val input = viewModel.uiState.value.input
        assertEquals("70.5", input.weight)
        assertEquals("2024", input.year)
        assertEquals("12", input.month)
        assertEquals("31", input.day)
    }

    @Test
    fun RST_02_restore_baseline_and_isChanged() = runTest {
        val handle = SavedStateHandle(mapOf(
            "restoration_version" to 1,
            "restoration_in_weight" to "70.0",
            "restoration_base_year" to "2020",
            "restoration_in_year" to "2020"
        ))

        val viewModel = BatchInputViewModel(
            healthRepository, personRepository, summaryRepository,
            userSettingsRepository, securitySession, auditLogRepository, handle
        )
        advanceUntilIdle()

        val input = viewModel.uiState.value.input
        assertEquals("70.0", input.weight)
        assertEquals("2020", input.initialYear)
        assertTrue(input.isChanged)
    }

    @Test
    fun RST_03_restore_prevents_reset_on_person_load() = runTest {
        val handle = SavedStateHandle(mapOf(
            "restoration_version" to 1,
            "restoration_in_height" to "175.0"
        ))

        val viewModel = BatchInputViewModel(
            healthRepository, personRepository, summaryRepository,
            userSettingsRepository, securitySession, auditLogRepository, handle
        )
        advanceUntilIdle()

        viewModel.loadPerson("u1")
        advanceUntilIdle()

        assertEquals("175.0", viewModel.uiState.value.input.height)
    }

    // endregion

    // region 7. バリデーション・フィードバックテスト (Validation Feedback)

    @Test
    fun FBK_01_outOfRange_feedback() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateBodyTemp("45.1")
        viewModel.markFieldAsTouched("bodyTemperature")
        advanceUntilIdle()

        val input = viewModel.uiState.value.input
        assertEquals(R.string.health_err_range_format, input.fieldErrors["bodyTemperature"])
        assertEquals(listOf("30.0", "45.0"), input.fieldErrorArgs["bodyTemperature"])
    }

    @Test
    fun FBK_02_invalidFormat_feedback() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateHeight("170.0.0")
        viewModel.markFieldAsTouched("height")
        advanceUntilIdle()

        assertEquals(R.string.common_error_invalid_input, viewModel.uiState.value.input.fieldErrors["height"])
    }

    @Test
    fun FBK_03_futureDate_feedback() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateYear("2099")
        viewModel.markFieldAsTouched("year")
        advanceUntilIdle()

        assertEquals(R.string.common_err_future_date_not_allowed, viewModel.uiState.value.input.fieldErrors["recordTime"])
    }

    @Test
    fun FBK_05_errorClears_onCorrection() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateBodyTemp("45.1")
        assertNotNull(viewModel.uiState.value.input.fieldErrors["bodyTemperature"])

        viewModel.updateBodyTemp("36.5")
        advanceUntilIdle()

        assertNull("Error should be cleared", viewModel.uiState.value.input.fieldErrors["bodyTemperature"])
    }

    // endregion
}
