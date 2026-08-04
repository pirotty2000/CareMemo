@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import androidx.lifecycle.SavedStateHandle
import jp.mydns.fujiwara.carememo.data.AuditLog
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
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
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * AuditLogViewModel のロジック・安全性テスト (System B 移行済)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-S-002_AuditLogScreen.md に準拠
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuditLogViewModelTest {

    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    
    private lateinit var viewModel: AuditLogViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val mockLogs = listOf(
        AuditLog(id = 1, timestamp = Instant.ofEpochMilli(1000), featureName = "PersonList", operation = "op1", tableName = "t1", actionType = "A", affectedId = "1", resultType = "SUCCESS")
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)
        
        every { auditLogRepository.allLogs } returns flowOf(mockLogs)

        viewModel = AuditLogViewModel(
            auditLogRepository,
            userSettingsRepository,
            SavedStateHandle()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    // ======================================================================================
    // 3. ロジック・安全性テスト (AuditLogViewModel)
    // ======================================================================================

    @Test
    fun lg_01_dataFetchFailure_safety() = runTest {
        // ログ取得中に例外が発生する状況をシミュレート
        every { auditLogRepository.allLogs } returns flow {
            throw RuntimeException("AuditLogs Flow Error")
        }

        val errorViewModel = AuditLogViewModel(auditLogRepository, userSettingsRepository, SavedStateHandle())

        errorViewModel.uiState.test {
            awaitItem() // 初期値
            advanceUntilIdle()
            
            // 検証: isLoading が false になること
            assertEquals(false, errorViewModel.uiState.value.isLoading)
            
            // 検証: 監査ログにエラーが記録されること (BaseViewModel/SafeCollectの機能)
            coVerify {
                auditLogRepository.log(
                    featureName = "AuditLog",
                    operation = "auditLogsFlow",
                    tableName = "audit_log",
                    actionType = "ERROR",
                    affectedId = any(),
                    details = match { it.contains("AuditLogs Flow Error") },
                    resultType = "OTHER_ERROR"
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun lg_02_atomicStateUpdate() = runTest {
        advanceUntilIdle()
        
        // フィルタ変更
        viewModel.setFeatureFilter("Settings")
        advanceUntilIdle()
        
        assertEquals("Settings", viewModel.uiState.value.selectedFeature)
        // リストは mockLogs (PersonList) なので、Settings でフィルタすると空になるはず
        assertEquals(0, viewModel.uiState.value.auditLogs.size)
    }
}
