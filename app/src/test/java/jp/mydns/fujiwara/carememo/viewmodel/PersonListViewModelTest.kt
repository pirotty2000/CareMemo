package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.repository.*
import jp.mydns.fujiwara.carememo.logic.feature.PersonListViewEvent
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
 * Logic Test: PersonListViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonListViewModelTest {

    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val archivedRepository = mockk<DeleteOrRestorePersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val conditionRepository = mockk<ConditionRepository>(relaxed = true)
    private val emergencyContactRepository = mockk<EmergencyContactRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private val testPerson = Person(
        id = "u1", lastName = "浅井", firstName = "太郎",
        lastNameFurigana = "あさい", firstNameFurigana = "たろう",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    private val isNameMaskingEnabledFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)

        every { userSettingsRepository.isNameMaskingEnabled } returns isNameMaskingEnabledFlow
        every { userSettingsRepository.healthDisplayModeIsHistory } returns flowOf(true)
        every { personRepository.getAllPersons() } returns flowOf(listOf(testPerson))
        every { summaryRepository.getPersonCategorySummaries() } returns flowOf(emptyMap())
        every { conditionRepository.getPersonIdsByConditionKeyword(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun createViewModel(): PersonListViewModel {
        return PersonListViewModel(
            personRepository, archivedRepository, summaryRepository,
            conditionRepository, emergencyContactRepository,
            userSettingsRepository, auditLogRepository
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
            assertEquals(1, loaded.userList.size)
            assertEquals("浅井　太郎", loaded.userList[0].maskedName)
        }
    }

    @Test
    fun INI_02_maskingSettingReflection() = runTest {
        val viewModel = createViewModel()
        
        viewModel.uiState.test {
            advanceUntilIdle()
            
            isNameMaskingEnabledFlow.value = true
            advanceUntilIdle()
            
            val state = expectMostRecentItem()
            assertTrue(state.isNameMaskingEnabled)
            assertEquals("浅○\u3000太○", state.userList[0].maskedName)
        }
    }

    // endregion

    // region 3. フィルタ・検索管理テスト (Filtering & Search)

    @Test
    fun FLT_01_setSelectedSection_filtersList() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSelectedSection("か") // "あさい" should be filtered out
        advanceUntilIdle()

        assertEquals("か", viewModel.uiState.value.selectedSection)
        assertTrue(viewModel.uiState.value.userList.isEmpty())
    }

    @Test
    fun FLT_02_setSearchQuery_resetsSectionToAll() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSelectedSection("か")
        viewModel.setSearchQuery("test")
        
        assertEquals("全", viewModel.uiState.value.selectedSection)
        assertEquals("test", viewModel.uiState.value.searchQuery)
    }

    // endregion

    // region 4. クイックメニュー・詳細操作テスト (Quick Menu)

    @Test
    fun MNU_01_showQuickMenu_setsSelectedPerson() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showQuickMenu(testPerson)
        
        val state = viewModel.uiState.value
        assertTrue(state.isQuickActionMenuExpanded)
        assertEquals(testPerson, state.selectedPersonForQuickMenu)
    }

    @Test
    fun MNU_02_loadEmergencyContacts_success() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val mockContacts = listOf(mockk<EmergencyContact>())
        every { emergencyContactRepository.getContactsByPersonId("u1") } returns flowOf(mockContacts)

        viewModel.loadEmergencyContacts("u1")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isQuickActionMenuExpanded)
        assertEquals(mockContacts, viewModel.uiState.value.emergencyContactsForSheet)
    }

    // endregion

    // region 5. 利用者管理操作テスト (Management)

    @Test
    fun MGT_01_addPerson_success() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { personRepository.findExistingPerson(any()) } returns null
        
        viewModel.addPerson(testPerson)
        advanceUntilIdle()

        coVerify { personRepository.insertPerson(testPerson, any(), any()) }
    }

    @Test
    fun MGT_02_addPerson_duplicateBlocked() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Mock existing duplicate
        coEvery { personRepository.findExistingPerson(any()) } returns testPerson.copy(id = "other-id")

        viewModel.uiEventFlow.test {
            viewModel.addPerson(testPerson)
            val event = awaitItem()
            assertTrue(event is BaseUiStateViewModel.UiEvent.ShowErrorDialogRes)
        }
        coVerify(exactly = 0) { personRepository.insertPerson(any(), any(), any()) }
    }

    // endregion

    // region 6. ナビゲーションテスト (Navigation)

    @Test
    fun NAV_01_navigateToDetail_emitsCorrectEvent() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setSearchQuery("search-term")

        viewModel.viewEvent.test {
            viewModel.navigateToDetail("u1", Category.BP_AND_PULSE)
            val event = awaitItem()
            assertTrue(event is PersonListViewEvent.NavigateToDetail)
            assertEquals("search-term", (event as PersonListViewEvent.NavigateToDetail).query)
        }
    }

    // endregion

    // region 7. 安全性・例外テスト (Safety)

    @Test
    fun ERR_01_listLoadFailure_safety() = runTest {
        every { personRepository.getAllPersons() } returns flow { throw RuntimeException("List Error") }

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        coVerify { auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("List Error") }, any()) }
    }

    // endregion
}
