@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * [BatchInputViewModel] のビジネスロジック検証テスト。
 * 記録日時の重複チェックや、利用者切り替え時の状態管理を検証します。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchInputViewModelTest {

    private val healthRepository = mockk<HealthRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testPerson = Person(
        id = 1,
        lastName = "山田",
        firstName = "太郎",
        lastNameFurigana = "ヤマダ",
        firstNameFurigana = "タロウ",
        birthday = Instant.now()
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("")
        coEvery { personRepository.getPersonById(any()) } returns flowOf(testPerson)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun `初期状態では入力バリデーションは false であること`() = runTest {
        val viewModel = BatchInputViewModel(healthRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
        assertFalse("初期状態では無効", viewModel.isInputValid.value)
    }

    @Test
    fun `有効な入力が行われた場合、バリデーションが true になること`() = runTest {
        val viewModel = BatchInputViewModel(healthRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
        
        // 状態の変化を監視するために収集を開始する（stateIn のため）
        backgroundScope.launch(testDispatcher) {
            viewModel.isInputValid.collect {}
        }

        // 体重を入力
        viewModel.weight.value = "60.5"
        assertTrue("体重のみで有効", viewModel.isInputValid.value)
        
        viewModel.weight.value = ""
        assertFalse("空にすると無効", viewModel.isInputValid.value)
        
        // 体温を入力
        viewModel.bodyTemperature.value = "36.5"
        assertTrue("体温のみで有効", viewModel.isInputValid.value)
    }

    @Test
    fun `記録日時に既にデータが存在する場合、保存がブロックされエラーが表示されること`() = runTest {
        val viewModel = BatchInputViewModel(healthRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
        viewModel.loadPerson(1)

        val uiEvents = mutableListOf<BaseViewModel.UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEventFlow.collect { uiEvents.add(it) }
        }

        val now = Instant.now()
        viewModel.setRecordTime(now)
        
        // 身長・体重を入力
        viewModel.height.value = "170"
        viewModel.weight.value = "65"

        // 重複データが存在すると設定
        coEvery { healthRepository.findHeightAndWeightAtTime(1, now) } returns mockk<HeightAndWeight>()

        viewModel.saveBatch()

        // エラーダイアログのイベントが発行されているか確認
        val errorEvent = uiEvents.filterIsInstance<BaseViewModel.UiEvent.ShowErrorDialogRes>().firstOrNull()
        assertTrue("エラーイベントが発行されていること", errorEvent != null)
        assertEquals(R.string.common_error_title_save, errorEvent?.titleResId)
        
        // 保存処理が呼ばれていないこと
        coVerify(exactly = 0) { healthRepository.insertHeightAndWeight(any(), any(), any()) }
    }

    @Test
    fun `重複がない場合、複数のカテゴリが正常に一括保存されること`() = runTest {
        val viewModel = BatchInputViewModel(healthRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
        viewModel.loadPerson(1)

        val uiEvents = mutableListOf<BaseViewModel.UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEventFlow.collect { uiEvents.add(it) }
        }

        val now = Instant.now()
        viewModel.setRecordTime(now)
        
        // 全カテゴリに入力
        viewModel.height.value = "170"
        viewModel.weight.value = "65"
        viewModel.bpSystolic.value = "120"
        viewModel.bpDiastolic.value = "80"
        viewModel.glucose.value = "100"

        // 重複なし
        coEvery { healthRepository.findHeightAndWeightAtTime(any(), any()) } returns null
        coEvery { healthRepository.findBpAndPulseAtTime(any(), any()) } returns null
        coEvery { healthRepository.findGlucoseAndHbA1cAtTime(any(), any()) } returns null

        viewModel.saveBatch()

        // 成功イベント
        assertTrue(uiEvents.contains(BaseViewModel.UiEvent.SaveSuccess))

        // 各カテゴリの保存メソッドが呼ばれていること
        coVerify(exactly = 1) { healthRepository.insertHeightAndWeight(any(), any(), any()) }
        coVerify(exactly = 1) { healthRepository.insertBpAndPulse(any(), any(), any()) }
        coVerify(exactly = 1) { healthRepository.insertGlucoseAndHbA1c(any(), any(), any()) }
        
        // 保存後に入力がリセットされていること
        assertEquals("", viewModel.height.value)
        assertEquals("", viewModel.weight.value)
    }

    @Test
    fun `利用者切り替え時、入力内容と時刻がリセットされること`() = runTest {
        val viewModel = BatchInputViewModel(healthRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
        
        // 最初の利用者
        viewModel.loadPerson(1)
        
        viewModel.weight.value = "70"
        val oldTime = Instant.now().minusSeconds(3600)
        viewModel.setRecordTime(oldTime)
        
        // 別の利用者に切り替え
        coEvery { personRepository.getPersonById(2) } returns flowOf(testPerson.copy(id = 2))
        viewModel.loadPerson(2)
        
        assertEquals("入力がリセットされている", "", viewModel.weight.value)
        assertTrue("時刻が更新されている", viewModel.recordTime.value.isAfter(oldTime))
    }

    @Test
    fun `LG-01_一括保存失敗時にisSavingがfalseになり監査ログが記録されること`() = runTest {
        val viewModel = BatchInputViewModel(healthRepository, personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
        viewModel.loadPerson(1)

        // 体重を入力
        viewModel.weight.value = "60"
        
        coEvery { healthRepository.findHeightAndWeightAtTime(any(), any()) } returns null
        coEvery { healthRepository.insertHeightAndWeight(any(), any(), any()) } throws RuntimeException("Batch Save Error")

        viewModel.saveBatch()

        assertEquals(false, viewModel.isSaving.value)
        coVerify {
            auditLogRepository.log(
                featureName = "BatchInput",
                operation = "saveBatch",
                tableName = "health_db",
                actionType = "ERROR",
                affectedId = "1",
                details = match { it?.contains("Batch Save Error") == true },
                resultType = "OTHER_ERROR"
            )
        }
    }
}
