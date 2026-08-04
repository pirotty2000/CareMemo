@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.repository.*
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * ViewModel層テスト：PersonConditionViewModel (ロジック・安全性)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PC-001_PersonConditionScreen.md に準拠
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonConditionViewModelTest {

    private val conditionRepository = mockk<ConditionRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private lateinit var viewModel: PersonConditionViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testPerson = Person(
        id = "1", lastName = "記録", firstName = "太郎",
        lastNameFurigana = "きろく", firstNameFurigana = "たろう",
        birthday = Instant.now()
    )

    private val testRecords = listOf(
        ConditionAtVisit(id = "1", personId = "1", title = "朝の様子", condition = "元気です", author = "記録者", recordTime = Instant.now()),
        ConditionAtVisit(id = "2", personId = "1", title = "昼の様子", condition = "眠そう", author = "記録者", recordTime = Instant.now())
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        mockkObject(ImageUtils)
        every { Log.e(any(), any(), any()) } returns 0
        coEvery { ImageUtils.processAndSaveImage(any(), any()) } returns ("photo.jpg" to "thumb.jpg")

        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("テスト記録者")
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        every { conditionRepository.getConditionAtVisitByPersonId(any()) } returns flowOf(testRecords)
        every { summaryRepository.getPersonCategorySummaryById(any()) } returns flowOf(PersonCategorySummary())
        coEvery { conditionRepository.findConditionAtTime(any(), any()) } returns null
        
        viewModel = PersonConditionViewModel(
            conditionRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository, mockk<Context>(relaxed = true), SavedStateHandle(mapOf("personId" to "1"))
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
        unmockkObject(ImageUtils)
    }

    @Test
    fun lg_01_データロード失敗時の安全性() = runTest {
        every { conditionRepository.getConditionAtVisitByPersonId(any()) } returns flow { throw RuntimeException("Flow Error") }

        viewModel.loadPerson("1")
        advanceUntilIdle()

        // isLoading が false に戻ること
        assertEquals(false, viewModel.uiState.value.isLoading)
        coVerify { 
            auditLogRepository.log(
                featureName = "PersonCondition", 
                operation = "recordsFlow", 
                tableName = "condition_db",
                actionType = "ERROR", 
                affectedId = any(), 
                resultType = "OTHER_ERROR", 
                details = match { it.contains("Flow Error") }
            ) 
        }
    }

    @Test
    fun lg_02_保存失敗時の安全性() = runTest {
        coEvery { conditionRepository.insertConditionAtVisit(any(), any(), any(), any()) } throws RuntimeException("Save Error")

        viewModel.loadPerson("1")
        advanceUntilIdle()

        viewModel.saveRecord(AppSpecifications.Id.NEW_RECORD_ID, "タイトル", "内容", "著者", Instant.now())
        advanceUntilIdle()

        // isLoading (loadingStateProxy経由) が false に戻ること
        assertEquals(false, viewModel.uiState.value.isLoading)
        coVerify { 
            auditLogRepository.log(
                featureName = "PersonCondition", 
                operation = "saveRecord", 
                tableName = "condition_db",
                actionType = "ERROR", 
                affectedId = AppSpecifications.Id.NEW_RECORD_ID,
                resultType = "OTHER_ERROR", 
                details = match { it.contains("Save Error") }
            ) 
        }
    }

    @Test
    fun lg_03_検索と連動した原子性() = runTest {
        viewModel.loadPerson("1")
        advanceUntilIdle()

        // 初期状態で2件
        assertEquals(2, viewModel.uiState.value.filteredRecords.size)

        // クエリを更新
        viewModel.updateSearchQuery("朝")
        
        // 即座に1件に絞り込まれていること (原子性の検証)
        val state = viewModel.uiState.value
        assertEquals("朝", state.searchQuery)
        assertEquals(1, state.filteredRecords.size)
        assertEquals("朝の様子", state.filteredRecords[0].title)
    }

    @Test
    fun lg_04_写真データの連動() = runTest {
        val conditionId = "100"
        val mockPhotos = listOf(mockk<ConditionPhoto>())
        every { conditionRepository.getConditionPhotosByConditionId(conditionId) } returns flowOf(mockPhotos)

        // ID を選択
        viewModel.setSelectedConditionId(conditionId)
        advanceUntilIdle()

        // 写真リストが購読・更新されていること
        assertEquals(mockPhotos, viewModel.uiState.value.currentConditionPhotos)
    }
}
