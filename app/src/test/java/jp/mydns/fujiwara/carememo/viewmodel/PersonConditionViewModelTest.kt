@file:Suppress("NonAsciiCharacters")

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
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * SCR-PC-001 PersonConditionViewModel のロジックテスト
 * 
 * 仕様書：doc/test/screen/TEST_SPEC_SCR-PC-001_PersonConditionScreen.md に準拠
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonConditionViewModelTest {

    private val conditionRepository = mockk<ConditionRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private lateinit var viewModel: PersonConditionViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

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

    // ======================================================================================
    // 3. 画面全体の挙動・結合テスト (PersonConditionScreen) - ロジック部分
    // ======================================================================================

    @Test
    fun bh01_saveRecord_calls_repository_with_audit_params() = runTest {
        val newRecord = ConditionAtVisit(
            id = 0,
            personId = 1,
            title = "新規",
            condition = "テスト内容",
            author = "テスト記録者",
            recordTime = Instant.now()
        )
        coEvery { conditionRepository.insertConditionAtVisit(any(), any(), any()) } returns 10L

        // BH-01: メモの保存 (ViewModel経由でRepositoryの監査ログ用引数が正しく渡されること)
        viewModel.saveRecord(newRecord)

        coVerify { conditionRepository.insertConditionAtVisit(newRecord, "PersonCondition", "saveRecord") }
    }

    // ======================================================================================
    // 4. ロジック・安全性テスト (PersonConditionViewModel)
    // ======================================================================================

    @Test
    fun lg01_dataFetchFailure_safety() = runTest {
        every { conditionRepository.getConditionAtVisitByPersonId(any()) } returns flow {
            throw RuntimeException("Flow Error")
        }
        
        // 再生成して Flow を起動
        val errorViewModel = PersonConditionViewModel(
            conditionRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository
        )
        errorViewModel.loadPerson(1)

        // LG-01: データ取得失敗時の安全性
        errorViewModel.records.test {
            awaitItem()
            assertEquals(false, errorViewModel.isLoading.value)
            coVerify {
                auditLogRepository.log(
                    featureName = "PersonCondition",
                    operation = "recordsFlow",
                    tableName = "condition_db",
                    actionType = "ERROR",
                    affectedId = any(),
                    details = match { it?.contains("Flow Error") == true },
                    resultType = "OTHER_ERROR"
                )
            }
        }
    }

    @Test
    fun lg02_saveFailure_safety() = runTest {
        val record = testRecords[0]
        coEvery { conditionRepository.insertConditionAtVisit(any(), any(), any()) } throws RuntimeException("Save Error")

        // LG-02: 保存失敗時の安全性
        viewModel.saveRecord(record)

        assertEquals(false, viewModel.isProcessing.value)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonCondition",
                operation = "saveRecord",
                tableName = "condition_db",
                actionType = "ERROR",
                affectedId = any(),
                details = match { it?.contains("Save Error") == true },
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    fun lg03_deleteFailure_safety() = runTest {
        val record = testRecords[0]
        coEvery { conditionRepository.deleteConditionAtVisit(any(), any(), any()) } throws RuntimeException("Delete Error")

        // LG-03: 削除失敗時の安全性
        viewModel.deleteRecord(record)

        assertEquals(false, viewModel.isLoading.value)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonCondition",
                operation = "deleteRecord",
                tableName = "condition_db",
                actionType = "ERROR",
                affectedId = any(),
                details = match { it?.contains("Delete Error") == true },
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    fun lg04_photoSaveFailure_safety() = runTest {
        val mockUri = mockk<Uri>(relaxed = true)
        coEvery { ImageUtils.processAndSaveImage(any(), any()) } throws RuntimeException("Image Process Error")
        
        // LG-04: 写真保存失敗時の安全性
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
                details = match { it?.contains("Image Process Error") == true },
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    fun lg05_photoDeleteFailure_safety() = runTest {
        val photo = ConditionPhoto(id = 1, conditionId = 1, personId = 1, photoFileName = "p.jpg", thumbnailFileName = "t.jpg", capturedAt = Instant.now())
        coEvery { conditionRepository.deleteConditionPhotoById(any(), any(), any(), any()) } throws RuntimeException("Photo Delete Error")

        // LG-05: 写真削除失敗時の安全性
        viewModel.deletePhoto(mockk(relaxed = true), photo)

        assertEquals(false, viewModel.isProcessing.value)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonCondition",
                operation = "deletePhoto",
                tableName = "condition_db",
                actionType = "ERROR",
                affectedId = any(),
                details = match { it?.contains("Photo Delete Error") == true },
                resultType = "OTHER_ERROR"
            )
        }
    }
}
