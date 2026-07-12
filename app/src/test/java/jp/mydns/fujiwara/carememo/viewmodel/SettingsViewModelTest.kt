@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
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
import io.mockk.verify
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
        coEvery { ImageUtils.clearPhotosDir(any()) } returns true

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

    // --- ロジック・安全性テスト (LG-01 〜 LG-06: Suspend系) ---

    @Test
    fun `LG-01_操作ログ消去失敗時に監査ログが記録されること`() = runTest {
        coEvery { auditLogRepository.deleteAllLogs() } throws RuntimeException("Delete Error")

        viewModel.clearAuditLogs()
        advanceUntilIdle() // 非同期実行を待機

        coVerify {
            auditLogRepository.log(
                featureName = "Settings",
                operation = "clearAuditLogs",
                tableName = any(),
                actionType = "ERROR",
                affectedId = any(),
                details = match { it.contains("Delete Error") }
            )
        }
    }

    @Test
    fun `LG-02_手動ローテーション失敗時にisProcessingがfalseになり監査ログが記録されること`() = runTest {
        coEvery { auditLogRepository.deleteOldLogs(any()) } throws RuntimeException("Rotate Error")

        viewModel.rotateLogsManually()
        advanceUntilIdle()

        assertEquals(false, viewModel.isProcessing.value)
        coVerify {
            auditLogRepository.log(
                featureName = "Settings",
                operation = "rotateLogsManually",
                tableName = any(),
                actionType = "ERROR",
                affectedId = any(),
                details = match { it.contains("Rotate Error") }
            )
        }
    }

    @Test
    fun `LG-05_全データ消去失敗時にisProcessingがfalseになり監査ログが記録されること`() = runTest {
        val context = mockk<Context>(relaxed = true)
        coEvery { maintenanceRepository.clearAllData() } throws RuntimeException("Clear All Error")

        viewModel.clearAllData(context)
        advanceUntilIdle()

        assertEquals(false, viewModel.isProcessing.value)
        coVerify {
            auditLogRepository.log(
                featureName = "Settings",
                operation = "clearAllData",
                tableName = any(),
                actionType = "ERROR",
                affectedId = any(),
                details = match { it.contains("Clear All Error") }
            )
        }
    }

    @Test
    fun `LG-06_整合性チェック失敗時にisProcessingがfalseになり監査ログが記録されること`() = runTest {
        coEvery { maintenanceRepository.scanInconsistencies() } throws RuntimeException("Integrity Error")

        viewModel.checkIntegrity()
        advanceUntilIdle()

        assertEquals(false, viewModel.isProcessing.value)
        coVerify {
            auditLogRepository.log(
                featureName = "Settings",
                operation = "checkIntegrity",
                tableName = any(),
                actionType = "ERROR",
                affectedId = any(),
                details = match { it.contains("Integrity Error") }
            )
        }
    }

    // --- ロジック・安全性テスト (LG-07 〜 LG-09: Flow系) ---

    @Test
    fun `LG-07_操作ログ一覧のFlow取得失敗時に監査ログが記録されること`() = runTest {
        every { auditLogRepository.allLogs } returns flow {
            throw RuntimeException("AuditLogs Flow Error")
        }

        val errorViewModel = SettingsViewModel(
            maintenanceRepository, archivedPersonRepository, auditLogRepository, userSettingsRepository
        )

        errorViewModel.auditLogs.test {
            awaitItem() // 初期値を受け取る
            advanceUntilIdle() // Flow内のcatch処理を走らせる
            
            coVerify {
                auditLogRepository.log(
                    featureName = "Settings",
                    operation = "auditLogsFlow",
                    tableName = any(),
                    actionType = "ERROR",
                    affectedId = any(),
                    details = match { it.contains("AuditLogs Flow Error") }
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `LG-08_利用終了者一覧のFlow取得失敗時に監査ログが記録されること`() = runTest {
        every { archivedPersonRepository.getArchivedPersons() } returns flow {
            throw RuntimeException("DeletedUserList Flow Error")
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
                    tableName = any(),
                    actionType = "ERROR",
                    affectedId = any(),
                    details = match { it.contains("DeletedUserList Flow Error") }
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `LG-09_統計情報のFlow取得失敗時にクラッシュしないこと`() = runTest {
        coEvery { auditLogRepository.getLogCount() } throws RuntimeException("Count Error")

        val errorViewModel = SettingsViewModel(
            maintenanceRepository, archivedPersonRepository, auditLogRepository, userSettingsRepository
        )

        errorViewModel.auditLogCount.test {
            assertEquals(0, awaitItem()) // 初期値
            advanceUntilIdle() // ポーリング開始と例外発生を待機
            
            // クラッシュせずにエラーログが出力されていることを確認
            verify {
                Log.e(any(), match { it.contains("auditLogCount") }, any())
            }
        }
    }
}
