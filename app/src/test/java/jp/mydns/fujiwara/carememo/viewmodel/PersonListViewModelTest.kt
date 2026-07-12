@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
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
    private val archivedRepository = mockk<DeleteOrRestorePersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val conditionRepository = mockk<ConditionRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)

    private lateinit var viewModel: PersonListViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    // テスト用の固定日時
    private val fixedInstant = Instant.parse("2023-10-27T10:00:00Z")

    // テスト用の共通Personオブジェクト
    private val testPerson = Person(
        id = 1,
        lastName = "テスト",
        firstName = "太郎",
        lastNameFurigana = "てすと",
        firstNameFurigana = "たろう",
        birthday = fixedInstant
    )

    @Before
    fun setup() {
        // Logクラスの全オーバーロードを確実にモック化
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        // ViewModel内で使用される Dispatchers.Main をテスト用に差し替える
        Dispatchers.setMain(testDispatcher)

        // Repositoryの各メソッドが返すFlowのデフォルト値を設定
        every { personRepository.getAllPersons() } returns flowOf(emptyList())
        every { archivedRepository.getArchivedPersons() } returns flowOf(emptyList())
        every { summaryRepository.getPersonCategorySummaries() } returns flowOf(emptyMap())
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { conditionRepository.getPersonIdsByConditionKeyword(any()) } returns flowOf(emptyList())
        coEvery { personRepository.findExistingPerson(any()) } returns null

        viewModel = PersonListViewModel(
            personRepository,
            archivedRepository,
            summaryRepository,
            conditionRepository,
            userSettingsRepository,
            auditLogRepository
        )
    }

    @After
    fun tearDown() {
        // テスト終了後にメインスレッドの設定をリセットする
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun `addPersonを実行したとき、RepositoryのinsertPersonが正しく呼ばれること`() = runTest {
        // 実行
        viewModel.addPerson(testPerson)

        // 検証: insertPersonが呼ばれたか
        coVerify { personRepository.insertPerson(testPerson, any(), any()) }
    }

    @Test
    fun `既に登録済みの名前を追加しようとした場合、uiEventFlowにErrorDialogイベントが流れること`() = runTest {
        // insertPersonが呼ばれたらSQLiteConstraintExceptionを投げるように設定
        coEvery { personRepository.insertPerson(any(), any(), any()) } throws SQLiteConstraintException()

        // Turbineライブラリを使用してSharedFlowをテスト
        viewModel.uiEventFlow.test {
            // 実行
            viewModel.addPerson(testPerson)

            // エラーイベントが流れることを確認
            val event = awaitItem()
            assertTrue(event is BaseViewModel.UiEvent.ShowErrorDialogRes)
        }
    }

    @Test
    fun `logicalDeletePersonを実行したとき、ArchivedRepositoryのlogicalDeletePersonが呼ばれること`() = runTest {
        // 実行
        viewModel.logicalDeletePerson(testPerson)

        // 検証
        coVerify { archivedRepository.logicalDeletePerson(testPerson.id, any(), any()) }
    }

    @Test
    fun `データ取得時に例外が発生した場合、isLoadingがfalseになり、エラーログが記録されること`() = runTest {
        // ViewModel作成前に、例外を投げるようにモックを設定
        every { personRepository.getAllPersons() } returns flow {
            throw RuntimeException("Load Error")
        }
        
        // 例外設定を反映したViewModelを新規作成
        val errorViewModel = PersonListViewModel(
            personRepository,
            archivedRepository,
            summaryRepository,
            conditionRepository,
            userSettingsRepository,
            auditLogRepository
        )

        // userListを購読してFlowを開始させる
        errorViewModel.userList.test {
            awaitItem() // 初期値の空リストを取得
            
            // 非同期のcatchブロックが完了するのを待機
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(false, errorViewModel.isLoading.value)
            
            coVerify(exactly = 1) {
                auditLogRepository.log(
                    screenName = "PersonList",
                    operation = "userListFlow",
                    tableName = "person_db",
                    actionType = "ERROR",
                    affectedId = any(),
                    details = match { it.contains("Load Error") }
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `利用者追加時に例外が発生した場合、isLoadingがfalseになり、エラーログが記録されること`() = runTest {
        coEvery { personRepository.insertPerson(any(), any(), any()) } throws RuntimeException("Add Error")

        viewModel.addPerson(testPerson)

        // 非同期処理の完了を待機
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.isLoading.value)
        
        coVerify(exactly = 1) {
            auditLogRepository.log(
                screenName = "PersonList",
                operation = "addPerson",
                tableName = "person_db",
                actionType = "ERROR",
                affectedId = any(),
                details = match { it.contains("Add Error") }
            )
        }
    }
}
