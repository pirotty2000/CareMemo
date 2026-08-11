package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailViewEvent
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
 * Logic Test: PersonDetailUiStateViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailUiStateViewModelTest {

    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private val testDispatcher = StandardTestDispatcher()

    private val personId = "u1"
    private val testPerson = Person(
        id = personId, lastName = "詳細", firstName = "太郎",
        lastNameFurigana = "しょうさい", firstNameFurigana = "たろう",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)

        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        coEvery { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        coEvery { summaryRepository.getPersonCategorySummaryById(any()) } returns flowOf(PersonCategorySummary(hasCondition = true))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun createViewModel(handleParams: Map<String, Any> = mapOf("personId" to personId)): PersonDetailUiStateViewModel {
        return PersonDetailUiStateViewModel(
            personRepository,
            summaryRepository,
            userSettingsRepository,
            auditLogRepository,
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
            
            val loaded = expectMostRecentItem()
            assertFalse(loaded.isLoading)
            assertEquals(personId, loaded.personId)
            assertEquals("詳細", loaded.person?.lastName)
            assertTrue(loaded.personSummary?.hasCondition == true)
        }
    }

    @Test
    fun INI_02_initialCategorySpecified() = runTest {
        val viewModel = createViewModel(mapOf("personId" to personId, "categoryName" to Category.BP_AND_PULSE.name))
        advanceUntilIdle()

        assertEquals(Category.BP_AND_PULSE, viewModel.uiState.value.currentCategory)
    }

    @Test
    fun INI_03_invalidCategory_defaultsToHeightWeight() = runTest {
        val viewModel = createViewModel(mapOf("personId" to personId, "categoryName" to "INVALID_ENUM"))
        advanceUntilIdle()

        assertEquals(Category.HEIGHT_AND_WEIGHT, viewModel.uiState.value.currentCategory)
    }

    // endregion

    // region 3. カテゴリ管理テスト (Category Management)

    @Test
    fun CAT_01_setCategory_updatesState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setCategory(Category.MEDICATION)
        assertEquals(Category.MEDICATION, viewModel.uiState.value.currentCategory)
    }

    @Test
    fun CAT_02_navigateToCategory_emitsEvent() = runTest {
        val viewModel = createViewModel()
        
        viewModel.viewEvent.test {
            viewModel.navigateToCategory(Category.CONDITION_AT_VISIT)
            assertEquals(PersonDetailViewEvent.NavigateToCategory(Category.CONDITION_AT_VISIT), awaitItem())
        }
    }

    // endregion

    // region 4. ナビゲーションテスト (Navigation)

    @Test
    fun NAV_01_navigateBackToMain() = runTest {
        val viewModel = createViewModel()
        
        viewModel.viewEvent.test {
            viewModel.navigateBackToMain()
            assertEquals(PersonDetailViewEvent.NavigateBackToMain, awaitItem())
        }
    }

    // endregion

    // region 5. 安全性・例外テスト (Safety)

    @Test
    fun ERR_01_loadFailure_safety() = runTest {
        every { personRepository.getPersonById(any()) } returns flow { throw RuntimeException("DB error") }

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        coVerify { auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("DB error") }, any()) }
    }

    @Test
    fun ERR_02_loadPerson_clearsPreviousData() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.person)

        // Mock next load to stay in loading state
        every { personRepository.getPersonById("u2") } returns flow { } 
        
        viewModel.loadPerson("u2")
        
        // Data should be cleared during loading
        assertNull(viewModel.uiState.value.personId)
        assertNull(viewModel.uiState.value.person)
    }

    // endregion
}
