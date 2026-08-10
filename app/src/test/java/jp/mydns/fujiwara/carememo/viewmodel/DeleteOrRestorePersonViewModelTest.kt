package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonViewEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Logic Test: DeleteOrRestorePersonViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteOrRestorePersonViewModelTest {

    private val repository = mockk<DeleteOrRestorePersonRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    
    private val testPerson = Person(
        id = "u1",
        lastName = "山田",
        firstName = "太郎",
        lastNameFurigana = "ヤマダ",
        firstNameFurigana = "タロウ",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )
    
    private val isNameMaskingEnabledFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)

        every { userSettingsRepository.isNameMaskingEnabled } returns isNameMaskingEnabledFlow
        every { repository.getArchivedPersons() } returns flowOf(listOf(testPerson))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun createViewModel(mode: String? = null): DeleteOrRestorePersonViewModel {
        val handle = if (mode != null) SavedStateHandle(mapOf("mode" to mode)) else SavedStateHandle()
        return DeleteOrRestorePersonViewModel(repository, userSettingsRepository, auditLogRepository, handle)
    }

    // region 2. 初期化・データロードテスト (Initialization)

    @Test
    fun INI_01_initialLoad_success() = runTest {
        val viewModel = createViewModel()
        
        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.isLoading)
            
            advanceUntilIdle()
            
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(1, state.archivedPersons.size)
            assertEquals("u1", state.archivedPersons[0].id)
        }
    }

    @Test
    fun INI_02_modeSpecifiedAtLaunch() = runTest {
        val viewModel = createViewModel(mode = "DELETE")
        assertEquals(DeleteOrRestorePersonViewModel.OperationMode.DELETE, viewModel.uiState.value.mode)
    }

    @Test
    fun INI_03_maskingSettingReflection() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        
        isNameMaskingEnabledFlow.value = true
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.isNameMaskingEnabled)
    }

    // endregion

    // region 3. 選択状態管理テスト (Selection)

    @Test
    fun SEL_01_toggleSelection() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection("u1")
        assertTrue(viewModel.uiState.value.selectedIds.contains("u1"))

        viewModel.toggleSelection("u1")
        assertFalse(viewModel.uiState.value.selectedIds.contains("u1"))
    }

    @Test
    fun SEL_02_modeSwitch_clearsSelection() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection("u1")
        viewModel.setMode(DeleteOrRestorePersonViewModel.OperationMode.DELETE)
        
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
        assertEquals(DeleteOrRestorePersonViewModel.OperationMode.DELETE, viewModel.uiState.value.mode)
    }

    @Test
    fun SEL_03_SEL_04_batchSelectionActions() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectAll(listOf(testPerson))
        assertEquals(setOf("u1"), viewModel.uiState.value.selectedIds)

        viewModel.clearSelection()
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
    }

    // endregion

    // region 4. 処理実行テスト (Execution)

    @Test
    fun EXE_01_restoreSelectedPersons_success() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection("u1")
        viewModel.restoreSelectedPersons(listOf(testPerson))
        advanceUntilIdle()

        coVerify { repository.restorePersonsBatch(listOf("u1"), any(), any()) }
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
    }

    @Test
    fun EXE_02_deleteSelectedPersons_success() = runTest {
        val viewModel = createViewModel()
        viewModel.setMode(DeleteOrRestorePersonViewModel.OperationMode.DELETE)
        advanceUntilIdle()

        viewModel.toggleSelection("u1")
        viewModel.deleteSelectedPersons(listOf(testPerson))
        advanceUntilIdle()

        coVerify { repository.permanentlyDeletePersonsBatch(listOf("u1"), any(), any()) }
    }

    @Test
    fun EXE_03_execution_failsWithNoSelection() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.restoreSelectedPersons(listOf(testPerson))
        
        viewModel.uiEventFlow.test {
            val event = awaitItem()
            assertTrue(event is BaseUiStateViewModel.UiEvent.ShowErrorDialogRes)
            assertEquals(R.string.archive_err_no_selection, (event as BaseUiStateViewModel.UiEvent.ShowErrorDialogRes).messageResId)
        }
    }

    @Test
    fun EXE_04_execution_failsSafetyOnException() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { repository.restorePersonsBatch(any(), any(), any()) } throws RuntimeException("Process Error")
        
        viewModel.toggleSelection("u1")
        viewModel.restoreSelectedPersons(listOf(testPerson))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        coVerify {
            auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("Process Error") }, any())
        }
    }

    // endregion

    // region 5. ナビゲーションテスト (Navigation)

    @Test
    fun NAV_01_navigateBack() = runTest {
        val viewModel = createViewModel()
        viewModel.viewEvent.test {
            viewModel.navigateBack()
            assertEquals(DeleteOrRestorePersonViewEvent.NavigateBack, awaitItem())
        }
    }

    // endregion
}
