package jp.mydns.fujiwara.carememo.viewmodel

import android.database.sqlite.SQLiteConstraintException
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.CareMemoRepository
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PersonListViewModelTest {

    // Mockオブジェクトの作成
    private val repository = mockk<CareMemoRepository>(relaxed = true)
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
        every { repository.getAllPersons() } returns flowOf(emptyList())
        every { repository.getDeletedPersons() } returns flowOf(emptyList())
        every { repository.getPersonIdsWithHeightWeight() } returns flowOf(emptyList())
        every { repository.getPersonIdsWithPulse() } returns flowOf(emptyList())
        every { repository.getPersonIdsWithBp() } returns flowOf(emptyList())
        every { repository.getPersonIdsWithGlucose() } returns flowOf(emptyList())
        every { repository.getPersonIdsWithCondition() } returns flowOf(emptyList())
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)

        viewModel = PersonListViewModel(repository, userSettingsRepository)
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
        coVerify { repository.insertPerson(testPerson) }
    }

    @Test
    fun `既に登録済みの名前を追加しようとした場合、errorFlowにエラーメッセージがセットされること`() = runTest {
        // insertPersonが呼ばれたらSQLiteConstraintExceptionを投げるように設定
        coEvery { repository.insertPerson(any()) } throws SQLiteConstraintException()

        // Turbineライブラリを使用してStateFlowをテスト
        viewModel.errorFlow.test {
            // 初期値はnull
            assertEquals(null, awaitItem())

            // 実行
            viewModel.addPerson(testPerson)

            // エラーメッセージがセットされることを確認
            assertEquals("この利用者は既に登録されています。", awaitItem())
        }
    }

    @Test
    fun `logicalDeletePersonを実行したとき、RepositoryのlogicalDeletePersonが呼ばれること`() = runTest {
        // 実行
        viewModel.logicalDeletePerson(testPerson)

        // 検証
        coVerify { repository.logicalDeletePerson(testPerson.id) }
    }

    @Test
    fun `setNameMaskingEnabledを実行したとき、UserSettingsRepositoryのメソッドが呼ばれること`() = runTest {
        // 実行
        viewModel.setNameMaskingEnabled(true)

        // 検証
        coVerify { userSettingsRepository.setNameMaskingEnabled(true) }
    }
}
