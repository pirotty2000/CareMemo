package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.AuditLog
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogViewEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Logic Test: AuditLogViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuditLogViewModelTest {

    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    
    private val testDispatcher = StandardTestDispatcher()

    private val mockLogs = listOf(
        AuditLog(id = 1, timestamp = Instant.ofEpochMilli(1000), featureName = "PersonList", operation = "op1", tableName = "t1", actionType = "INSERT", affectedId = "1", resultType = "SUCCESS"),
        AuditLog(id = 2, timestamp = Instant.ofEpochMilli(2000), featureName = "Settings", operation = "op2", tableName = "t2", actionType = "UPDATE", affectedId = "2", resultType = "DB_ERROR")
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)
        
        every { auditLogRepository.allLogs } returns flowOf(mockLogs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    // region 2. データ読み込みテスト (Init / Load)

    @Test
    fun RD_01_RD_02_initialLoad_success() = runTest {
        val viewModel = AuditLogViewModel(auditLogRepository, userSettingsRepository)
        
        viewModel.uiState.test {
            // Initial state
            val initial = awaitItem()
            assertTrue(initial.isLoading)

            advanceUntilIdle()

            // Loaded state
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(2, state.filteredLogs.size)
            assertTrue(state.availableFeatures.contains("Settings"))
            assertTrue(state.availableResults.contains("DB_ERROR"))
        }
    }

    @Test
    fun RD_03_loadFailure_safety() = runTest {
        every { auditLogRepository.allLogs } returns flow {
            throw RuntimeException("Connection Failed")
        }

        val viewModel = AuditLogViewModel(auditLogRepository, userSettingsRepository)

        viewModel.uiState.test {
            awaitItem() // Initial
            advanceUntilIdle()
            
            val state = awaitItem()
            assertFalse(state.isLoading)
            
            coVerify {
                auditLogRepository.log(any(), any(), "audit_log", "ERROR", any(), any(), "OTHER_ERROR")
            }
        }
    }

    // endregion

    // region 3. フィルタリング・ソートテスト (Filter / Sort)

    @Test
    fun FLT_01_setFeatureFilter() = runTest {
        val viewModel = AuditLogViewModel(auditLogRepository, userSettingsRepository)
        advanceUntilIdle()

        viewModel.setFeatureFilter("Settings")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Settings", state.selectedFeature)
        assertEquals(1, state.filteredLogs.size)
        assertEquals("Settings", state.filteredLogs[0].featureName)
    }

    @Test
    fun FLT_02_setResultFilter() = runTest {
        val viewModel = AuditLogViewModel(auditLogRepository, userSettingsRepository)
        advanceUntilIdle()

        viewModel.setResultFilter("DB_ERROR")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("DB_ERROR", state.selectedResult)
        assertEquals(1, state.filteredLogs.size)
    }

    @Test
    fun SRT_01_toggleSortOrder() = runTest {
        val viewModel = AuditLogViewModel(auditLogRepository, userSettingsRepository)
        advanceUntilIdle()

        // Default is descending (2, 1)
        assertEquals(2, viewModel.uiState.value.filteredLogs[0].id)

        viewModel.toggleSortOrder() // Change to ascending
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAscending)
        assertEquals(1, viewModel.uiState.value.filteredLogs[0].id)
    }

    @Test
    fun CLR_01_clearFilters() = runTest {
        val viewModel = AuditLogViewModel(auditLogRepository, userSettingsRepository)
        advanceUntilIdle()

        viewModel.setFeatureFilter("Settings")
        viewModel.clearFilters()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedFeature)
        assertEquals(2, viewModel.uiState.value.filteredLogs.size)
    }

    // endregion

    // region 4. ナビゲーションテスト (Event)

    @Test
    fun NAV_01_navigateBack() = runTest {
        val viewModel = AuditLogViewModel(auditLogRepository, userSettingsRepository)
        
        viewModel.viewEvent.test {
            viewModel.navigateBack()
            assertEquals(AuditLogViewEvent.NavigateBack, awaitItem())
        }
    }

    // endregion
}
