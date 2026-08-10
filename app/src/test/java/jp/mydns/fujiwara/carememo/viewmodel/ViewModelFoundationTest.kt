package jp.mydns.fujiwara.carememo.viewmodel

import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit Test: ViewModel Architectural Foundation (BASE)
 * 
 * Verifies the core contract shared by all ViewModels:
 * loading state management, global error handling, and state atomicity.
 */
private data class FoundationUiState(
    val count: Int = 0,
    val isLoading: Boolean = false
)

private class FoundationViewModel(
    userSettingsRepository: UserSettingsRepository
) : BaseUiStateViewModel<FoundationUiState, Unit>(userSettingsRepository, FoundationUiState()) {
    override val featureName: String = "FoundationTest"
    
    override fun copyWithLoadingState(state: FoundationUiState, isLoading: Boolean): FoundationUiState {
        return state.copy(isLoading = isLoading)
    }

    fun increment() = updateUiState { it.copy(count = it.count + 1) }
    
    fun setHandler(handler: CoroutineErrorHandler) {
        this.coroutineErrorHandler = handler
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelFoundationTest {

    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = FoundationViewModel(userSettingsRepository).apply {
        setHandler(mockk(relaxed = true))
    }

    @Test
    fun BASE_01_updateUiState_isAtomic() = runTest {
        val viewModel = createViewModel()
        
        viewModel.increment()
        viewModel.increment()
        
        assertEquals(2, viewModel.uiState.value.count)
    }

    @Test
    fun BASE_02_safeLaunch_managesLoadingState() = runTest {
        val viewModel = createViewModel()
        
        viewModel.uiState.test {
            awaitItem() // Initial
            
            viewModel.safeLaunch("Op") {
                delay(1000)
            }
            
            assertTrue("Should be loading", awaitItem().isLoading)
            advanceUntilIdle()
            assertFalse("Should be finished", awaitItem().isLoading)
        }
    }

    @Test
    fun BASE_03_safeLaunch_delegatesToErrorHandlerOnException() = runTest {
        val handler = mockk<CoroutineErrorHandler>(relaxed = true)
        val viewModel = createViewModel().apply { setHandler(handler) }
        
        val exception = RuntimeException("Crash")
        viewModel.safeLaunch("Op") {
            throw exception
        }
        
        advanceUntilIdle()
        
        coVerify { handler.handleException(exception, any()) }
        assertFalse("Loading state should be cleared even on error", viewModel.uiState.value.isLoading)
    }

    @Test
    fun BASE_04_safeLaunch_rethrowsCancellationException() = runTest {
        val handler = mockk<CoroutineErrorHandler>(relaxed = true)
        val viewModel = createViewModel().apply { setHandler(handler) }
        
        viewModel.safeLaunch("Op") {
            throw CancellationException("Normal cancel")
        }
        
        advanceUntilIdle()
        
        // Handlers should ignore CancellationExceptions (coroutine standard)
        coVerify(exactly = 0) { handler.handleException(any(), any()) }
    }
}
