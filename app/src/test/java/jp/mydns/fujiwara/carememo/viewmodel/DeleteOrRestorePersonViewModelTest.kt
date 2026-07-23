@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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

/**
 * SCR-S-003 DeleteOrRestorePersonViewModel のユニットテスト (System B 移行済)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteOrRestorePersonViewModelTest {

    private val repository = mockk<DeleteOrRestorePersonRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)

    private lateinit var viewModel: DeleteOrRestorePersonViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testPerson = Person(id = 1, lastName = "山田", firstName = "太郎", lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ", birthday = Instant.now())

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)

        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { repository.getArchivedPersons() } returns flowOf(listOf(testPerson))

        viewModel = DeleteOrRestorePersonViewModel(repository, userSettingsRepository, auditLogRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun init_loadsArchivedPersons() = runTest(testDispatcher) {
        assertEquals(1, viewModel.uiState.value.archivedPersons.size)
        assertEquals(testPerson, viewModel.uiState.value.archivedPersons[0])
    }

    @Test
    fun setMode_clearsSelection() = runTest(testDispatcher) {
        viewModel.toggleSelection(1)
        assertEquals(setOf(1), viewModel.uiState.value.selectedIds)

        viewModel.setMode(DeleteOrRestorePersonViewModel.OperationMode.DELETE)
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
        assertEquals(DeleteOrRestorePersonViewModel.OperationMode.DELETE, viewModel.uiState.value.mode)
    }

    @Test
    fun toggleSelection_updatesSelectedIds() = runTest(testDispatcher) {
        viewModel.toggleSelection(1)
        assertEquals(setOf(1), viewModel.uiState.value.selectedIds)

        viewModel.toggleSelection(1)
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
    }

    @Test
    fun selectAll_clearSelection_updatesState() = runTest(testDispatcher) {
        viewModel.selectAll(listOf(testPerson))
        assertEquals(setOf(1), viewModel.uiState.value.selectedIds)

        viewModel.clearSelection()
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
    }

    @Test
    fun lg_01_restoreFailure_safety() = runTest(testDispatcher) {
        coEvery { repository.restorePerson(any(), any(), any()) } throws RuntimeException("Restore Error")
        viewModel.toggleSelection(1)

        viewModel.restoreSelectedPersons(listOf(testPerson))

        // Then: isLoading が false に戻ること
        assertEquals(false, viewModel.uiState.value.isLoading)
        coVerify {
            auditLogRepository.log(
                featureName = "DeleteOrRestorePerson",
                operation = "restoreSelectedPersons",
                actionType = "ERROR",
                tableName = "person_db",
                affectedId = any(),
                details = any(),
                resultType = any()
            )
        }
    }

    @Test
    fun lg_02_maskingSetting_syncsToUiState() = runTest(testDispatcher) {
        // 設定が ON の場合
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(true)
        
        // 新しく ViewModel を作成して Flow を購読させる
        val syncViewModel = DeleteOrRestorePersonViewModel(repository, userSettingsRepository, auditLogRepository)
        
        assertEquals(true, syncViewModel.uiState.value.isNameMaskingEnabled)
    }

    @Test
    fun lg_03_atomicClearSelection() = runTest(testDispatcher) {
        viewModel.toggleSelection(1)
        assertEquals(setOf(1), viewModel.uiState.value.selectedIds)

        viewModel.clearSelection()
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
    }

    @Test
    fun lg_04_restore_noSelection_showsError() = runTest(testDispatcher) {
        val events = mutableListOf<BaseUiStateViewModel.UiEvent>()
        val job = backgroundScope.launch {
            viewModel.uiEventFlow.collect { events.add(it) }
        }
        
        // 選択なしで呼び出し
        viewModel.restoreSelectedPersons(listOf(testPerson))
        
        assertTrue(events.any { it is BaseUiStateViewModel.UiEvent.ShowErrorDialogRes })
        
        val errorEvent = events.filterIsInstance<BaseUiStateViewModel.UiEvent.ShowErrorDialogRes>().first()
        assertEquals(jp.mydns.fujiwara.carememo.R.string.archive_err_no_selection, errorEvent.messageResId)

        job.cancel()
    }

    @Test
    fun lg_05_delete_noSelection_showsError() = runTest(testDispatcher) {
        val events = mutableListOf<BaseUiStateViewModel.UiEvent>()
        val job = backgroundScope.launch {
            viewModel.uiEventFlow.collect { events.add(it) }
        }

        // 選択なしで呼び出し
        viewModel.deleteSelectedPersons(listOf(testPerson))

        assertTrue(events.any { it is BaseUiStateViewModel.UiEvent.ShowErrorDialogRes })

        val errorEvent = events.filterIsInstance<BaseUiStateViewModel.UiEvent.ShowErrorDialogRes>().first()
        assertEquals(jp.mydns.fujiwara.carememo.R.string.archive_err_no_selection, errorEvent.messageResId)

        job.cancel()
    }
}
