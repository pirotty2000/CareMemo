package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoInfo
import jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Logic Test: UnassignedPhotoViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UnassignedPhotoViewModelTest {

    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val conditionRepository = mockk<ConditionRepository>(relaxed = true)
    
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)
        
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { conditionRepository.getPhotoPhysicalFiles() } returns emptyList()
        coEvery { conditionRepository.deletePhotoFiles(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun createViewModel() = UnassignedPhotoViewModel(
        userSettingsRepository,
        conditionRepository
    )

    // region 2. 初期化・データロードテスト (Initialization)

    @Test
    fun INI_01_initialLoad_success() = runTest {
        coEvery { conditionRepository.getAllConditionPhotosRaw() } returns emptyList()
        coEvery { conditionRepository.getAllConditionAtVisitIds() } returns emptySet()

        val viewModel = createViewModel()
        
        viewModel.uiState.test {
            // Skip intermediate state transitions during initialization
            advanceUntilIdle()
            
            val loaded = expectMostRecentItem()
            assertFalse(loaded.isLoading)
            assertTrue(loaded.unassignedPhotos.isEmpty())
        }
    }

    // endregion

    // region 3. 写真操作テスト (Actions)

    @Test
    fun ACT_01_deletePhoto_callsRepositoryAndClearsFiles() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val info = UnassignedPhotoInfo(
            type = UnassignedPhotoType.TEMPORARY,
            photoId = "p1",
            personId = "u1",
            photoFileName = "img_1.jpg",
            thumbnailFileName = "thumb_1.jpg",
            capturedAt = Instant.now(),
            descriptionResId = 0
        )

        viewModel.deletePhoto(info)
        advanceUntilIdle()

        coVerify { conditionRepository.deleteConditionPhotoById("p1", "u1", any(), any()) }
        coVerify { conditionRepository.deletePhotoFiles("img_1.jpg", "thumb_1.jpg") }
    }

    // endregion

    // region 4. ナビゲーションテスト (Navigation)

    @Test
    fun NAV_01_navigateBack() = runTest {
        val viewModel = createViewModel()
        viewModel.viewEvent.test {
            viewModel.navigateBack()
            assertEquals(UnassignedPhotoViewEvent.NavigateBack, awaitItem())
        }
    }

    // endregion
}
