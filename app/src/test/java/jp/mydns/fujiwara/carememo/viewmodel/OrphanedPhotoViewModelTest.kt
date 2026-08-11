package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
import android.util.Log
import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo
import jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoType
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * Logic Test: OrphanedPhotoViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrphanedPhotoViewModelTest {

    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val conditionRepository = mockk<ConditionRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)
        
        mockkObject(ImageUtils)
        
        // Stub context properties that are often accessed
        val tempDir = File("build/tmp/test_photos")
        tempDir.mkdirs()
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns tempDir
        
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { ImageUtils.getPhotosDirPublic(any()) } returns mockk<File> {
            every { listFiles() } returns emptyArray()
        }
        coEvery { ImageUtils.deleteImageFiles(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(ImageUtils)
        unmockkStatic(Log::class)
    }

    private fun createViewModel() = OrphanedPhotoViewModel(
        userSettingsRepository,
        conditionRepository,
        context
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
            assertTrue(loaded.orphanedPhotos.isEmpty())
        }
    }

    // endregion

    // region 3. 写真操作テスト (Actions)

    @Test
    fun ACT_01_deletePhoto_callsRepositoryAndClearsFiles() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val info = OrphanedPhotoInfo(
            type = OrphanedPhotoType.TEMPORARY,
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
        coVerify { ImageUtils.deleteImageFiles(context, "img_1.jpg", "thumb_1.jpg") }
    }

    // endregion

    // region 4. ナビゲーションテスト (Navigation)

    @Test
    fun NAV_01_navigateBack() = runTest {
        val viewModel = createViewModel()
        viewModel.viewEvent.test {
            viewModel.navigateBack()
            assertEquals(OrphanedPhotoViewEvent.NavigateBack, awaitItem())
        }
    }

    // endregion
}
