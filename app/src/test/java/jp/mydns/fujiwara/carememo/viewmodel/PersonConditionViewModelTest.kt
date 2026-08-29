package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.*
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
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
    private val securitySession = SecuritySession()
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
            userSettingsRepository, securitySession, auditLogRepository, 
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

        viewModel.saveCurrentEdit()
        advanceUntilIdle()

        coVerify { conditionRepository.saveConditionAtVisit(any(), false, any(), any()) }
        // The ID should be generated by Logic layer (not "new-uuid" return value)
        coVerify { conditionRepository.linkTemporaryPhotosToRecord(any(), match { !IdLogic.isNew(it) }, any(), any()) }
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

        coEvery { conditionRepository.saveConditionAtVisit(any(), any(), any(), any()) } throws RuntimeException("Save Error")

        viewModel.setSelectedConditionId(newId)
        viewModel.updateEditInput { it.copy(condition = "content", author = "auth", recordTime = Instant.now()) }

        viewModel.saveCurrentEdit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        coVerify { auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("Save Error") }, "OTHER_ERROR") }
    }

    // region 8. 状態復元テスト (State Restoration)

    @Test
    fun RST_01_restore_input_and_session() = runTest {
        val handle = SavedStateHandle(mapOf(
            "restoration_version" to 1,
            "restoration_selected_id" to "c1",
            "restoration_is_editing" to true,
            "restoration_in_title" to "復元タイトル",
            "restoration_in_body" to "復元された本文です。",
            "restoration_in_time" to 1000L
        ))

        val viewModel = PersonConditionViewModel(
            conditionRepository, personRepository, summaryRepository,
            userSettingsRepository, securitySession, auditLogRepository, handle
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("c1", state.selectedConditionId)
        assertTrue(state.isEditing)
        assertEquals("復元タイトル", state.editInput.title)
        assertEquals("復元された本文です。", state.editInput.condition)
        assertEquals(Instant.ofEpochMilli(1000L), state.editInput.recordTime)
    }

    @Test
    fun RST_02_restore_baseline_and_isChanged() = runTest {
        val handle = SavedStateHandle(mapOf(
            "restoration_version" to 1,
            "restoration_in_body" to "書き換え後",
            "restoration_base_body" to "オリジナル",
            "restoration_base_time" to 1000L
        ))

        val viewModel = PersonConditionViewModel(
            conditionRepository, personRepository, summaryRepository,
            userSettingsRepository, securitySession, auditLogRepository, handle
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("書き換え後", state.editInput.condition)
        assertEquals("オリジナル", state.initialSnapshot?.condition)
        assertTrue(state.isChanged)
    }

    @Test
    fun RST_03_restore_photo_preview_state() = runTest {
        val handle = SavedStateHandle(mapOf(
            "restoration_version" to 1,
            "restoration_preview_uri" to "content://test/photo.jpg",
            "restoration_preview_caption" to "書きかけのキャプション"
        ))

        val viewModel = PersonConditionViewModel(
            conditionRepository, personRepository, summaryRepository,
            userSettingsRepository, securitySession, auditLogRepository, handle
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("content://test/photo.jpg", state.previewUri)
        assertEquals("書きかけのキャプション", state.previewCaption)
    }

    // endregion
}
