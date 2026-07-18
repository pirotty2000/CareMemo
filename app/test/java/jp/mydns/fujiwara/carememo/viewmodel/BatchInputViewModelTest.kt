package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
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
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * ViewModel層テスト：BatchInputViewModel (ロジック・安全性)
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
        id = 1,
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
    fun lg01_batch_save_failure_safety() = runTest {
        val viewModel = BatchInputViewModel(healthRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
        viewModel.loadPerson(1)
        advanceUntilIdle()

        viewModel.updateWeight("60")
        coEvery { healthRepository.insertHeightAndWeight(any(), any(), any()) } throws RuntimeException("Batch Save Failure")

        viewModel.saveBatch()
        advanceUntilIdle()

        assertEquals(false, viewModel.isSaving.value)
        coVerify(exactly = 1) { 
            auditLogRepository.log(
                featureName = "BatchInput",
                operation = "saveBatch",
                tableName = "health_db",
                actionType = "ERROR",
                affectedId = "1",
                resultType = "OTHER_ERROR",
                details = match { it?.contains("Batch Save Failure") == true }
            )
        }
    }

    @Test
    fun lg02_validation_failure_translation() = runTest {
        val viewModel = BatchInputViewModel(healthRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
        viewModel.loadPerson(1)
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
                details = match { it?.contains("INVALID_VALUE") == true }
            )
        }
    }

    @Test
    fun lg03_duplicate_category_identification() = runTest {
        val viewModel = BatchInputViewModel(healthRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
        viewModel.loadPerson(1)
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
                details = match { it?.contains("HEIGHT_WEIGHT") == true }
            )
        }
    }
}
