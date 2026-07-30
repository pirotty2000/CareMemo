package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/**
 * 非同期処理のライフサイクル、特に「利用者の切り替え」に伴う
 * 状態のクリア、キャンセル、レースコンディションを検証するテスト。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonLifecycleTest {

    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val conditionRepository = mockk<ConditionRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("")
        // PersonDetailUiStateViewModel が使用するサマリーもデフォルト値を返すように設定（combine を完了させるため）
        every { summaryRepository.getPersonCategorySummaryById(any()) } returns flowOf(jp.mydns.fujiwara.carememo.data.PersonCategorySummary())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun BP_01_loadPerson_clears_state_immediately() = runTest {
        val viewModel = PersonDetailUiStateViewModel(personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
        val person1 = Person(id = "1", lastName = "一人目", firstName = "", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now())
        
        // 1. 一人目をロード完了させる
        every { personRepository.getPersonById("1") } returns flowOf(person1)
        viewModel.loadPerson("1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("1", viewModel.uiState.value.personId)

        // 2. 二人目をロード開始する（レスポンスは遅延させる）
        every { personRepository.getPersonById("2") } returns flow {
            delay(1000.milliseconds)
            emit(Person(id = "2", lastName = "二人目", firstName = "", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now()))
        }
        
        viewModel.loadPerson("2")
        
        // 【重要】非同期処理が完了する前（0ms時点）で、状態がnullになっている必要がある
        assertNull("ロード開始直後に状態がクリアされていること", viewModel.uiState.value.personId)
        
        // 時間を進めれば二人目がセットされる
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("2", viewModel.uiState.value.personId)
    }

    @Test
    fun BP_01_rapid_switch_cancels_previous_load() = runTest {
        val viewModel = PersonDetailUiStateViewModel(personRepository, summaryRepository, userSettingsRepository, auditLogRepository)

        // ID:1 は 2000ms かかる
        every { personRepository.getPersonById("1") } returns flow {
            delay(2000.milliseconds)
            emit(Person(id = "1", lastName = "遅い", firstName = "", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now()))
        }
        // ID:2 は 500ms かかる
        every { personRepository.getPersonById("2") } returns flow {
            delay(500.milliseconds)
            emit(Person(id = "2", lastName = "速い", firstName = "", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now()))
        }

        // ID:1 を開始し、すぐに ID:2 に切り替える
        viewModel.loadPerson("1")
        advanceTimeBy(100.milliseconds)
        viewModel.loadPerson("2")

        // 全ての処理が終わるまで時間を進める
        testDispatcher.scheduler.advanceUntilIdle()

        // 結果として ID:2 が残っていること。ID:1 で上書きされていないこと。
        assertEquals("2", viewModel.uiState.value.personId)
        assertEquals("速い", viewModel.uiState.value.person?.lastName)
    }

    @Test
    fun BP_01_switch_person_resets_subclass_state() = runTest {
        val viewModel = PersonConditionViewModel(conditionRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
        
        // ID:1 で検索クエリを入力済み
        every { personRepository.getPersonById("1") } returns flowOf(Person(id = "1", lastName = "A", firstName = "", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now()))
        viewModel.loadPerson("1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.updateSearchQuery("検索ワード")
        assertEquals("検索ワード", viewModel.uiState.value.searchQuery)

        // ID:2 に切り替え（データロード前）
        every { personRepository.getPersonById("2") } returns flow {
            delay(1000.milliseconds)
            emit(Person(id = "2", lastName = "B", firstName = "", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now()))
        }
        viewModel.loadPerson("2")

        // 【重要】データのロード完了を待たずして、クエリがリセットされていること
        assertEquals("", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun BP_01_same_id_avoids_reload() = runTest {
        val viewModel = PersonDetailUiStateViewModel(personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
        val person1 = Person(id = "1", lastName = "一人目", firstName = "", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now())
        
        every { personRepository.getPersonById("1") } returns flowOf(person1)
        
        // 初回ロード
        viewModel.loadPerson("1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("1", viewModel.uiState.value.personId)

        // 同じIDで再度ロード（もしリロードされるなら一瞬 null になるはず）
        viewModel.loadPerson("1")
        
        // null にならず、即座に値が維持されていること
        assertEquals("1", viewModel.uiState.value.personId)
    }
}
