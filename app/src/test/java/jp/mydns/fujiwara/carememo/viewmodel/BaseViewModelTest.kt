@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * BaseViewModel のテスト用具象クラス
 */
class ConcreteBaseViewModel(
    userSettingsRepository: UserSettingsRepository,
    override val scope: CoroutineScope // テスト用のスコープを受け取れるようにする
) : BaseViewModel(userSettingsRepository) {
    override val featureName: String = "TestScreen"

    fun setHandler(handler: CoroutineErrorHandler) {
        this.coroutineErrorHandler = handler
    }

    // テスト用に protected メソッドを公開
    fun callSafeLaunch(
        operation: String,
        loadingState: MutableStateFlow<Boolean>? = null,
        contextBuilder: (ErrorContextBuilder.() -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit
    ) = safeLaunch(operation, loadingState, contextBuilder, block)

    fun <T> callSafeCollect(
        operation: String,
        mode: CollectMode = CollectMode.INITIAL,
        loadingState: MutableStateFlow<Boolean>? = null,
        contextBuilder: (ErrorContextBuilder.() -> Unit)? = null,
        flowProvider: () -> kotlinx.coroutines.flow.Flow<T>,
        action: suspend (T) -> Unit
    ) = safeCollect(operation, mode, loadingState, contextBuilder, flowProvider, action)
}

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {

    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val errorHandler = mockk<CoroutineErrorHandler>(relaxed = true)
    private lateinit var viewModel: ConcreteBaseViewModel
    private val testDispatcher = StandardTestDispatcher()
    
    // 未捕捉の例外をキャッチするためのハンドラ
    private var lastCaughtThrowable: Throwable? = null
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        lastCaughtThrowable = throwable
    }
    
    // テスト用スコープ
    private lateinit var testScope: CoroutineScope

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(true)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("")

        lastCaughtThrowable = null
        testScope = CoroutineScope(SupervisorJob() + testDispatcher + exceptionHandler)
        
        viewModel = ConcreteBaseViewModel(userSettingsRepository, testScope)
        viewModel.setHandler(errorHandler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `SL-01_正常終了時にloadingStateが遷移すること`() = runTest {
        val loading = MutableStateFlow(false)
        var executed = false

        viewModel.callSafeLaunch(operation = "test", loadingState = loading) {
            executed = true
        }

        advanceUntilIdle()

        assertTrue(executed)
        assertEquals(false, loading.value)
    }

    @Test
    fun `SL-02_Exception発生時にハンドラが呼ばれloadingStateがfalseになること`() = runTest {
        val loading = MutableStateFlow(false)
        val exception = RuntimeException("Test Exception")

        viewModel.callSafeLaunch(operation = "testOp", loadingState = loading) {
            throw exception
        }

        advanceUntilIdle()

        coVerify {
            errorHandler.handleException(
                e = exception,
                context = match { it.operation == "testOp" && it.featureName == "TestScreen" }
            )
        }
        assertEquals(false, loading.value)
        // Exception は再スローされないので、例外ハンドラには届かない
        assertEquals(null, lastCaughtThrowable)
    }

    @Test
    fun `SL-03_CancellationException発生時はハンドラを呼ばず再スローすること`() = runTest {
        val loading = MutableStateFlow(false)

        viewModel.callSafeLaunch(operation = "cancel", loadingState = loading) {
            throw CancellationException("Normal Cancel")
        }

        advanceUntilIdle()

        coVerify(exactly = 0) {
            errorHandler.handleException(any(), any())
        }
        assertEquals(false, loading.value)
        // CancellationException は例外ハンドラには届かない（正常な終了として扱われる）
        assertEquals(null, lastCaughtThrowable)
    }

    @Test
    fun `SL-04_Error発生時にハンドラを呼び出した上で再スローされfinallyが実行されること`() = runTest {
        val loading = MutableStateFlow(false)
        val error = OutOfMemoryError("Fake OOM")

        viewModel.callSafeLaunch(operation = "fatal", loadingState = loading) {
            throw error
        }

        advanceUntilIdle()

        // 1. ハンドラが呼ばれたこと
        coVerify {
            errorHandler.handleException(error, any())
        }
        // 2. 最後に loadingState が解除されていること
        assertEquals(false, loading.value)
        // 3. Error は再スローされて例外ハンドラに届いていること
        assertEquals(error, lastCaughtThrowable)
    }

    @Test
    fun `SL-05_ハンドラ自体が失敗しても再スローされfinallyが実行されること`() = runTest {
        val loading = MutableStateFlow(false)
        val handlerException = RuntimeException("Handler Failed")
        coEvery { errorHandler.handleException(any(), any()) } throws handlerException

        viewModel.callSafeLaunch(operation = "op", loadingState = loading) {
            throw Exception("Original Error")
        }

        advanceUntilIdle()

        // ハンドラが失敗しても、finally は実行される
        assertEquals(false, loading.value)
        // ハンドラの例外が再スローされて例外ハンドラに届いている
        assertEquals(handlerException, lastCaughtThrowable)
    }

    @Test
    fun `SC-01_safeCollect_INITIALモードでデータ受信時にloadingが解除されること`() = runTest {
        val loading = MutableStateFlow(false)
        val dataFlow = flowOf("Data1", "Data2")
        var receivedCount = 0

        viewModel.callSafeCollect(
            operation = "collect",
            mode = CollectMode.INITIAL,
            loadingState = loading,
            flowProvider = { dataFlow }
        ) {
            receivedCount++
        }

        advanceUntilIdle()

        assertEquals(2, receivedCount)
        assertEquals(false, loading.value)
    }

    @Test
    fun `SC-02_safeCollect_MONITORINGモードでデータ受信時にloadingが変化しないこと`() = runTest {
        val loading = MutableStateFlow(false)
        val dataFlow = flowOf("Update")

        viewModel.callSafeCollect(
            operation = "watch",
            mode = CollectMode.MONITORING,
            loadingState = loading,
            flowProvider = { dataFlow }
        ) {
            // Do nothing
        }

        advanceUntilIdle()

        assertEquals(false, loading.value)
    }

    @Test
    fun `SC-03_safeCollect_例外発生時にハンドラが呼ばれ解除されること`() = runTest {
        val loading = MutableStateFlow(false)
        val errorFlow = flow<String> { throw Exception("Flow Error") }

        viewModel.callSafeCollect(
            operation = "failFlow",
            mode = CollectMode.INITIAL,
            loadingState = loading,
            flowProvider = { errorFlow }
        ) { }

        advanceUntilIdle()

        coVerify { errorHandler.handleException(any(), any()) }
        assertEquals(false, loading.value)
    }
}
