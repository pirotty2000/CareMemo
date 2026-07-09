@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("テスト記録者")
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        every { conditionRepository.getConditionAtVisitByPersonId(any()) } returns flowOf(testRecords)
        
        viewModel = PersonConditionViewModel(
            conditionRepository,
            personRepository,
            summaryRepository,
            userSettingsRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
}
