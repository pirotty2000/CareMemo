package jp.mydns.fujiwara.carememo.viewmodel

import android.net.Uri
import android.util.Log
import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.AppMaintenanceRepository
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.SettingsViewEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Logic Test: SettingsViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val maintenanceRepository = mockk<AppMaintenanceRepository>(relaxed = true)
    private val archivedPersonRepository = mockk<DeleteOrRestorePersonRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val securitySession = SecuritySession()
    
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)
        
        // Default Flow setup
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.isBiometricEnabled } returns flowOf(false)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("")
        every { userSettingsRepository.isBackupPasswordEnabled } returns flowOf(true)
        every { userSettingsRepository.backupPassword } returns flowOf("")
        every { userSettingsRepository.themeSetting } returns flowOf(ThemeSetting.SYSTEM)
        every { userSettingsRepository.auditLogRetentionDays } returns flowOf(30)
        
        every { auditLogRepository.getAuditLogCountFlow() } returns flowOf(10)
        every { archivedPersonRepository.getArchivedPersons() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun createViewModel() = SettingsViewModel(
        maintenanceRepository,
        archivedPersonRepository,
        auditLogRepository,
        userSettingsRepository,
        securitySession
    )

    // region 2. 初期化・設定同期テスト (Initialization & Sync)

    @Test
    fun INI_01_initialSettingsSync_success() = runTest {
        val viewModel = createViewModel()
        
        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertFalse(state.isNameMaskingEnabled)
            assertEquals(10, state.auditLogCount)
            assertEquals(ThemeSetting.SYSTEM, state.themeSetting)
        }
    }

    @Test
    fun INI_03_syncFailure_safety() = runTest {
        every { userSettingsRepository.isNameMaskingEnabled } returns flow {
            throw RuntimeException("Sync Error")
        }

        createViewModel()
        advanceUntilIdle()
            
        coVerify {
            auditLogRepository.log(any(), any(), "all_db", "ERROR", any(), match { it.contains("Sync Error") }, any())
        }
    }

    // endregion

    // region 3. 設定更新テスト (Settings Updates)

    @Test
    fun SET_01_setNameMaskingEnabled_callsRepository() = runTest {
        val viewModel = createViewModel()
        viewModel.setNameMaskingEnabled(true)
        advanceUntilIdle()
        coVerify { userSettingsRepository.setNameMaskingEnabled(true) }
    }

    @Test
    fun DEV_01_handleVersionClick_enablesDevMode() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Click 7 times (threshold defined in AppSpecifications/SettingsLogic)
        repeat(7) { viewModel.handleVersionClick() }
        
        assertTrue(viewModel.uiState.value.isDeveloperModeEnabled)
    }

    // endregion

    // region 4. メンテナンス操作テスト (Maintenance Ops)

    @Test
    fun MNT_01_exportData_success() = runTest {
        val viewModel = createViewModel()
        val uri = mockk<Uri>(relaxed = true)
        advanceUntilIdle()

        viewModel.viewEvent.test {
            viewModel.exportData(uri)
            advanceUntilIdle()
            assertEquals(SettingsViewEvent.ExportSuccess, awaitItem())
        }
        coVerify { maintenanceRepository.exportData(uri, any(), any()) }
    }

    @Test
    fun MNT_02_exportAuditLogs_success() = runTest {
        val viewModel = createViewModel()
        val uri = mockk<Uri>(relaxed = true)
        advanceUntilIdle()

        viewModel.exportAuditLogs(uri)
        advanceUntilIdle()

        coVerify { maintenanceRepository.exportAuditLogs(uri, any(), any()) }
    }

    @Test
    fun MNT_03_importData_passwordError_emitsRequestEvent() = runTest {
        val viewModel = createViewModel()
        val uri = mockk<Uri>(relaxed = true)
        advanceUntilIdle()

        coEvery { maintenanceRepository.importData(any(), any(), any(), any()) } throws IOException("Wrong password")

        viewModel.viewEvent.test {
            viewModel.importData(uri, "suffix")
            advanceUntilIdle()
            assertEquals(SettingsViewEvent.RequestImportPassword, awaitItem())
        }
    }

    @Test
    fun MNT_05_checkIntegrity_updatesState() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val mockResults = listOf(mockk<jp.mydns.fujiwara.carememo.data.DatabaseInconsistency>())
        coEvery { maintenanceRepository.scanInconsistencies() } returns mockResults

        viewModel.checkIntegrity()
        advanceUntilIdle()

        assertEquals(mockResults, viewModel.uiState.value.inconsistencies)
    }

    // endregion

    // region 5. 安全性・例外テスト (Safety)

    @Test
    fun ERR_01_exportFailure_safety() = runTest {
        val viewModel = createViewModel()
        val uri = mockk<Uri>(relaxed = true)
        advanceUntilIdle()

        coEvery { maintenanceRepository.exportData(any(), any(), any()) } throws RuntimeException("Export Error")

        viewModel.exportData(uri)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isProcessing)
        coVerify { auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("Export Error") }, any()) }
    }

    // endregion

    // region 6. ナビゲーションテスト (Navigation)

    @Test
    fun NAV_01_navigateToAuditLog() = runTest {
        val viewModel = createViewModel()
        viewModel.viewEvent.test {
            viewModel.navigateToAuditLog()
            assertEquals(SettingsViewEvent.NavigateToAuditLog, awaitItem())
        }
    }

    // endregion
}
