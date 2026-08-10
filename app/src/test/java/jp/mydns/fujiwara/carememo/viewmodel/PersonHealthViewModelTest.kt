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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * ViewModel層テスト：PersonHealthViewModel (4. ロジック・安全性)
 * 仕様書項目: LG-01 〜 LG-03
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonHealthViewModelTest {

    private val healthRepository = mockk<HealthRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)

    private lateinit var viewModel: PersonHealthViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testPerson = Person(
        id = "1", lastName = "健康", firstName = "太郎",
        lastNameFurigana = "ケンコウ", firstNameFurigana = "タロウ",
        birthday = Instant.now()
    )

    private val emptySummary = PersonCategorySummary(
        hasHeightWeight = false,
        hasBpAndPulse = false,
        hasGlucoseAndHbA1c = false,
        hasCondition = false,
        hasMedication = false
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        every { summaryRepository.getPersonCategorySummaryById(any()) } returns flowOf(emptySummary)
        
        // 重複チェックをパスさせるための初期設定
        coEvery { healthRepository.findBpAndPulseAtTime(any(), any()) } returns null
        coEvery { healthRepository.findHeightAndWeightAtTime(any(), any()) } returns null
        coEvery { healthRepository.findGlucoseAndHbA1cAtTime(any(), any()) } returns null
        
        viewModel = PersonHealthViewModel(
            healthRepository,
            personRepository,
            summaryRepository,
            userSettingsRepository,
            auditLogRepository,
            SavedStateHandle(mapOf("personId" to "1", "categoryName" to Category.HEIGHT_AND_WEIGHT.name))
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun LG_01_load_failure_safety() = runTest {
        // 特定のカテゴリ（バイタル）のロードでエラーを発生させる
        every { healthRepository.getBpAndPulseByPersonId(any()) } returns flow {
            throw RuntimeException("Load Failure")
        }

        viewModel.loadPerson("1")
        viewModel.setCategory(Category.BP_AND_PULSE)

        // 状態の変化を監視
        viewModel.uiState.test {
            // ローディングが終了していること
            assertEquals(false, awaitItem().isLoading)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { 
            auditLogRepository.log(
                featureName = "PersonHealth",
                operation = "recordsFlow",
                tableName = "health_db",
                actionType = "ERROR",
                affectedId = any(),
                details = match { it.contains("Load Failure") },
                resultType = "OTHER_ERROR"
            ) 
        }
    }

    @Test
    fun LG_02_save_failure_safety() = runTest {
        viewModel.loadPerson("1")
        advanceUntilIdle()

        // 編集セッションを開始して値をセット
        viewModel.setSelectedRecordId(AppSpecifications.Id.NEW_RECORD_ID)
        viewModel.updateEditInput { 
            it.copy(bpSystolicText = "120", bpDiastolicText = "80", pulseText = "70")
        }

        viewModel.saveCurrentEdit()
        advanceUntilIdle()

        // isLoading が false に戻ること
        assertEquals(false, viewModel.uiState.value.isLoading)
        coVerify(exactly = 1) { 
            auditLogRepository.log(
                featureName = "PersonHealth",
                operation = "saveRecord",
                tableName = "health_db",
                actionType = "ERROR",
                affectedId = "",
                details = match { it.contains("Save Failure") },
                resultType = "OTHER_ERROR"
            ) 
        }
    }

    @Test
    @Suppress("USELESS_IS_CHECK")
    fun LG_04_atomicity_category_switch() = runTest {
        viewModel.loadPerson("1")
        advanceUntilIdle()

        // カテゴリを切り替えた際、リポジトリが呼ばれていること
        viewModel.setCategory(Category.GLUCOSE_AND_HBA1C)
        advanceUntilIdle()
        
        verify { healthRepository.getGlucoseAndHbA1cByPersonId("1") is kotlinx.coroutines.flow.Flow<*> }
    }

    @Test
    fun LG_05_edit_session_isChanged_tracking() = runTest {
        viewModel.loadPerson("1")
        advanceUntilIdle()

        // 1. 新規レコードセッション開始
        viewModel.setSelectedRecordId(AppSpecifications.Id.NEW_RECORD_ID)
        
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertEquals(true, initialState.isEditing)
            assertEquals(false, initialState.isChanged)
            assertEquals(false, initialState.isSaveEnabled)

            // 2. 値を変更
            viewModel.updateEditInput { it.copy(heightText = "170.5") }
            val changedState = awaitItem()
            assertEquals(true, changedState.isChanged)
            assertEquals(true, changedState.isSaveEnabled) // バリデーション成功かつ変更あり

            // 3. 値を元に戻す
            viewModel.updateEditInput { it.copy(heightText = "") }
            val revertedState = awaitItem()
            assertEquals(false, revertedState.isChanged)
            assertEquals(false, revertedState.isSaveEnabled)
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun LG_06_edit_session_latest_height_inheritance() = runTest {
        // 前回の記録がある状態を作る
        val lastRecord = HeightAndWeight(id = "prev", personId = "1", height = 165.0, weight = 60.0, recordTime = Instant.now().minusSeconds(3600))
        every { healthRepository.getHeightAndWeightByPersonId("1") } returns flowOf(listOf(lastRecord))

        viewModel.loadPerson("1")
        advanceUntilIdle()

        // 新規作成開始
        viewModel.setCategory(Category.HEIGHT_AND_WEIGHT)
        viewModel.setSelectedRecordId(AppSpecifications.Id.NEW_RECORD_ID)

        assertEquals("165.0", viewModel.uiState.value.editInput.heightText)
    }
}
