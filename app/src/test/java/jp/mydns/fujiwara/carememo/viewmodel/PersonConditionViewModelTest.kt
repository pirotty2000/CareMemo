package jp.mydns.fujiwara.carememo.viewmodel

import android.net.Uri
import android.util.Log
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
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
import java.io.IOException
import java.time.Instant

/**
 * ViewModel層テスト：PersonConditionViewModel (ロジック・安全性)
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
        id = 1,
        lastName = "記録",
        firstName = "太郎",
        lastNameFurigana = "きろく",
        firstNameFurigana = "たろう",
        birthday = Instant.now()
    )

    private val testRecords = listOf(
        ConditionAtVisit(
            id = 1,
            personId = 1,
            title = "朝の様子",
            condition = "元気です",
            author = "テスト記録者",
            recordTime = Instant.now()
        ),
        ConditionAtVisit(
            id = 2,
            personId = 1,
            title = "昼の様子",
            condition = "少し眠そう",
            author = "テスト記録者",
            recordTime = Instant.now()
        )
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        mockkObject(ImageUtils)
        every { Log.e(any(), any(), any()) } returns 0
        coEvery { ImageUtils.processAndSaveImage(any(), any()) } returns ("photo.jpg" to "thumb.jpg")
        coEvery { ImageUtils.deleteImageFiles(any(), any(), any()) } returns Unit

        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("テスト記録者")
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        every { conditionRepository.getConditionAtVisitByPersonId(any()) } returns flowOf(testRecords)
        coEvery { conditionRepository.findConditionAtTime(any(), any()) } returns null
        
        viewModel = PersonConditionViewModel(
            conditionRepository,
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
        unmockkObject(ImageUtils)
    }

    @Test
    fun bh01_saveRecord_calls_repository_with_audit_params() = runTest {
        val state = PersonConditionUiState(
            title = "新規",
            condition = "テスト内容",
            author = "テスト記録者",
            recordTime = Instant.now()
        )
        coEvery { conditionRepository.insertConditionAtVisit(any(), any(), any()) } returns 10L

        viewModel.saveRecord(1, 0, state)
        advanceUntilIdle()

        coVerify { conditionRepository.insertConditionAtVisit(match { it.title == "新規" }, "PersonCondition", "saveRecord") }
    }

    @Test
    fun bh03_saveRecord_fails_when_duplicate_datetime() = runTest {
        val recordTime = Instant.now()
        val state = PersonConditionUiState(title = "重複", condition = "内容", author = "A", recordTime = recordTime)
        
        coEvery { conditionRepository.findConditionAtTime(1, recordTime) } returns testRecords[0]

        viewModel.uiEventFlow.test {
            viewModel.saveRecord(1, 0, state)
            val event = awaitItem()
            assert(event is BaseViewModel.UiEvent.ShowErrorDialogRes)
        }

        coVerify(exactly = 0) { conditionRepository.insertConditionAtVisit(any(), any(), any()) }
    }

    @Test
    fun lg01_dataFetchFailure_safety() = runTest {
        every { conditionRepository.getConditionAtVisitByPersonId(any()) } returns flow {
            throw RuntimeException("Flow Error")
        }

        val errorViewModel = PersonConditionViewModel(
            conditionRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository
        )
        errorViewModel.loadPerson(1)
        advanceUntilIdle()

        assertEquals(false, errorViewModel.isLoading.value)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonCondition",
                operation = "loadPersonAndRecords",
                tableName = "condition_db",
                actionType = "ERROR",
                affectedId = "1",
                resultType = "OTHER_ERROR",
                details = match { it?.contains("Flow Error") == true }
            )
        }
    }

    @Test
    fun lg02_saveFailure_safety() = runTest {
        val state = PersonConditionUiState(condition = "内容", author = "A", recordTime = Instant.now())
        coEvery { conditionRepository.insertConditionAtVisit(any(), any(), any()) } throws RuntimeException("Save Error")

        viewModel.saveRecord(1, 1, state)
        advanceUntilIdle()

        assertEquals(false, viewModel.isProcessing.value)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonCondition",
                operation = "saveRecord",
                tableName = "condition_db",
                actionType = "ERROR",
                affectedId = "1",
                resultType = "OTHER_ERROR",
                details = match { it?.contains("Save Error") == true }
            )
        }
    }

    @Test
    fun lg07_validationFailure_translation() = runTest {
        // 空の内容で保存を試みる
        val state = PersonConditionUiState(condition = "", author = "A", recordTime = Instant.now())

        viewModel.saveRecord(1, 0, state)
        advanceUntilIdle()

        // VALIDATION_ERROR として記録されること
        coVerify {
            auditLogRepository.log(
                featureName = "PersonCondition",
                operation = "saveRecord",
                tableName = "condition_db",
                actionType = "ERROR",
                affectedId = "0",
                resultType = "VALIDATION_ERROR",
                details = match { it?.contains("EMPTY_CONDITION") == true }
            )
        }
    }

    @Test
    fun lg04_photoSaveFailure_safety() = runTest {
        val mockUri = mockk<Uri>(relaxed = true)
        coEvery { ImageUtils.processAndSaveImage(any(), any()) } throws IOException("Disk Full")
        
        viewModel.processAndSavePhoto(mockk(relaxed = true), mockUri, 1, 1, "caption")
        advanceUntilIdle()

        assertEquals(false, viewModel.isProcessing.value)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonCondition",
                operation = "processAndSavePhoto",
                tableName = "condition_db",
                actionType = "ERROR",
                affectedId = "1",
                resultType = "IO_ERROR",
                details = match { it?.contains("Disk Full") == true }
            )
        }
    }

    @Test
    fun lg06_notifyPhotoError_sets_errorMessage_and_logs() = runTest {
        viewModel.notifyPhotoError("Test Preparation Error")
        advanceUntilIdle()

        viewModel.errorMessage.test {
            assertEquals("Test Preparation Error", awaitItem())
        }
        
        coVerify {
            auditLogRepository.log(
                featureName = "PersonCondition",
                operation = "photoOperation",
                tableName = "condition_db",
                actionType = "ERROR",
                affectedId = "",
                resultType = "EXTERNAL_ERROR",
                details = match { it?.contains("Test Preparation Error") == true }
            )
        }
    }

    @Test
    fun vml01_search_filtering() = runTest {
        viewModel.loadPerson(1)
        advanceUntilIdle()

        viewModel.filteredRecords.test {
            assertEquals(2, awaitItem().size)

            viewModel.updateSearchQuery("朝")
            val filtered = awaitItem()
            assertEquals(1, filtered.size)
            assertEquals("朝の様子", filtered[0].title)

            viewModel.updateSearchQuery("夜")
            assertEquals(0, awaitItem().size)
        }
    }
}
