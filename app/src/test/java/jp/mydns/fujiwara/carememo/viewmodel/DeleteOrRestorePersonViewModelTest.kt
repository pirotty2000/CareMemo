@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteOrRestorePersonViewModelTest {

    private val repository = mockk<DeleteOrRestorePersonRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private lateinit var viewModel: DeleteOrRestorePersonViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testPerson = Person(
        id = 1,
        lastName = "削除済",
        firstName = "太郎",
        lastNameFurigana = "さくじょずみ",
        firstNameFurigana = "たろう",
        birthday = Instant.now()
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
        
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("")
        every { repository.getArchivedPersons() } returns flowOf(listOf(testPerson))

        viewModel = DeleteOrRestorePersonViewModel(
            repository,
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
    fun `OK-01_モードを切り替えたとき選択状態がクリアされること`() = runTest {
        viewModel.toggleSelection(1)
        assertTrue(viewModel.selectedIds.value.contains(1))

        viewModel.setMode(DeleteOrRestorePersonViewModel.OperationMode.DELETE)
        assertTrue(viewModel.selectedIds.value.isEmpty())
    }

    @Test
    fun `OK-02_利用者の選択状態を正しく切り替えられること`() = runTest {
        viewModel.toggleSelection(1)
        assertTrue(viewModel.selectedIds.value.contains(1))

        viewModel.toggleSelection(1)
        assertFalse(viewModel.selectedIds.value.contains(1))
    }

    @Test
    fun `OK-03_全選択および全解除が正しく機能すること`() = runTest {
        val persons = listOf(
            testPerson.copy(id = 1),
            testPerson.copy(id = 2),
            testPerson.copy(id = 3)
        )
        
        // 全選択
        viewModel.selectAll(persons)
        assertEquals(3, viewModel.selectedIds.value.size)
        assertTrue(viewModel.selectedIds.value.containsAll(listOf(1, 2, 3)))

        // 全解除
        viewModel.clearSelection()
        assertTrue(viewModel.selectedIds.value.isEmpty())
    }

    // --- ロジック・安全性テスト (LG-01 〜 LG-03) ---

    @Test
    fun `LG-01_アーカイブ一覧取得失敗時にisLoadingがfalseになり監査ログが記録されること`() = runTest {
        every { repository.getArchivedPersons() } returns flow {
            throw RuntimeException("Flow Error")
        }

        // ViewModelを再生成して例外を発生させる
        val errorViewModel = DeleteOrRestorePersonViewModel(
            repository, userSettingsRepository, auditLogRepository
        )

        errorViewModel.archivedPersonList.test {
            awaitItem() // 初期値 or エラー
            advanceUntilIdle()

            assertEquals(false, errorViewModel.isLoading.value)
            coVerify {
                auditLogRepository.log(
                    featureName = "DeleteOrRestorePerson",
                    operation = "archivedPersonListFlow",
                    tableName = "person_db",
                    actionType = "ERROR",
                    affectedId = "0",
                    details = match { it.contains("Flow Error") },
                    resultType = "OTHER_ERROR"
                )
            }
        }
    }

    @Test
    fun `LG-02_利用者復元失敗時にisLoadingがfalseになり監査ログが記録されること`() = runTest {
        coEvery { repository.restorePerson(any(), any(), any()) } throws RuntimeException("Restore Error")

        viewModel.toggleSelection(1)
        viewModel.restoreSelectedPersons(listOf(testPerson))
        advanceUntilIdle()

        assertEquals(false, viewModel.isLoading.value)
        coVerify {
            auditLogRepository.log(
                featureName = "DeleteOrRestorePerson",
                operation = "restoreSelectedPersons",
                tableName = "person_db",
                actionType = "ERROR",
                affectedId = "0",
                details = match { it.contains("Restore Error") },
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    fun `LG-03_利用者完全抹消失敗時にisLoadingがfalseになり監査ログが記録されること`() = runTest {
        coEvery { repository.permanentlyDeletePerson(any(), any(), any()) } throws RuntimeException("Delete Error")

        viewModel.toggleSelection(1)
        viewModel.deleteSelectedPersons(listOf(testPerson))
        advanceUntilIdle()

        assertEquals(false, viewModel.isLoading.value)
        coVerify {
            auditLogRepository.log(
                featureName = "DeleteOrRestorePerson",
                operation = "deleteSelectedPersons",
                tableName = "person_db",
                actionType = "ERROR",
                affectedId = "0",
                details = match { it.contains("Delete Error") },
                resultType = "OTHER_ERROR"
            )
        }
    }
}
