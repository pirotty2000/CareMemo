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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

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

    @Test
    fun `loadPersonを実行したとき、検索クエリと選択IDが初期化されること`() = runTest {
        // 事前に値をセット
        viewModel.updateSearchQuery("検索ワード")
        viewModel.setSelectedConditionId(99)

        // 別のIDでロード
        viewModel.loadPerson(2)

        assertEquals("", viewModel.searchQuery.value)
        assertEquals(null, viewModel.selectedConditionId.value)
    }

    @Test
    fun `filteredRecordsは検索クエリに基づいてフィルタリングされること`() = runTest {
        viewModel.loadPerson(1)

        // クエリなし
        viewModel.updateSearchQuery("")
        viewModel.filteredRecords.test {
            assertEquals(2, awaitItem().size)
        }

        // タイトルで検索
        viewModel.updateSearchQuery("朝")
        viewModel.filteredRecords.test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("朝の様子", result[0].title)
        }

        // 内容で検索
        viewModel.updateSearchQuery("眠そう")
        viewModel.filteredRecords.test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("少し眠そう", result[0].condition)
        }
    }

    @Test
    fun `saveRecordを実行したとき、Repositoryの保存メソッドが呼ばれること`() = runTest {
        val newRecord = ConditionAtVisit(
            id = 0,
            personId = 1,
            title = "新規",
            condition = "テスト内容",
            author = "テスト記録者",
            recordTime = Instant.now()
        )
        coEvery { conditionRepository.insertConditionAtVisit(any(), any(), any()) } returns 10L

        viewModel.saveRecord(newRecord)

        coVerify { conditionRepository.insertConditionAtVisit(newRecord, "PersonCondition", "saveRecord") }
        // 新規なのでリンク処理も呼ばれるはず
        coVerify { conditionRepository.linkTemporaryPhotosToRecord(1, 10, any(), any()) }
    }

    @Test
    fun `deleteRecordを実行したとき、Repositoryの削除メソッドが呼ばれること`() = runTest {
        val record = testRecords[0]
        viewModel.deleteRecord(record)

        coVerify { conditionRepository.deleteConditionAtVisit(record, "PersonCondition", "deleteRecord") }
    }

    @Test
    fun `saveRecord成功時にSnackbar表示イベントが発行されること`() = runTest {
        val record = testRecords[0].copy(id = 1) // 更新
        coEvery { conditionRepository.insertConditionAtVisit(any(), any(), any()) } returns 1L

        viewModel.uiEventFlow.test {
            viewModel.saveRecord(record)
            val event = awaitItem()
            assertTrue(event is BaseViewModel.UiEvent.ShowSnackbarRes)
        }
    }

    // --- ロジック・安全性テスト (LG-01 〜 LG-05) ---

    @Test
    fun `LG-01_データ取得失敗時にisLoadingがfalseになり監査ログが記録されること`() = runTest {
        every { conditionRepository.getConditionAtVisitByPersonId(any()) } returns flow {
            throw RuntimeException("Flow Error")
        }
        
        // Flow を再実行させるために ViewModel を再生成
        val errorViewModel = PersonConditionViewModel(
            conditionRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository
        )
        
        errorViewModel.loadPerson(1)

        errorViewModel.records.test {
            awaitItem() // 初期値 or エラー前の値がある場合はそれ、今回は初期値
            assertEquals(false, errorViewModel.isLoading.value)
            coVerify {
                auditLogRepository.log(
                    featureName = "PersonCondition",
                    operation = "recordsFlow",
                    tableName = "condition_db",
                    actionType = "ERROR",
                    affectedId = any(),
                    details = match { it?.contains("Flow Error") == true }
                )
            }
        }
    }

    @Test
    fun `LG-02_保存失敗時にisProcessingがfalseになり監査ログが記録されること`() = runTest {
        val record = testRecords[0]
        coEvery { conditionRepository.insertConditionAtVisit(any(), any(), any()) } throws RuntimeException("Save Error")

        viewModel.saveRecord(record)

        assertEquals(false, viewModel.isProcessing.value)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonCondition",
                operation = "saveRecord",
                tableName = "condition_db",
                actionType = "ERROR",
                affectedId = any(),
                details = match { it?.contains("Save Error") == true }
            )
        }
    }

    @Test
    fun `LG-03_削除失敗時にisLoadingがfalseになり監査ログが記録されること`() = runTest {
        val record = testRecords[0]
        coEvery { conditionRepository.deleteConditionAtVisit(any(), any(), any()) } throws RuntimeException("Delete Error")

        viewModel.deleteRecord(record)

        assertEquals(false, viewModel.isLoading.value)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonCondition",
                operation = "deleteRecord",
                tableName = "condition_db",
                actionType = "ERROR",
                affectedId = any(),
                details = match { it?.contains("Delete Error") == true }
            )
        }
    }

    @Test
    fun `LG-04_写真保存失敗時にisProcessingがfalseになり監査ログが記録されること`() = runTest {
        val mockUri = mockk<Uri>(relaxed = true)
        
        // ImageUtils をモックして例外を投げさせる (suspend 関数なので coEvery)
        coEvery { ImageUtils.processAndSaveImage(any(), any()) } throws RuntimeException("Image Process Error")
        
        viewModel.processAndSavePhoto(mockk(relaxed = true), mockUri, 1, 1, "caption")
        
        // 内部のコルーチン完了を待機
        advanceUntilIdle()

        assertEquals(false, viewModel.isProcessing.value)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonCondition",
                operation = "processAndSavePhoto",
                tableName = "condition_db",
                actionType = "ERROR",
                affectedId = "1",
                details = match { it?.contains("Image Process Error") == true }
            )
        }
    }

    @Test
    fun `LG-05_写真削除失敗時にisProcessingがfalseになり監査ログが記録されること`() = runTest {
        val photo = ConditionPhoto(id = 1, conditionId = 1, personId = 1, photoFileName = "p.jpg", thumbnailFileName = "t.jpg", capturedAt = Instant.now())
        coEvery { conditionRepository.deleteConditionPhotoById(any(), any(), any(), any()) } throws RuntimeException("Photo Delete Error")

        viewModel.deletePhoto(mockk(relaxed = true), photo)

        assertEquals(false, viewModel.isProcessing.value)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonCondition",
                operation = "deletePhoto",
                tableName = "condition_db",
                actionType = "ERROR",
                affectedId = any(),
                details = match { it?.contains("Photo Delete Error") == true }
            )
        }
    }
}
