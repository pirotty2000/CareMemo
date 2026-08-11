package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.EmergencyContactRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.EmergencyContactType
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
 * Logic Test: EmergencyContactEditViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyContactEditViewModelTest {

    private val emergencyContactRepository = mockk<EmergencyContactRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private val personId = "u1"
    private val testPerson = Person(
        id = personId, lastName = "山田", firstName = "太郎",
        lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    private val testContact = EmergencyContact(
        id = "c1",
        personId = personId,
        contactType = EmergencyContactType.DOCTOR.value,
        facilityName = "A病院"
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)

        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { personRepository.getPersonById(personId) } returns flowOf(testPerson)
        every { emergencyContactRepository.getContactsByPersonId(personId) } returns flowOf(listOf(testContact))
        coEvery { emergencyContactRepository.getContactById("c1") } returns testContact
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun createViewModel(handleParams: Map<String, Any> = mapOf("personId" to personId)): EmergencyContactEditViewModel {
        return EmergencyContactEditViewModel(
            SavedStateHandle(handleParams),
            emergencyContactRepository,
            personRepository,
            userSettingsRepository,
            auditLogRepository
        )
    }

    // region 2. 初期化・データロードテスト (Initialization)

    @Test
    fun INI_01_INI_02_loadPersonAndContacts() = runTest {
        val viewModel = createViewModel()
        
        viewModel.uiState.test {
            // Skip intermediate state transitions during initialization
            advanceUntilIdle()
            
            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            // Expecting unmasked name as mock is set to false
            assertEquals("山田　太郎", state.personName)
            assertEquals(1, state.contacts.size)
            assertEquals("A病院", state.contacts[0].facilityName)
        }
    }

    @Test
    fun INI_03_loadContactIdAtLaunch() = runTest {
        val viewModel = createViewModel(mapOf("personId" to personId, "contactId" to "c1"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEditing)
        assertEquals("c1", viewModel.uiState.value.editingContact?.id)
    }

    @Test
    fun INI_04_startAddAtLaunchWithNullContactId() = runTest {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val handle = SavedStateHandle(mapOf("personId" to personId))
        handle["contactId"] = null 

        val viewModel = EmergencyContactEditViewModel(handle, emergencyContactRepository, personRepository, userSettingsRepository, auditLogRepository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEditing)
        assertEquals(newId, viewModel.uiState.value.editingContact?.id)
    }

    // endregion

    // region 3. 編集・操作テスト (Editing)

    @Test
    fun EDT_01_startAdd() = runTest {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startAdd()
        val state = viewModel.uiState.value
        assertEquals(newId, state.editingContact?.id)
        assertTrue(state.isEditing)
    }

    @Test
    fun EDT_03_updateEditingContact_triggersChangeDetection() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startEdit(testContact)
        viewModel.updateEditingContact { it.copy(facilityName = "Updated") }
        
        assertTrue(viewModel.uiState.value.isChanged)
        assertEquals("Updated", viewModel.uiState.value.editingContact?.facilityName)
    }

    @Test
    fun EDT_04_dismissEdit_clearsInput() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startAdd()
        viewModel.dismissEdit()
        
        assertFalse(viewModel.uiState.value.isEditing)
        assertNull(viewModel.uiState.value.editingContact)
    }

    // endregion

    // region 4. 処理実行テスト (Execution)

    @Test
    fun SAV_01_saveContact_new() = runTest {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startAdd()
        viewModel.updateEditingContact { it.copy(facilityName = "New clinic") }

        viewModel.viewEvent.test {
            viewModel.saveContact()
            advanceUntilIdle()
            assertEquals(EmergencyContactViewEvent.SaveSuccess, awaitItem())
        }

        coVerify { emergencyContactRepository.insertContact(match { it.facilityName == "New clinic" }, any(), any()) }
    }

    @Test
    fun SAV_02_saveContact_update() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startEdit(testContact)
        viewModel.updateEditingContact { it.copy(facilityName = "Modified") }

        viewModel.viewEvent.test {
            viewModel.saveContact()
            advanceUntilIdle()
            assertEquals(EmergencyContactViewEvent.SaveSuccess, awaitItem())
        }

        coVerify { emergencyContactRepository.updateContact(match { it.id == "c1" && it.facilityName == "Modified" }, any(), any()) }
    }

    @Test
    fun DEL_01_deleteContact() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.viewEvent.test {
            viewModel.deleteContact(testContact)
            advanceUntilIdle()
            assertEquals(EmergencyContactViewEvent.DeleteSuccess, awaitItem())
        }
        coVerify { emergencyContactRepository.deleteContact(testContact, any(), any()) }
    }

    // endregion

    // region 5. 安全性・例外テスト (Safety)

    @Test
    fun ERR_01_saveFailure_safety() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { emergencyContactRepository.insertContact(any(), any(), any()) } throws RuntimeException("Save failed")
        
        viewModel.startAdd()
        viewModel.updateEditingContact { it.copy(facilityName = "Error Trigger") }
        viewModel.saveContact()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        coVerify { auditLogRepository.log(any(), any(), any(), "ERROR", any(), any(), any()) }
    }

    // endregion
}
