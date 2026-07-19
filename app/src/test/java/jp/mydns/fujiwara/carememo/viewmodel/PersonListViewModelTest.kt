@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
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

/**
 * SCR-M-001 PersonListViewModel のロジックテスト
 * 
 * 仕様書：doc/test/screen/TEST_SPEC_SCR-M-001_MainScreen.md に準拠
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonListViewModelTest {

    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val archivedRepository = mockk<DeleteOrRestorePersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val conditionRepository = mockk<ConditionRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)

    private lateinit var viewModel: PersonListViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testPerson = Person(
        id = 1,
        lastName = "テスト",
        firstName = "太郎",
        lastNameFurigana = "てすと",
        firstNameFurigana = "たろう",
        birthday = Instant.now()
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)

        every { personRepository.getAllPersons() } returns flowOf(emptyList())
        every { archivedRepository.getArchivedPersons() } returns flowOf(emptyList())
        every { summaryRepository.getPersonCategorySummaries() } returns flowOf(emptyMap())
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { conditionRepository.getPersonIdsByConditionKeyword(any()) } returns flowOf(emptyList())

        viewModel = PersonListViewModel(
            personRepository, archivedRepository, summaryRepository,
            conditionRepository, userSettingsRepository, auditLogRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    // ======================================================================================
    // 3. ロジック・安全性テスト (PersonListViewModel)
    // ======================================================================================

    @Test
    fun lg01_dataFetchFailure_resetsLoadingAndLogsError() = runTest {
        // ViewModel作成前に、例外を投げるようにモックを設定
        every { personRepository.getAllPersons() } returns flow {
            throw RuntimeException("Load Error")
        }
        
        val errorViewModel = PersonListViewModel(
            personRepository, archivedRepository, summaryRepository,
            conditionRepository, userSettingsRepository, auditLogRepository
        )

        errorViewModel.uiState.test {
            awaitItem() // 初期値
            
            // Then: isLoading が false になり、監査ログに ERROR が記録されること
            assertEquals(false, errorViewModel.uiState.value.isLoading)
            
            coVerify {
                auditLogRepository.log(
                    featureName = "PersonList",
                    operation = "userListFlow",
                    actionType = "ERROR",
                    tableName = "person_db",
                    affectedId = any(),
                    details = any(),
                    resultType = any()
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun lg02_saveOrDeleteFailure_resetsLoadingAndLogsError() = runTest {
        coEvery { personRepository.insertPerson(any(), any(), any()) } throws RuntimeException("Add Error")
        coEvery { personRepository.findExistingPerson(any()) } returns null

        viewModel.addPerson(testPerson)
        
        // Then: isLoading が false になり、監査ログに ERROR が記録されること
        assertEquals(false, viewModel.uiState.value.isLoading)
        
        coVerify {
            auditLogRepository.log(
                featureName = "PersonList",
                operation = "addPerson",
                actionType = "ERROR",
                tableName = "person_db",
                affectedId = any(),
                details = any(),
                resultType = any()
            )
        }
    }

    @Test
    fun lg03_setSearchQuery_updatesQueryAndResetsSection() = runTest {
        viewModel.setSelectedSection("か")
        assertEquals("か", viewModel.uiState.value.selectedSection)

        viewModel.setSearchQuery("テスト")
        
        // Then: 検索クエリが更新され、セクションが「全」にリセットされること (原子性の検証)
        assertEquals("テスト", viewModel.uiState.value.searchQuery)
        assertEquals("全", viewModel.uiState.value.selectedSection)
    }

    @Test
    fun lg04_maskingSetting_syncsToUiState() = runTest {
        // 設定が ON の場合
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(true)
        
        // 新しく ViewModel を作成して Flow を購読させる
        val syncViewModel = PersonListViewModel(
            personRepository, archivedRepository, summaryRepository,
            conditionRepository, userSettingsRepository, auditLogRepository
        )
        
        // TestDispatcher でコルーチンを回す
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, syncViewModel.uiState.value.isNameMaskingEnabled)
    }
}
