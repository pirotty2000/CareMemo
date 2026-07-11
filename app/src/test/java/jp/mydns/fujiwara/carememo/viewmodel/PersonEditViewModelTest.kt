@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.ui.components.main.BirthEra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class PersonEditViewModelTest {

    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { userSettingsRepository.isNameMaskingEnabled } returns MutableStateFlow(false)
        coEvery { userSettingsRepository.defaultRecorderName } returns MutableStateFlow("")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `新規登録時、同姓同名の利用者が既に存在する場合、エラーイベントが発行されること`() = runTest {
        val viewModel = PersonEditViewModel(-1, personRepository, userSettingsRepository)

        // イベントをキャプチャするための準備
        val uiEvents = mutableListOf<BaseViewModel.UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEventFlow.collect { uiEvents.add(it) }
        }

        // 入力値をセット
        viewModel.lastName.value = "山田"
        viewModel.firstName.value = "太郎"
        viewModel.era.value = BirthEra.SHOWA
        viewModel.year.value = "25"
        viewModel.month.value = "1"
        viewModel.day.value = "1"
        viewModel.note.value = "テストメモ"

        val birthday = LocalDate.of(1950, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val existingPerson = Person(
            id = 100,
            lastName = "山田",
            firstName = "太郎",
            lastNameFurigana = "ヤマダ",
            firstNameFurigana = "タロウ",
            birthday = birthday,
            note = "テストメモ"
        )

        // リポジトリが既存データを返すように設定
        coEvery { personRepository.findExistingPerson(any()) } returns existingPerson

        viewModel.save()
        advanceUntilIdle()

        // エラーダイアログのイベントが発行されているか確認
        val errorEvent = uiEvents.filterIsInstance<BaseViewModel.UiEvent.ShowErrorDialogRes>().firstOrNull()
        assertTrue("エラーイベントが発行されていること", errorEvent != null)
        assertTrue(errorEvent?.titleResId == R.string.main_err_title_duplicate_archived_add)
        assertTrue(errorEvent?.messageResId == R.string.main_err_duplicate_active)
        
        // 保存処理（insert）が呼ばれていないことを確認
        coVerify(exactly = 0) { personRepository.insertPerson(any()) }
    }

    @Test
    fun `編集時、自分以外の同姓同名の利用者が存在する場合、エラーイベントが発行されること`() = runTest {
        // ID=1 の利用者を編集
        val currentPerson = Person(
            id = 1,
            lastName = "山田",
            firstName = "太郎",
            lastNameFurigana = "ヤマダ",
            firstNameFurigana = "タロウ",
            birthday = LocalDate.of(1950, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
            note = "元々のメモ"
        )
        coEvery { personRepository.getPersonById(1) } returns MutableStateFlow(currentPerson)
        
        val viewModel = PersonEditViewModel(1, personRepository, userSettingsRepository)
        advanceUntilIdle()

        // イベントキャプチャ開始
        val uiEvents = mutableListOf<BaseViewModel.UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEventFlow.collect { uiEvents.add(it) }
        }

        // 重複する別人（ID=2）が存在する設定
        val otherPerson = Person(
            id = 2,
            lastName = "山田",
            firstName = "太郎",
            lastNameFurigana = "ヤマダ",
            firstNameFurigana = "タロウ",
            birthday = LocalDate.of(1950, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
            note = "重複するメモ"
        )
        coEvery { personRepository.findExistingPerson(any()) } returns otherPerson

        // 重複する内容に変更
        viewModel.note.value = "重複するメモ"

        viewModel.save()
        advanceUntilIdle()

        val errorEvent = uiEvents.filterIsInstance<BaseViewModel.UiEvent.ShowErrorDialogRes>().firstOrNull()
        assertTrue("エラーイベントが発行されていること", errorEvent != null)
        assertTrue(errorEvent?.titleResId == R.string.main_err_title_duplicate_archived_update)
        
        // 更新処理（update）が呼ばれていないことを確認
        coVerify(exactly = 0) { personRepository.updatePerson(any()) }
    }

    @Test
    fun `編集時、識別メモを変更して重複を回避した場合、正常に保存されること`() = runTest {
        val currentPerson = Person(
            id = 1,
            lastName = "山田",
            firstName = "太郎",
            lastNameFurigana = "ヤマダ",
            firstNameFurigana = "タロウ",
            birthday = LocalDate.of(1950, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
            note = "重複していたメモ"
        )
        coEvery { personRepository.getPersonById(1) } returns MutableStateFlow(currentPerson)

        val viewModel = PersonEditViewModel(1, personRepository, userSettingsRepository)
        advanceUntilIdle()

        // イベントキャプチャ開始
        val uiEvents = mutableListOf<BaseViewModel.UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEventFlow.collect { uiEvents.add(it) }
        }

        // リポジトリは「一致なし」を返す（識別メモを変えたので見つからない）
        coEvery { personRepository.findExistingPerson(any()) } returns null

        // 識別メモを変更
        viewModel.note.value = "識別用の新しいメモ"

        viewModel.save()
        advanceUntilIdle()

        // 正常保存のイベントが発行されているか確認
        assertTrue("保存成功イベントが発行されていること", uiEvents.contains(BaseViewModel.UiEvent.SaveSuccess))
        
        // 更新処理（update）が呼ばれていることを確認
        coVerify(exactly = 1) { personRepository.updatePerson(match { it.id == 1 && it.note == "識別用の新しいメモ" }) }
    }
}
