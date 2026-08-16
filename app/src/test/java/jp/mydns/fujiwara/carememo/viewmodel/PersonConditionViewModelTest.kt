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

/**
 * Logic Test: PersonConditionViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonConditionViewModelTest {

    private val conditionRepository = mockk<ConditionRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private val testDispatcher = StandardTestDispatcher()

    private val personId = "u1"
    private val testPerson = Person(
        id = personId, lastName = "記録", firstName = "太郎",
        lastNameFurigana = "きろく", firstNameFurigana = "たろう",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    private val testRecords = listOf(
        ConditionAtVisit(id = "c1", personId = personId, title = "朝の様子", condition = "元気です", author = "記録者", recordTime = Instant.now()),
        ConditionAtVisit(id = "c2", personId = personId, title = "昼の様子", condition = "眠そう", author = "記録者", recordTime = Instant.now())
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)

        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("テスト記録者")
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        every { conditionRepository.getConditionAtVisitByPersonId(any()) } returns flowOf(testRecords)
        every { summaryRepository.getPersonCategorySummaryById(any()) } returns flowOf(PersonCategorySummary())
        
        // Setup for duplicate check to pass by default
        coEvery { conditionRepository.findConditionAtTime(any(), any()) } returns null
        
        // Repository physical file mock
        every { conditionRepository.getPhotoPhysicalFiles() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun createViewModel(handleParams: Map<String, Any> = mapOf("personId" to personId)): PersonConditionViewModel {
        return PersonConditionViewModel(
            conditionRepository, personRepository, summaryRepository, 
            userSettingsRepository, auditLogRepository, 
            SavedStateHandle(handleParams)
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
            assertEquals(2, state.records.size)
            assertEquals(personId, state.personId)
        }
    }

    // endregion

    // region 3. 選択・検索管理テスト (Selection & Search)

    @Test
    fun SEL_01_setSelectedConditionId_triggersPhotoLoad() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val mockPhotos = listOf(mockk<ConditionPhoto>())
        every { conditionRepository.getConditionPhotosByConditionId("c1") } returns flowOf(mockPhotos)

        viewModel.setSelectedConditionId("c1")
        advanceUntilIdle()

        assertEquals("c1", viewModel.uiState.value.selectedConditionId)
        assertEquals(mockPhotos, viewModel.uiState.value.currentConditionPhotos)
    }

    @Test
    fun SEL_02_updateSearchQuery_filtersImmediately() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateSearchQuery("朝")
        
        val state = viewModel.uiState.value
        assertEquals("朝", state.searchQuery)
        assertEquals(1, state.filteredRecords.size)
        assertEquals("c1", state.filteredRecords[0].id)
    }

    @Test
    fun SEL_03_setSelectedConditionId_null_resetsState() = runTest {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSelectedConditionId(newId)
        viewModel.updateEditInput { it.copy(condition = "test") }
        
        viewModel.setSelectedConditionId(null)
        
        val state = viewModel.uiState.value
        assertNull(state.selectedConditionId)
        assertFalse(state.isEditing)
        assertEquals("", state.editInput.condition)
    }

    // endregion

    // region 4. 編集セッション管理テスト (Editing)

    @Test
    fun EDT_01_startNewSession_setsDefaultAuthor() = runTest {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val viewModel = createViewModel()
        
        // Subscribe to defaultRecorderName to ensure it collects from mock before using it
        viewModel.defaultRecorderName.test {
            // First item is "", then mock value "テスト記録者"
            // Wait until it becomes the expected value
            var currentAuthor = awaitItem()
            while (currentAuthor == "") {
                currentAuthor = awaitItem()
            }
            assertEquals("テスト記録者", currentAuthor)

            // Now that defaultRecorderName.value is correct, trigger the action
            viewModel.setSelectedConditionId(newId)
            advanceUntilIdle()
            
            val state = viewModel.uiState.value
            assertTrue(state.isEditing)
            assertEquals("テスト記録者", state.editInput.author)
            assertFalse(state.isChanged)
        }
    }

    @Test
    fun EDT_02_EDT_03_editSession_changeDetection() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSelectedConditionId("c1")
        viewModel.startEditSession()
        
        assertTrue(viewModel.uiState.value.isEditing)
        assertEquals("朝の様子", viewModel.uiState.value.editInput.title)

        viewModel.updateEditInput { it.copy(title = "Updated Title") }
        
        assertTrue(viewModel.uiState.value.isChanged)
        assertTrue(viewModel.uiState.value.isSaveEnabled)
    }

    @Test
    fun EDT_04_cancelEditSession_resetsEditing() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSelectedConditionId("c1")
        viewModel.startEditSession()
        viewModel.cancelEditSession()
        
        assertFalse(viewModel.uiState.value.isEditing)
    }

    // endregion

    // region 5. 処理実行テスト (Execution)

    @Test
    fun EXE_01_saveCurrentEdit_success() = runTest {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSelectedConditionId(newId)
        viewModel.updateEditInput { it.copy(condition = "Save this content", author = "Author", recordTime = Instant.now()) }
        
        coEvery { conditionRepository.insertConditionAtVisit(any(), any(), any(), any()) } returns "new-uuid"

        viewModel.saveCurrentEdit()
        advanceUntilIdle()

        coVerify { conditionRepository.insertConditionAtVisit(any(), any(), any(), any()) }
        coVerify { conditionRepository.linkTemporaryPhotosToRecord(any(), "new-uuid", any(), any()) }
    }

    // endregion

    // region 6. 安全性・例外テスト (Safety)

    @Test
    fun ERR_01_loadFailure_safety() = runTest {
        every { conditionRepository.getConditionAtVisitByPersonId(any()) } returns flow { throw RuntimeException("Fetch Error") }

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        coVerify { auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("Fetch Error") }, any()) }
    }

    @Test
    fun ERR_03_saveFailure_safety() = runTest {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { conditionRepository.insertConditionAtVisit(any(), any(), any(), any()) } throws RuntimeException("Save Error")
        
        viewModel.setSelectedConditionId(newId)
        viewModel.updateEditInput { it.copy(condition = "content", author = "auth", recordTime = Instant.now()) }
        
        viewModel.saveCurrentEdit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        coVerify { auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("Save Error") }, "OTHER_ERROR") }
    }

    // endregion
}
