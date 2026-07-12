@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
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
 * PersonBaseViewModel は abstract のため、テスト用の具象クラスを作成
 */
class TestPersonBaseViewModel(
    repository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : PersonBaseViewModel(repository, summaryRepository, userSettingsRepository, auditLogRepository) {
    fun clearLoadingForTest() {
        _isLoading.value = false
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PersonBaseViewModelTest {

    private val repository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private lateinit var viewModel: TestPersonBaseViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testPerson = Person(
        id = 1,
        lastName = "基底",
        firstName = "太郎",
        lastNameFurigana = "きてい",
        firstNameFurigana = "たろう",
        birthday = Instant.now()
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
        
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("")
        every { repository.getPersonById(any()) } returns flowOf(testPerson)

        viewModel = TestPersonBaseViewModel(
            repository,
            summaryRepository,
            userSettingsRepository,
            auditLogRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun `OK-01_同一IDのロード時は処理をスキップすること`() = runTest {
        // 1回目のロード
        viewModel.loadPerson(1)
        advanceUntilIdle()
        
        // 本来はサブクラスのFlowで解除されるが、テストでは手動で解除して状態を作る
        // (isLoading = false, currentPerson.id = 1)
        viewModel.clearLoadingForTest()
        assertEquals(false, viewModel.isLoading.value)
        assertEquals(1, viewModel.currentPerson.value?.id)

        // 2回目のロード（同じID）
        viewModel.loadPerson(1)
        
        // スキップされるため、isLoading は true に戻らない
        assertEquals(false, viewModel.isLoading.value)
        
        // Repository が 1 回しか呼ばれていないことを確認
        coVerify(exactly = 1) { repository.getPersonById(1) }
    }

    @Test
    fun `LG-01_ロード例外時にisLoadingがfalseになり監査ログが記録されること`() = runTest {
        every { repository.getPersonById(any()) } returns flow {
            throw RuntimeException("Base Load Error")
        }

        viewModel.loadPerson(2)
        advanceUntilIdle()

        assertEquals(false, viewModel.isLoading.value)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonBase",
                operation = "loadPerson",
                tableName = "person_db",
                actionType = "ERROR",
                affectedId = "2",
                details = match { it.contains("Base Load Error") }
            )
        }
    }

    @Test
    fun `LG-02_利用者不在時にisLoadingがfalseになること`() = runTest {
        every { repository.getPersonById(any()) } returns flowOf(null)

        viewModel.loadPerson(3)
        advanceUntilIdle()

        assertEquals(false, viewModel.isLoading.value)
        assertEquals(null, viewModel.currentPerson.value)
    }
}
