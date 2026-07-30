package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
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
            auditLogRepository
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
        // 新規レコード (id="")
        val time = Instant.now()
        val values = mapOf("bpSystolic" to 120, "bpDiastolic" to 80, "pulse" to 70)
        coEvery { healthRepository.insertBpAndPulse(any(), any(), any(), any()) } throws RuntimeException("Save Failure")

        viewModel.saveRecord(Category.BP_AND_PULSE, "", time, values)
        
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
}
