package jp.mydns.fujiwara.carememo.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.*
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionOperation
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionScreenState
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionViewEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
        id = personId, lastName = "所見", firstName = "太郎",
        lastNameFurigana = "ショケン", firstNameFurigana = "タロウ",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        mockkStatic(Uri::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        Dispatchers.setMain(testDispatcher)

        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("記録者A")
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        every { summaryRepository.getPersonCategorySummaryById(any()) } returns flowOf(PersonCategorySummary())
        
        coEvery { conditionRepository.findConditionAtTime(any(), any()) } returns null
        every { conditionRepository.getConditionAtVisitByPersonId(any()) } returns flowOf(emptyList())
        every { conditionRepository.getAllPhotosByPersonIdFlow(any()) } returns flowOf(emptyList())
        every { conditionRepository.getConditionPhotosByConditionId(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
        unmockkStatic(Uri::class)
    }

    private fun createViewModel(
        personId: String? = "u1"
    ): PersonConditionViewModel {
        return PersonConditionViewModel(
            conditionRepository, personRepository, summaryRepository, 
            userSettingsRepository, securitySession, auditLogRepository, 
            SavedStateHandle(mapOf("personId" to personId))
        )
    }

    // region 2. 初期化・データロードテスト (Initialization)

    @Test
    fun INI_01_initialLoad_success() = runTest {
        val viewModel = createViewModel()
        
        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.screenState is PersonConditionScreenState.Active)
            assertEquals(personId, state.personId)
        }
    }

    @Test
    fun INI_02_searchQueryReflection() = runTest {
        val handle = SavedStateHandle(mapOf("personId" to personId))
        val viewModel = PersonConditionViewModel(
            conditionRepository, personRepository, summaryRepository,
            userSettingsRepository, securitySession, auditLogRepository, handle
        )
        advanceUntilIdle()

        viewModel.updateSearchQuery("発熱")
        advanceUntilIdle()

        assertEquals("発熱", viewModel.uiState.value.searchQuery)
    }

    // endregion

    // region 3. 編集セッション管理テスト (Editing)

    @Test
    fun EDT_01_startNewSession_setsDefaultAuthor() = runTest {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSelectedConditionId(newId)
        
        val state = viewModel.uiState.value
        val session = state.editSession
        assertTrue(session.isEditing)
        assertEquals("記録者A", session.editInput.author)
        assertNotNull(session.editInput.recordTime)
    }

    @Test
    fun EDT_02_startEditSession_loadsExistingData() = runTest {
        val record = ConditionAtVisit(id = "c1", personId = personId, title = "T1", condition = "B1", author = "A1", recordTime = Instant.now())
        every { conditionRepository.getConditionAtVisitByPersonId(personId) } returns flowOf(listOf(record))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSelectedConditionId("c1")
        viewModel.startEditSession()
        
        val session = viewModel.uiState.value.editSession
        assertTrue(session.isEditing)
        assertEquals("T1", session.editInput.title)
        assertEquals("B1", session.editInput.condition)
    }

    @Test
    fun EDT_03_inputUpdate_triggersValidation() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSelectedConditionId(AppSpecifications.Id.NEW_RECORD_ID)
        viewModel.updateEditInput { it.copy(condition = "状態が良い") }
        
        val session = viewModel.uiState.value.editSession
        assertTrue(session.isChanged)
        // author が空でない（デフォルト A）かつ condition が空でないため有効
        assertTrue(session.isSaveEnabled)
    }

    // endregion

    // region 4. 処理実行テスト (Execution)

    @Test
    fun EXE_01_saveCurrentEdit_success() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSelectedConditionId(AppSpecifications.Id.NEW_RECORD_ID)
        viewModel.updateEditInput { it.copy(condition = "テスト所見", author = "記録者", title = "T") }
        advanceUntilIdle()

        // 状態が保存可能であることを確認
        assertTrue("Save should be enabled", viewModel.uiState.value.editSession.isSaveEnabled)

        viewModel.saveCurrentEdit()
        advanceUntilIdle()

        coVerify(timeout = 2000) { conditionRepository.saveConditionAtVisit(any(), any(), any(), any()) }
        assertFalse(viewModel.uiState.value.editSession.isEditing)
    }

    @Test
    fun EXE_02_deleteRecord_success() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val record = ConditionAtVisit(id = "c1", personId = personId, title = null, condition = "D1", author = "A1", recordTime = Instant.now())
        viewModel.deleteRecord(record)
        advanceUntilIdle()

        coVerify { conditionRepository.deleteConditionAtVisit(record, any(), any()) }
    }

    // endregion

    // region 5. 写真操作テスト (Photos)

    @Test
    fun PHT_01_processAndSavePhoto_callsRepository() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val dummyUri = Uri.parse("content://dummy")
        coEvery { conditionRepository.processAndSavePhoto(any()) } returns Pair("orig.jpg", "thumb.jpg")

        viewModel.processAndSavePhoto(dummyUri, "c1", "説明")
        advanceUntilIdle()

        coVerify { conditionRepository.saveConditionPhoto(match { it.photoFileName == "orig.jpg" }, any(), any(), any()) }
    }

    // endregion

    // region 6. 状態復元テスト (State Restoration)

    @Test
    fun RST_01_restore_full_session() = runTest {
        val handle = SavedStateHandle(mapOf(
            "restoration_version" to 1,
            "restoration_selected_id" to "c1",
            "restoration_is_editing" to true,
            "restoration_in_body" to "復元された本文",
            "restoration_in_author" to "復元者",
            "restoration_in_time" to 2000L
        ))

        val viewModel = PersonConditionViewModel(
            conditionRepository, personRepository, summaryRepository,
            userSettingsRepository, securitySession, auditLogRepository, handle
        )
        advanceUntilIdle()

        val session = viewModel.uiState.value.editSession
        assertEquals("c1", session.selectedConditionId)
        assertTrue(session.isEditing)
        assertEquals("復元された本文", session.editInput.condition)
        assertEquals(Instant.ofEpochMilli(2000L), session.editInput.recordTime)
    }

    // endregion
}
