@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailViewModelTest {

    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private lateinit var viewModel: PersonDetailViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testPerson = Person(
        id = 1,
        lastName = "詳細",
        firstName = "太郎",
        lastNameFurigana = "しょうさい",
        firstNameFurigana = "たろう",
        birthday = Instant.now()
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        
        viewModel = PersonDetailViewModel(personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun `loadPersonを実行したとき、指定したIDの利用者が取得できること`() = runTest {
        viewModel.loadPerson(1)

        viewModel.currentPerson.test {
            assertEquals(testPerson, awaitItem())
        }
    }

    @Test
    fun `setCategoryを実行したとき、currentCategoryが更新されること`() = runTest {
        viewModel.setCategory(Category.BP_AND_PULSE)
        
        assertEquals(Category.BP_AND_PULSE, viewModel.currentCategory.value)
    }

    @Test
    fun `personCategorySummaryで例外が発生したとき、isLoadingがfalseになりエラーログが記録されること`() = runTest {
        // 1. ViewModel を作る前に例外を投げるように設定
        every { summaryRepository.getPersonCategorySummaryById(any()) } returns flow {
            throw RuntimeException("Summary error")
        }

        // 2. ViewModel 再生成
        val errorViewModel = PersonDetailViewModel(personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
        
        // 3. 利用者をロードして Flow を動かす
        errorViewModel.loadPerson(1)

        // 4. 検証
        errorViewModel.personCategorySummary.test {
            awaitItem() // 初期値 null
            // エラーが発生して isLoading が false になるのを待つ
            assertEquals(false, errorViewModel.isLoading.value)
            coVerify {
                auditLogRepository.log(
                    featureName = any(),
                    operation = "personCategorySummaryFlow",
                    tableName = any(),
                    actionType = "ERROR",
                    affectedId = any(),
                    details = match { it?.contains("Summary error") == true },
                    resultType = "OTHER_ERROR"
                )
            }
        }
    }

    @Test
    fun `loadPersonで例外が発生したとき、isLoadingがfalseになりエラーログが記録されること`() = runTest {
        every { personRepository.getPersonById(any()) } returns flow {
            throw RuntimeException("Load error")
        }

        viewModel.loadPerson(1)

        assertEquals(false, viewModel.isLoading.value)
        coVerify {
            auditLogRepository.log(
                featureName = any(),
                operation = "loadPerson",
                tableName = any(),
                actionType = "ERROR",
                affectedId = any(),
                details = match { it?.contains("Load error") == true },
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    fun `loadPersonを実行したとき、サマリー取得が完了するまでisLoadingがtrueを維持すること`() = runTest {
        val personId = 2
        val expectedSummary = PersonCategorySummary(hasHeightWeight = true)

        // 1. 各リポジトリのレスポンスに遅延を入れる
        every { personRepository.getPersonById(personId) } returns flow {
            delay(1000) // 利用者取得に1秒
            emit(testPerson.copy(id = personId))
        }
        every { summaryRepository.getPersonCategorySummaryById(personId) } returns flow {
            delay(1000) // サマリー取得にさらに1秒
            emit(expectedSummary)
        }

        // 2. ViewModel作成
        val viewModel = PersonDetailViewModel(personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
        
        // 3. 購読を開始して Flow をアクティブにする
        viewModel.personCategorySummary.test {
            // 初期値を受け取る
            assertEquals(null, awaitItem())

            // 4. ロード開始
            viewModel.loadPerson(personId)
            
            // 開始直後：Loading中であること
            assertEquals(true, viewModel.isLoading.value)
            
            // 500ms経過：まだLoading中
            advanceTimeBy(500)
            assertEquals(true, viewModel.isLoading.value)
            
            // 1500ms経過：利用者(1s)は取れたが、サマリー(1s)がまだなのでLoading中
            advanceTimeBy(1000)
            assertEquals(true, viewModel.isLoading.value)
            
            // 2500ms経過：全て完了したので Loading が false になる
            advanceTimeBy(1000)
            assertEquals(false, viewModel.isLoading.value)
            
            // 最終的なデータが流れてくること
            assertEquals(expectedSummary, awaitItem())
        }
    }
}
