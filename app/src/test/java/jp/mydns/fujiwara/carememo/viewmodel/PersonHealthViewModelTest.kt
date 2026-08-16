package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.repository.*
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthViewEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Logic Test: PersonHealthViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonHealthViewModelTest {

    private val healthRepository = mockk<HealthRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private val personId = "u1"
    private val testPerson = Person(
        id = personId, lastName = "健康", firstName = "太郎",
        lastNameFurigana = "ケンコウ", firstNameFurigana = "タロウ",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    private val displayModeFlow = MutableStateFlow(true)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)

        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.healthDisplayModeIsHistory } returns displayModeFlow
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        every { summaryRepository.getPersonCategorySummaryById(any()) } returns flowOf(PersonCategorySummary())
        
        coEvery { healthRepository.findHeightAndWeightAtTime(any(), any()) } returns null
        coEvery { healthRepository.findHistoryRecordAtTime(any(), any(), any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun createViewModel(
        personId: String? = "u1",
        category: Category = Category.HEIGHT_AND_WEIGHT
    ): PersonHealthViewModel {
        return PersonHealthViewModel(
            healthRepository, personRepository, summaryRepository, 
            userSettingsRepository, auditLogRepository, 
            SavedStateHandle(mapOf("personId" to personId, "categoryName" to category.name))
        )
    }

    // region 2. 初期化・データロードテスト (Initialization)

    @Test
    fun INI_01_initialLoad_success() = runTest {
        val viewModel = createViewModel()
        
        viewModel.uiState.test {
            // Skip intermediate state transitions
            advanceUntilIdle()
            
            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertEquals(personId, state.personId)
            assertEquals(Category.HEIGHT_AND_WEIGHT, state.currentCategory)
        }
    }

    @Test
    fun INI_02_launchWithSpecificCategory() = runTest {
        val viewModel = createViewModel(category = Category.BP_AND_PULSE)
        advanceUntilIdle()
        assertEquals(Category.BP_AND_PULSE, viewModel.uiState.value.currentCategory)
    }

    @Test
    fun INI_03_displayModeReflection() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        
        displayModeFlow.value = false
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.preferredShowHistory)
    }

    // endregion

    // region 3. カテゴリ・状態管理テスト (Category & State)

    @Test
    @Suppress("CheckResult", "UNUSED_EXPRESSION")
    fun CAT_01_setCategory_triggersFlowSubscription() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setCategory(Category.GLUCOSE_AND_HBA1C)
        advanceUntilIdle()
        
        assertEquals(Category.GLUCOSE_AND_HBA1C, viewModel.uiState.value.currentCategory)
        verify { healthRepository.getGlucoseAndHbA1cByPersonId(personId) }
    }

    @Test
    fun CAT_02_updatePreferredShowHistory_callsRepository() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updatePreferredShowHistory(false)
        advanceUntilIdle()

        coVerify { userSettingsRepository.setHealthDisplayModeIsHistory(false) }
    }

    // endregion

    // region 4. 編集セッション管理テスト (Editing)

    @Test
    fun EDT_01_startNewSession_setsCurrentTime() = runTest {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSelectedRecordId(newId)
        
        val state = viewModel.uiState.value
        assertTrue(state.isEditing)
        assertNotNull(state.editInput.recordTime)
    }

    @Test
    fun EDT_02_inheritLatestHeight() = runTest {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val lastRecord = HeightAndWeight(id = "prev", personId = personId, height = 165.5, weight = 60.0, recordTime = Instant.now().minusSeconds(3600))
        every { healthRepository.getHeightAndWeightByPersonId(personId) } returns flowOf(listOf(lastRecord))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSelectedRecordId(newId)
        assertEquals("165.5", viewModel.uiState.value.editInput.heightText)
    }

    @Test
    fun EDT_04_inputUpdate_triggersValidationAndChangeDetection() = runTest {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSelectedRecordId(newId)
        viewModel.updateEditInput { it.copy(weightText = "60.0") }
        
        val state = viewModel.uiState.value
        assertTrue(state.isChanged)
        assertTrue(state.isSaveEnabled)
    }

    // endregion

    // region 5. 処理実行テスト (Execution)

    @Test
    fun EXE_01_saveCurrentEdit_new_success() = runTest {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSelectedRecordId(newId)
        viewModel.updateEditInput { it.copy(weightText = "60.0", recordTime = Instant.now()) }

        viewModel.saveCurrentEdit()
        advanceUntilIdle()

        coVerify { healthRepository.insertHistoryRecord(any(), any(), any(), false) }
    }

    @Test
    fun EXE_02_save_duplicateBlocked() = runTest {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val viewModel = createViewModel()
        advanceUntilIdle()

        val fixedTime = Instant.parse("2023-10-27T10:00:00Z")
        viewModel.setSelectedRecordId(newId)
        viewModel.updateEditInput { 
            it.copy(heightText = "170.0", weightText = "60.0", recordTime = fixedTime) 
        }
        
        // Simulate duplicate
        val existing = HeightAndWeight(id = "existing-1", personId = personId, height = 170.0, weight = 60.0, recordTime = fixedTime)
        coEvery { healthRepository.findHistoryRecordAtTime(any(), any(), any()) } returns existing

        viewModel.uiEventFlow.test {
            viewModel.saveCurrentEdit()
            val event = awaitItem()
            assertTrue(event is BaseUiStateViewModel.UiEvent.ShowErrorDialogRes)
            // Expecting duplicate error
            assertEquals("Should show duplicate error message", R.string.common_err_duplicate_blocked_simple, (event as BaseUiStateViewModel.UiEvent.ShowErrorDialogRes).messageResId)
        }
    }

    @Test
    fun EXE_03_deleteRecord_success() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val record = HeightAndWeight(id = "h1", personId = personId, height = 170.0, weight = 60.0, recordTime = Instant.now())
        viewModel.deleteRecord(record)
        advanceUntilIdle()

        coVerify { healthRepository.deleteHistoryRecord(record, any(), any()) }
    }

    // endregion

    // region 6. 安全性・例外テスト (Safety)

    @Test
    fun ERR_01_loadFailure_safety() = runTest {
        every { healthRepository.getHeightAndWeightByPersonId(any()) } returns flow { throw RuntimeException("Fetch Error") }

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        coVerify { auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("Fetch Error") }, any()) }
    }

    @Test
    fun ERR_02_saveFailure_safety() = runTest {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { healthRepository.insertHistoryRecord(any(), any(), any(), any()) } throws RuntimeException("Save Error")
        
        viewModel.setSelectedRecordId(newId)
        viewModel.updateEditInput { it.copy(weightText = "60.0", recordTime = Instant.now()) }
        
        viewModel.saveCurrentEdit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        coVerify { auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("Save Error") }, any()) }
    }

    // endregion

    // region 7. ナビゲーションテスト (Navigation)

    @Test
    fun NAV_01_navigateToGraphExpansion() = runTest {
        val viewModel = createViewModel()
        viewModel.viewEvent.test {
            viewModel.navigateToGraphExpansion(personId, Category.HEIGHT_AND_WEIGHT, 0)
            val event = awaitItem()
            assertTrue(event is PersonHealthViewEvent.NavigateToGraphExpansion)
            assertEquals(personId, (event as PersonHealthViewEvent.NavigateToGraphExpansion).personId)
        }
    }

    // endregion
}
