@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.ui.components.main.BirthEra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class PersonEditViewModelTest {

    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun `新規登録時、姓名・生年月日・識別メモの全てが一致する利用者が存在する場合、エラーとなること`() = runTest {
        val viewModel = PersonEditViewModel(-1, personRepository, userSettingsRepository, auditLogRepository)

        val uiEvents = mutableListOf<BaseViewModel.UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEventFlow.collect { uiEvents.add(it) }
        }

        // 入力値をセット
        viewModel.updateLastName("山田")
        viewModel.updateFirstName("太郎")
        viewModel.updateEra(BirthEra.SHOWA)
        viewModel.updateYear("25")
        viewModel.updateMonth("1")
        viewModel.updateDay("1")
        viewModel.updateNote("識別メモA")

        val birthday = LocalDate.of(1950, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val existingPerson = Person(
            id = 100,
            lastName = "山田",
            firstName = "太郎",
            lastNameFurigana = "ヤマダ",
            firstNameFurigana = "タロウ",
            birthday = birthday,
            note = "識別メモA"
        )

        // 姓名・生年月日・識別メモが完全一致するデータが存在する
        coEvery { personRepository.findExistingPerson(match { 
            it.lastName == "山田" && it.firstName == "太郎" && it.note == "識別メモA" 
        }) } returns existingPerson

        viewModel.save()
        advanceUntilIdle()

        val errorEvent = uiEvents.filterIsInstance<BaseViewModel.UiEvent.ShowErrorDialogRes>().firstOrNull()
        assertTrue("重複エラーイベントが発行されていること", errorEvent != null)
        coVerify(exactly = 0) { personRepository.insertPerson(any(), any(), any()) }
    }

    @Test
    fun `新規登録時、姓名・生年月日が同じでも識別メモが異なれば、保存できること`() = runTest {
        val viewModel = PersonEditViewModel(-1, personRepository, userSettingsRepository, auditLogRepository)

        val uiEvents = mutableListOf<BaseViewModel.UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEventFlow.collect { uiEvents.add(it) }
        }

        viewModel.updateLastName("山田")
        viewModel.updateFirstName("太郎")
        viewModel.updateEra(BirthEra.SHOWA)
        viewModel.updateYear("25")
        viewModel.updateMonth("1")
        viewModel.updateDay("1")
        viewModel.updateNote("識別メモB") // 既存(A)とは異なるメモ

        // リポジトリは、識別メモBでの検索には null を返す（重複なし）
        coEvery { personRepository.findExistingPerson(match { it.note == "識別メモB" }) } returns null

        viewModel.save()
        advanceUntilIdle()

        assertTrue("保存成功イベントが発行されていること", uiEvents.contains(BaseViewModel.UiEvent.SaveSuccess))
        coVerify(exactly = 1) { personRepository.insertPerson(any(), any(), any()) }
    }

    @Test
    fun `編集時、自分自身のデータを更新する場合（姓名・生年月日・メモが既存と一致）、正常に更新されること`() = runTest {
        val personId = 1
        val currentPerson = Person(
            id = personId,
            lastName = "山田",
            firstName = "太郎",
            lastNameFurigana = "ヤマダ",
            firstNameFurigana = "タロウ",
            birthday = LocalDate.of(1950, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
            note = "識別メモA"
        )
        coEvery { personRepository.getPersonById(personId) } returns flowOf(currentPerson)
        
        val viewModel = PersonEditViewModel(personId, personRepository, userSettingsRepository, auditLogRepository)
        advanceUntilIdle()

        val uiEvents = mutableListOf<BaseViewModel.UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEventFlow.collect { uiEvents.add(it) }
        }

        // 検索すると自分自身がヒットする状態
        coEvery { personRepository.findExistingPerson(any()) } returns currentPerson

        // 何も変更せずに保存
        viewModel.save()
        advanceUntilIdle()

        assertTrue("保存成功イベントが発行されていること", uiEvents.contains(BaseViewModel.UiEvent.SaveSuccess))
        coVerify(exactly = 1) { personRepository.updatePerson(match { it.id == personId }, any(), any()) }
        coVerify(exactly = 0) { personRepository.insertPerson(any(), any(), any()) } // 編集モードでは Insert しない
    }

    @Test
    fun `編集時、姓名・生年月日を変更した結果、別の利用者と重複した場合はエラーとなること`() = runTest {
        val personId = 1 // 編集中の利用者A
        val personA = Person(
            id = personId,
            lastName = "山田",
            firstName = "太郎",
            lastNameFurigana = "ヤマダ",
            firstNameFurigana = "タロウ",
            birthday = LocalDate.of(1950, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
            note = "Aのメモ"
        )
        coEvery { personRepository.getPersonById(personId) } returns flowOf(personA)

        val viewModel = PersonEditViewModel(personId, personRepository, userSettingsRepository, auditLogRepository)
        advanceUntilIdle()

        val uiEvents = mutableListOf<BaseViewModel.UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEventFlow.collect { uiEvents.add(it) }
        }

        // 別の利用者B（ID=2）が存在する
        val personB = Person(
            id = 2,
            lastName = "佐藤",
            firstName = "次郎",
            lastNameFurigana = "サトウ",
            firstNameFurigana = "ジロウ",
            birthday = LocalDate.of(1960, 5, 5).atStartOfDay(ZoneId.systemDefault()).toInstant(),
            note = "Bのメモ"
        )
        
        // 入力内容をBと同じに変更
        viewModel.updateLastName("佐藤")
        viewModel.updateFirstName("次郎")
        viewModel.updateEra(BirthEra.SHOWA)
        viewModel.updateYear("35") // 1960年
        viewModel.updateMonth("5")
        viewModel.updateDay("5")
        viewModel.updateNote("Bのメモ")

        coEvery { personRepository.findExistingPerson(match { it.lastName == "佐藤" }) } returns personB

        viewModel.save()
        advanceUntilIdle()

        val errorEvent = uiEvents.filterIsInstance<BaseViewModel.UiEvent.ShowErrorDialogRes>().firstOrNull()
        assertTrue("重複エラーイベントが発行されていること", errorEvent != null)
        coVerify(exactly = 0) { personRepository.updatePerson(any(), any(), any()) }
    }

    @Test
    fun `LG-01_データ読み込み失敗時にisLoadingがfalseになり監査ログが記録されること`() = runTest {
        val personId = 1
        coEvery { personRepository.getPersonById(personId) } returns flow { throw RuntimeException("Load Error") }

        val viewModel = PersonEditViewModel(personId, personRepository, userSettingsRepository, auditLogRepository)
        advanceUntilIdle()

        assertEquals(false, viewModel.isLoading.value)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonEdit",
                operation = "loadPerson",
                tableName = "person_db",
                actionType = "ERROR",
                affectedId = personId.toString(),
                details = match { it?.contains("Load Error") == true }
            )
        }
    }

    @Test
    fun `LG-02_保存失敗時にisLoadingがfalseになり監査ログが記録されること`() = runTest {
        val viewModel = PersonEditViewModel(-1, personRepository, userSettingsRepository, auditLogRepository)
        advanceUntilIdle()

        // 必須項目入力
        viewModel.updateLastName("山田")
        viewModel.updateFirstName("太郎")
        viewModel.updateYear("25")
        viewModel.updateMonth("1")
        viewModel.updateDay("1")

        coEvery { personRepository.findExistingPerson(any()) } returns null
        coEvery { personRepository.insertPerson(any(), any(), any()) } throws RuntimeException("Save Error")

        viewModel.save()
        advanceUntilIdle()

        assertEquals(false, viewModel.isLoading.value)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonEdit",
                operation = "save",
                tableName = "person_db",
                actionType = "ERROR",
                affectedId = any(),
                details = match { it?.contains("Save Error") == true }
            )
        }
    }
}
