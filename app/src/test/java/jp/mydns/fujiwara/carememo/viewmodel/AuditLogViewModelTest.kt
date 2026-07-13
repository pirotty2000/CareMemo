@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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

@OptIn(ExperimentalCoroutinesApi::class)
class AuditLogViewModelTest {

    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    
    private lateinit var viewModel: AuditLogViewModel
    private val testDispatcher = StandardTestDispatcher()

    // 降順（新しい順）で定義する
    private val mockLogs = listOf(
        AuditLog(id = 2, timestamp = Instant.ofEpochMilli(2000), featureName = "PersonList", operation = "op2", tableName = "t2", actionType = "B", affectedId = "2", resultType = "DB_ERROR"),
        AuditLog(id = 1, timestamp = Instant.ofEpochMilli(1000), featureName = "Settings", operation = "op1", tableName = "t1", actionType = "A", affectedId = "1", resultType = "SUCCESS")
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)
        
        every { auditLogRepository.allLogs } returns flowOf(mockLogs)

        viewModel = AuditLogViewModel(
            auditLogRepository,
            userSettingsRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun `LG-04_操作ログ一覧のFlow取得失敗時に監査ログが記録されること`() = runTest {
        every { auditLogRepository.allLogs } returns flow {
            throw RuntimeException("AuditLogs Flow Error")
        }

        val errorViewModel = AuditLogViewModel(auditLogRepository, userSettingsRepository)

        errorViewModel.auditLogs.test {
            awaitItem() // 初期値
            advanceUntilIdle()
            
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
    fun `LG-05_フィルタリング操作が正しく連動すること`() = runTest {
        viewModel.auditLogs.test {
            // StateFlowの初期値(emptyList)をスキップまたは消費
            var initial = awaitItem()
            if (initial.isEmpty()) initial = awaitItem()
            
            assertEquals(2, initial.size)

            // 機能で絞り込み
            viewModel.setFeatureFilter("Settings")
            val filtered = awaitItem()
            assertEquals(1, filtered.size)
            assertEquals("Settings", filtered[0].featureName)

            // フィルタ解除
            viewModel.clearFilters()
            assertEquals(2, awaitItem().size)
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `並べ替えトグルが正しく連動すること`() = runTest {
        viewModel.auditLogs.test {
            var descLogs = awaitItem()
            if (descLogs.isEmpty()) descLogs = awaitItem()

            assertEquals(2L, descLogs[0].id) // 降順（デフォルト）

            viewModel.toggleSortOrder()
            val ascLogs = awaitItem()
            assertEquals(1L, ascLogs[0].id) // 昇順
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}
