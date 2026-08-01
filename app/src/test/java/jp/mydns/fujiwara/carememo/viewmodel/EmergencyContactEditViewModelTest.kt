package jp.mydns.fujiwara.carememo.viewmodel

import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.EmergencyContactRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.EmergencyContactType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyContactEditViewModelTest {

    private val emergencyContactRepository = mockk<EmergencyContactRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)

    private lateinit var viewModel: EmergencyContactEditViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val personId = "person-1"
    private val testPerson = Person(
        id = personId, lastName = "山田", firstName = "太郎",
        lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ",
        birthday = Instant.now()
    )

    private val testContact = EmergencyContact(
        id = "contact-1",
        personId = personId,
        contactType = EmergencyContactType.DOCTOR.value,
        facilityName = "A病院"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // 伏せ字設定をデフォルト OFF にしてテストしやすくする
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { personRepository.getPersonById(personId) } returns flowOf(testPerson)
        every { emergencyContactRepository.getContactsByPersonId(personId) } returns flowOf(listOf(testContact))

        viewModel = EmergencyContactEditViewModel(
            personId,
            emergencyContactRepository,
            personRepository,
            userSettingsRepository,
            auditLogRepository
        )
        // 初期化時の Flow 購読を完了させる
        testDispatcher.scheduler.runCurrent()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `初期化時に利用者名と連絡先一覧が読み込まれること`() = runTest {
        assertEquals("山田　太郎", viewModel.uiState.value.personName)
        assertEquals(1, viewModel.uiState.value.contacts.size)
        assertEquals("A病院", viewModel.uiState.value.contacts[0].facilityName)
    }

    @Test
    fun `startAdd - 新規登録モードが正しく開始されること`() {
        viewModel.startAdd()
        val state = viewModel.uiState.value
        assertNotNull(state.editingContact)
        assertEquals(personId, state.editingContact?.personId)
        assertTrue(state.isEditing)
        assertFalse(state.isChanged) // 初期状態なので変更なし
    }

    @Test
    fun `startEdit - 編集モードが正しく開始されること`() {
        viewModel.startEdit(testContact)
        val state = viewModel.uiState.value
        assertEquals(testContact, state.editingContact)
        assertTrue(state.isEditing)
        assertFalse(state.isChanged)
    }

    @Test
    fun `updateEditingContact - 入力値の変更が検知されること`() {
        viewModel.startEdit(testContact)
        viewModel.updateEditingContact { it.copy(facilityName = "新しい病院") }
        
        assertTrue(viewModel.uiState.value.isChanged)
        assertEquals("新しい病院", viewModel.uiState.value.editingContact?.facilityName)
    }

    @Test
    fun `saveContact - 正常に保存処理が呼ばれること`() = runTest {
        val contactToSave = testContact.copy(facilityName = "修正後の病院")
        viewModel.startEdit(contactToSave)
        
        coEvery { emergencyContactRepository.getContactById(any()) } returns testContact
        
        // 購読を開始してからアクションを実行する
        viewModel.viewEvent.test {
            viewModel.saveContact()
            
            // 非同期処理の完了を待機
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertEquals(EmergencyContactViewEvent.SaveSuccess, awaitItem())
        }
        
        coVerify { emergencyContactRepository.updateContact(match { it.facilityName == "修正後の病院" }, any(), any()) }
    }

    @Test
    fun `deleteContact - 削除処理が呼ばれること`() = runTest {
        viewModel.deleteContact(testContact)
        coVerify { emergencyContactRepository.deleteContact(testContact, any(), any()) }
    }
}
