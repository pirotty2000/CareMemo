@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
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
 * SCR-S-001 SettingsViewModel のユニットテスト
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
        coEvery { auditLogRepository.getLogCount() } returns 0
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
    fun lg01_exportData_safetyOnFailure() = runTest {
        val context = mockk<Context>(relaxed = true)
        val uri = mockk<Uri>(relaxed = true)
        
        // バックアップ処理中に例外を発生させる
        coEvery { maintenanceRepository.getBackupData() } throws IOException("No space left")

        viewModel.exportData(context, uri)
        advanceUntilIdle()

        // 検証: isLoading (isProcessing) が false に戻ること
        assertEquals(false, viewModel.isProcessing.value)
        
        // 監査ログにエラーが記録されていること
        coVerify {
            auditLogRepository.log(
                featureName = "Settings",
                operation = "exportData",
                actionType = "ERROR",
                details = match { it.contains("No space left") },
                resultType = "IO_ERROR",
                tableName = any(),
                affectedId = any()
            )
        }
    }

    @Test
    fun lg02_clearAuditLogs_safetyOnFailure() = runTest {
        coEvery { auditLogRepository.deleteAllLogs() } throws RuntimeException("Delete Error")

        viewModel.clearAuditLogs()
        advanceUntilIdle()

        coVerify {
            auditLogRepository.log(
                featureName = "Settings",
                operation = "clearAuditLogs",
                actionType = "ERROR",
                details = match { it.contains("Delete Error") },
                tableName = any(),
                affectedId = any(),
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    fun lg03_rotateLogs_safetyOnFailure() = runTest {
        coEvery { auditLogRepository.deleteOldLogs(any()) } throws RuntimeException("Rotate Error")

        viewModel.rotateLogsManually()
        advanceUntilIdle()

        assertEquals(false, viewModel.isProcessing.value)
        coVerify {
            auditLogRepository.log(
                featureName = "Settings",
                operation = "rotateLogsManually",
                actionType = "ERROR",
                details = match { it.contains("Rotate Error") },
                tableName = any(),
                affectedId = any(),
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    fun lg04_clearAllData_safetyOnFailure() = runTest {
        val context = mockk<Context>(relaxed = true)
        coEvery { ImageUtils.clearPhotosDir(any()) } throws IOException("Clear Photos Error")

        viewModel.clearAllData(context)
        advanceUntilIdle()

        assertEquals(false, viewModel.isProcessing.value)
        coVerify {
            auditLogRepository.log(
                featureName = "Settings",
                operation = "clearAllData",
                actionType = "ERROR",
                details = match { it.contains("Clear Photos Error") },
                tableName = "all_db",
                affectedId = any(),
                resultType = "IO_ERROR"
            )
        }
    }

    @Test
    fun lg05_checkIntegrity_safetyOnFailure() = runTest {
        coEvery { maintenanceRepository.scanInconsistencies() } throws RuntimeException("Integrity Error")

        viewModel.checkIntegrity()
        advanceUntilIdle()

        assertEquals(false, viewModel.isProcessing.value)
        coVerify {
            auditLogRepository.log(
                featureName = "Settings",
                operation = "checkIntegrity",
                actionType = "ERROR",
                details = match { it.contains("Integrity Error") },
                tableName = any(),
                affectedId = any(),
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    fun lg06_deletedUserListFlow_safetyOnFailure() = runTest {
        every { archivedPersonRepository.getArchivedPersons() } returns flow {
            throw RuntimeException("Flow Error")
        }

        val errorViewModel = SettingsViewModel(
            maintenanceRepository, archivedPersonRepository, auditLogRepository, userSettingsRepository
        )

        errorViewModel.deletedUserList.test {
            awaitItem()
            advanceUntilIdle()
            
            coVerify {
                auditLogRepository.log(
                    featureName = "Settings",
                    operation = "deletedUserListFlow",
                    actionType = "ERROR",
                    details = match { it.contains("Flow Error") },
                    tableName = any(),
                    affectedId = any(),
                    resultType = "OTHER_ERROR"
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
