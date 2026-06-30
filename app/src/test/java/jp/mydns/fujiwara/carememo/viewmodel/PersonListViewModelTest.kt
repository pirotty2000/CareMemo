package jp.mydns.fujiwara.carememo.viewmodel

import android.database.sqlite.SQLiteConstraintException
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.Person
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
class PersonListViewModelTest {

    // Mockオブジェクトの作成
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val conditionRepository = mockk<ConditionRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)

    private lateinit var viewModel: PersonListViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    // テスト用の共通Personオブジェクト
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
        // ViewModel内で使用される Dispatchers.Main をテスト用に差し替える
        Dispatchers.setMain(testDispatcher)

        // Repositoryの各メソッドが返すFlowのデフォルト値を設定
        every { personRepository.getAllPersons() } returns flowOf(emptyList())
        every { personRepository.getDeletedPersons() } returns flowOf(emptyList())
        every { personRepository.getPersonCategorySummaries() } returns flowOf(emptyMap())
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { conditionRepository.getPersonIdsByConditionKeyword(any()) } returns flowOf(emptyList())

        viewModel = PersonListViewModel(personRepository, conditionRepository, userSettingsRepository)
    }

    @After
    fun tearDown() {
        // テスト終了後にメインスレッドの設定をリセットする
        Dispatchers.resetMain()
    }

    @Test
    fun `addPersonを実行したとき、RepositoryのinsertPersonが正しく呼ばれること`() = runTest {
        // 実行
        viewModel.addPerson(testPerson)

        // 検証: insertPersonが呼ばれたか
        coVerify { personRepository.insertPerson(testPerson) }
    }

    @Test
    fun `既に登録済みの名前を追加しようとした場合、uiEventFlowにErrorDialogイベントが流れること`() = runTest {
        // insertPersonが呼ばれたらSQLiteConstraintExceptionを投げるように設定
        coEvery { personRepository.insertPerson(any()) } throws SQLiteConstraintException()

        // Turbineライブラリを使用してSharedFlowをテスト
        viewModel.uiEventFlow.test {
            // 実行
            viewModel.addPerson(testPerson)

            // エラーイベントが流れることを確認
            val event = awaitItem()
            assertTrue(event is BaseViewModel.UiEvent.ShowErrorDialog)
            assertEquals("登録エラー", (event as BaseViewModel.UiEvent.ShowErrorDialog).title)
        }
    }

    @Test
    fun `logicalDeletePersonを実行したとき、RepositoryのlogicalDeletePersonが呼ばれること`() = runTest {
        // 実行
        viewModel.logicalDeletePerson(testPerson)

        // 検証
        coVerify { personRepository.logicalDeletePerson(testPerson.id) }
    }
}
