@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import jp.mydns.fujiwara.carememo.data.repository.AppMaintenanceRepository
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.utils.ImageUtils
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
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * SCR-S-001 SettingsViewModel のユニットテスト (System B 移行済)
 * 
 * 仕様書：doc/test/screen/TEST_SPEC_SCR-S-001_SettingsScreen.md に準拠
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val maintenanceRepository = mockk<AppMaintenanceRepository>(relaxed = true)
    private val archivedPersonRepository = mockk<DeleteOrRestorePersonRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    
    private lateinit var viewModel: SettingsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        mockkObject(ImageUtils)
        every { Log.e(any(), any(), any()) } returns 0
        coEvery { ImageUtils.clearPhotosDir(any()) } returns Unit

        Dispatchers.setMain(testDispatcher)
        
        // デフォルトのFlow設定
        every { userSettingsRepository.isBiometricEnabled } returns flowOf(false)
        every { userSettingsRepository.lockTimeoutMinutes } returns flowOf(0)
        every { userSettingsRepository.isBackupPasswordEnabled } returns flowOf(true)
        every { userSettingsRepository.backupPassword } returns flowOf("")
        every { userSettingsRepository.themeSetting } returns flowOf(jp.mydns.fujiwara.carememo.data.ThemeSetting.SYSTEM)
        every { userSettingsRepository.auditLogRetentionDays } returns flowOf(30)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("")
        
        every { auditLogRepository.allLogs } returns flowOf(emptyList())
        every { auditLogRepository.getAuditLogCountFlow() } returns flowOf(0)
        every { archivedPersonRepository.getArchivedPersons() } returns flowOf(emptyList())

        viewModel = SettingsViewModel(
            maintenanceRepository,
            archivedPersonRepository,
            auditLogRepository,
            userSettingsRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
        unmockkObject(ImageUtils)
    }

    // ======================================================================================
    // 3. ロジック・安全性テスト (SettingsViewModel)
    // ======================================================================================

    @Test
    fun lg_01_exportData_safetyOnFailure() = runTest {
        val context = mockk<Context>(relaxed = true)
        val uri = mockk<Uri>(relaxed = true)
        
        // 現時点では未実装エラーが出ることを期待（System B 移行時のスタブ状態）
        viewModel.exportData(context, uri)
        advanceUntilIdle()

        // 検証: isProcessing が false に戻ること
        assertEquals(false, viewModel.uiState.value.isProcessing)
    }

    @Test
    fun lg_02_clearAuditLogs_safetyOnFailure() = runTest {
        coEvery { auditLogRepository.deleteAllLogs() } throws RuntimeException("Delete Error")

        viewModel.clearAuditLogs()
        advanceUntilIdle()

        coVerify {
            auditLogRepository.log(
                featureName = "Settings",
                operation = "clearAuditLogs",
                actionType = "ERROR",
                tableName = any(),
                affectedId = any(),
                details = match { it.contains("Delete Error") },
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    fun lg_03_rotateLogs_safetyOnFailure() = runTest {
        // 設定値の読み込みを待機
        advanceUntilIdle()
        
        coEvery { auditLogRepository.deleteOldLogs(any()) } throws RuntimeException("Rotate Error")

        viewModel.rotateLogsManually()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isProcessing)
        coVerify {
            auditLogRepository.log(
                featureName = "Settings",
                operation = "rotateLogsManually",
                actionType = "ERROR",
                tableName = any(),
                affectedId = any(),
                details = match { it.contains("Rotate Error") },
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    fun lg_04_clearAllData_safetyOnFailure() = runTest {
        coEvery { maintenanceRepository.clearAllData() } throws IOException("Clear Error")

        viewModel.clearAllData()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isProcessing)
        coVerify {
            auditLogRepository.log(
                featureName = "Settings",
                operation = "clearAllData",
                actionType = "ERROR",
                tableName = any(),
                affectedId = "0",
                details = match { it.contains("Clear Error") },
                resultType = "IO_ERROR"
            )
        }
    }

    @Test
    fun lg_05_checkIntegrity_safetyOnFailure() = runTest {
        coEvery { maintenanceRepository.scanInconsistencies() } throws RuntimeException("Integrity Error")

        viewModel.checkIntegrity()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isProcessing)
        coVerify {
            auditLogRepository.log(
                featureName = "Settings",
                operation = "checkIntegrity",
                actionType = "ERROR",
                tableName = any(),
                affectedId = any(),
                details = match { it.contains("Integrity Error") },
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    fun lg_06_initialSettingsSync_safety() = runTest {
        every { userSettingsRepository.isBiometricEnabled } returns flow {
            throw RuntimeException("Flow Error")
        }

        // 新しく ViewModel を作成して Flow を購読させる
        val errorViewModel = SettingsViewModel(
            maintenanceRepository, archivedPersonRepository, auditLogRepository, userSettingsRepository
        )
        
        advanceUntilIdle()
            
        coVerify {
            auditLogRepository.log(
                featureName = "Settings",
                operation = "initialSettingsSync", 
                actionType = "ERROR",
                tableName = "all_db",
                affectedId = "0",
                details = match { it.contains("Flow Error") },
                resultType = "OTHER_ERROR"
            )
        }
    }
}
