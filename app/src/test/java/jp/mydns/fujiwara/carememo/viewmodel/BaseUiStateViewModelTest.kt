package jp.mydns.fujiwara.carememo.viewmodel

import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit Test: BaseUiStateViewModel Helpers
 * 
 * Verifies specific helper functions like snackbar, error dialogs, 
 * and settings synchronization.
 */
private data class MockUiState(val isLoading: Boolean = false)

private class MockViewModel(
    userSettingsRepository: UserSettingsRepository,
    securitySession: SecuritySession
) : BaseUiStateViewModel<MockUiState, String>(userSettingsRepository, securitySession, MockUiState()) {
    public override var featureName: String = "Mock"
    override fun copyWithLoadingState(state: MockUiState, isLoading: Boolean) = state.copy(isLoading = isLoading)

    fun testShowSnackbar(msg: String) = showSnackbar(msg)
    fun testSendViewEvent(event: String) = sendViewEvent(event)
    fun testSafeLaunch(block: suspend () -> Unit) = safeLaunch("TestOp") { block() }
    fun setHandler(handler: CoroutineErrorHandler) { coroutineErrorHandler = handler }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BaseUiStateViewModelTest {

    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val securitySession = SecuritySession()
    private val testDispatcher = StandardTestDispatcher()
    private val isNameMaskingEnabledFlow = MutableStateFlow(true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns isNameMaskingEnabledFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MockViewModel(userSettingsRepository, securitySession)

    // region 2. イベント送出テスト (Events)

    @Test
    fun EVT_01_showSnackbar_emitsUiEvent() = runTest {
        val viewModel = createViewModel()
        viewModel.uiEventFlow.test {
            viewModel.testShowSnackbar("Hello")
            val event = awaitItem()
            assertTrue(event is BaseUiStateViewModel.UiEvent.ShowSnackbar)
            assertEquals("Hello", (event as BaseUiStateViewModel.UiEvent.ShowSnackbar).message)
        }
    }

    @Test
    fun EVT_02_sendViewEvent_emitsViewEvent() = runTest {
        val viewModel = createViewModel()
        viewModel.viewEvent.test {
            viewModel.testSendViewEvent("Nav")
            assertEquals("Nav", awaitItem())
        }
    }

    // endregion

    // region 3. 共通設定同期テスト (Settings)

    @Test
    fun SET_01_maskingSetting_isSynced() = runTest {
        val viewModel = createViewModel()
        
        // Use turbine to subscribe to the Flow, keeping it active (triggering WhileSubscribed)
        viewModel.isNameMaskingEnabled.test {
            // Initial value
            assertTrue(awaitItem())
            
            isNameMaskingEnabledFlow.value = false
            // Should emit the new value
            assertFalse(awaitItem())
        }
    }

    @Test
    fun LAUNCH_01_safeLaunch_recordsGuardSkippedIfFeatureNameThrows() = runTest {
        val viewModel = createViewModel()
        val handler = mockk<CoroutineErrorHandler>(relaxed = true)
        viewModel.setHandler(handler)
        
        // Mock featureName to throw
        val vmSpy = spyk(viewModel)
        every { vmSpy.featureName } throws UninitializedPropertyAccessException("featureName not initialized")

        vmSpy.testSafeLaunch {
            throw RuntimeException("Test error")
        }
        
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { 
            handler.handleException(any(), withArg {
                assertEquals("Unknown", it.featureName)
                assertEquals("GUARD_SKIPPED", it.tableName)
            })
        }
    }
}
