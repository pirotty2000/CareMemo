@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * ViewModel層テスト：BatchInputViewModel (ロジック・安全性)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PH-002_BatchInputScreen.md に準拠
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchInputViewModelTest {

    private val healthRepository = mockk<HealthRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val testPerson = Person(
        id = "1",
        lastName = "健康",
        firstName = "太郎",
        lastNameFurigana = "ケンコウ",
        firstNameFurigana = "タロウ",
        birthday = Instant.now()
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("")
        coEvery { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        coEvery { summaryRepository.getPersonCategorySummaryById(any()) } returns flowOf(
            jp.mydns.fujiwara.carememo.data.PersonCategorySummary(
                hasHeightWeight = false,
                hasBpAndPulse = false,
                hasGlucoseAndHbA1c = false,
                hasCondition = false,
                hasMedication = false
            )
        )
        
        coEvery { healthRepository.findHeightAndWeightAtTime(any(), any()) } returns null
        coEvery { healthRepository.findBpAndPulseAtTime(any(), any()) } returns null
        coEvery { healthRepository.findGlucoseAndHbA1cAtTime(any(), any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun lg_01_一括保存失敗時の安全性() = runTest {
        val viewModel = BatchInputViewModel(healthRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository, SavedStateHandle(mapOf("personId" to "1")))
        advanceUntilIdle()

        viewModel.updateWeight("60")
        coEvery { healthRepository.insertHeightAndWeight(any(), any(), any()) } throws RuntimeException("Batch Save Failure")

        viewModel.saveBatch()
        advanceUntilIdle()

        // isLoading が false に戻ること
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 1) { 
            auditLogRepository.log(
                featureName = "BatchInput",
                operation = "saveBatch",
                tableName = "health_db",
                actionType = "ERROR",
                affectedId = "1",
                resultType = "OTHER_ERROR",
                details = match { it.contains("Batch Save Failure") }
            )
        }
    }

    @Test
    fun lg_02_バリデーション結果の翻訳() = runTest {
        val viewModel = BatchInputViewModel(healthRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository, SavedStateHandle(mapOf("personId" to "1")))
        advanceUntilIdle()

        // 形式不正な入力
        viewModel.updateWeight("abc")

        viewModel.saveBatch()
        advanceUntilIdle()

        coVerify {
            auditLogRepository.log(
                featureName = "BatchInput",
                operation = "saveBatch",
                tableName = "health_db",
                actionType = "ERROR",
                affectedId = "1",
                resultType = "VALIDATION_ERROR",
                details = match { it.contains("INVALID_VALUE") }
            )
        }
    }

    @Test
    fun lg_03_重複カテゴリの識別() = runTest {
        val viewModel = BatchInputViewModel(healthRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository, SavedStateHandle(mapOf("personId" to "1")))
        advanceUntilIdle()

        viewModel.updateWeight("60")
        
        // 重複がある状態をシミュレート
        coEvery { healthRepository.findHeightAndWeightAtTime(any(), any()) } returns mockk<HeightAndWeight>()

        viewModel.saveBatch()
        advanceUntilIdle()

        coVerify {
            auditLogRepository.log(
                featureName = "BatchInput",
                operation = "saveBatch",
                tableName = "health_db",
                actionType = "ERROR",
                affectedId = "1",
                resultType = "VALIDATION_ERROR",
                details = match { it.contains("HEIGHT_WEIGHT") }
            )
        }
    }

    @Test
    fun lg_04_状態の原子性() = runTest {
        val viewModel = BatchInputViewModel(healthRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository, SavedStateHandle(mapOf("personId" to "1")))
        advanceUntilIdle()

        // 初期状態: 全項目空、変更なし
        assertFalse(viewModel.uiState.value.isChanged)
        assertFalse(viewModel.uiState.value.isValid)

        // 数値を入力
        viewModel.updateWeight("60")
        
        // 即座に変更あり、かつ有効（体重のみでも有効）になること
        val state = viewModel.uiState.value
        assertEquals("60", state.weight)
        assertTrue(state.isChanged)
        assertTrue(state.isValid)
        
        // 日時を変更
        val nextTime = state.recordTime.plusSeconds(3600)
        viewModel.setRecordTime(nextTime)
        
        // 日時が更新されると同時に、変更ありフラグが維持されていること
        assertEquals(nextTime, viewModel.uiState.value.recordTime)
        assertTrue(viewModel.uiState.value.isChanged)
    }
}
