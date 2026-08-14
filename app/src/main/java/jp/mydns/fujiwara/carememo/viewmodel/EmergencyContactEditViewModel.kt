package jp.mydns.fujiwara.carememo.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.EmergencyContactRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.EmergencyContactLogic
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * UI State：EmergencyContactUiState
 */
@Immutable
data class EmergencyContactUiState(
    val isLoading: Boolean = false,
    val personId: String = "",
    val contacts: ImmutableList<EmergencyContact> = persistentListOf(),
    val editingContact: EmergencyContact? = null,
    val initialContact: EmergencyContact? = null,
    val isEditing: Boolean = false,
    val personName: String = "",
    val isNameMaskingEnabled: Boolean = true
) {
    val isChanged: Boolean get() = EmergencyContactLogic.isChanged(editingContact, initialContact)
    val isValid: Boolean get() = EmergencyContactLogic.isValid(editingContact)
}

/**
 * View Event：EmergencyContactViewEvent
 */
sealed interface EmergencyContactViewEvent {
    object NavigateBack : EmergencyContactViewEvent
    object SaveSuccess : EmergencyContactViewEvent
    object DeleteSuccess : EmergencyContactViewEvent
}

/**
 * ViewModel：EmergencyContactEditViewModel
 */
class EmergencyContactEditViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val emergencyContactRepository: EmergencyContactRepository,
    private val personRepository: PersonRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : BaseUiStateViewModel<EmergencyContactUiState, EmergencyContactViewEvent>(
    userSettingsRepository,
    EmergencyContactUiState()
) {

    companion object {
        private const val FEATURE_NAME = "MedicalContact"
        private const val OP_SAVE = "saveContact"
        private const val OP_DELETE = "deleteContact"
        private const val TABLE_NAME = "emergency_contact_db"
        private const val KEY_PERSON_ID = "personId"
        private const val KEY_CONTACT_ID = "contactId"
    }

    override val featureName: String = FEATURE_NAME

    /** 保存処理用の Job */
    private var saveJob: Job? = null

    /** 削除処理用の Job */
    private var deleteJob: Job? = null

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        observeParams()

        // 共通設定の変更を購読
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }
    }

    private fun observeParams() {
        // personId の監視
        scope.launch {
            savedStateHandle.getStateFlow<String?>(KEY_PERSON_ID, null).collect { id ->
                if (!id.isNullOrBlank()) {
                    updateUiState { it.copy(personId = id) }
                    loadPersonInfo(id)
                    loadEmergencyContacts(id)
                }
            }
        }

        // contactId の監視（編集画面用）
        scope.launch {
            savedStateHandle.getStateFlow<String?>(KEY_CONTACT_ID, null).collect { id ->
                // KEY_CONTACT_ID が存在する場合（MedicalContactEdit 目的地の場合）のみ処理
                if (savedStateHandle.contains(KEY_CONTACT_ID)) {
                    if (id == null) {
                        startAdd()
                    } else {
                        val contact = emergencyContactRepository.getContactById(id)
                        if (contact != null) {
                            startEdit(contact)
                        }
                    }
                }
            }
        }
    }

    private fun loadPersonInfo(id: String) {
        safeLaunch(
            operation = "loadPersonInfo",
            contextBuilder = { tableName = "person_db"; affectedId = id }
        ) {
            combine(
                personRepository.getPersonById(id).filterNotNull(),
                isNameMaskingEnabled
            ) { person, masking ->
                person.getMaskedName(masking)
            }.collect { maskedName ->
                updateUiState { it.copy(personName = maskedName) }
            }
        }
    }

    private fun loadEmergencyContacts(id: String) {
        safeCollect(
            operation = "loadEmergencyContacts",
            mode = CollectMode.INITIAL,
            loadingState = loadingStateProxy,
            contextBuilder = { tableName = TABLE_NAME; affectedId = id },
            flowProvider = { emergencyContactRepository.getContactsByPersonId(id) }
        ) { contacts ->
            updateUiState { it.copy(contacts = contacts.toImmutableList()) }
        }
    }

    fun startAdd() {
        val initial = EmergencyContactLogic.createInitialEntity(currentState.personId)
        updateUiState {
            it.copy(
                editingContact = initial,
                initialContact = initial,
                isEditing = true
            )
        }
    }

    fun startEdit(contact: EmergencyContact) {
        updateUiState {
            it.copy(
                editingContact = contact,
                initialContact = contact,
                isEditing = true
            )
        }
    }

    fun updateEditingContact(reducer: (EmergencyContact) -> EmergencyContact) {
        updateUiState { current ->
            current.editingContact?.let {
                current.copy(editingContact = reducer(it))
            } ?: current
        }
    }

    fun dismissEdit() {
        updateUiState { it.copy(isEditing = false, editingContact = null, initialContact = null) }
    }

    fun saveContact() {
        // 二重保存防止
        if (saveJob?.isActive == true) return

        val contact = currentState.editingContact ?: return
        
        saveJob = safeLaunch(
            operation = OP_SAVE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_NAME
                affectedId = contact.id
            }
        ) {
            val contactToSave = EmergencyContactLogic.createSaveEntity(contact)
            
            if (IdLogic.isNew(contactToSave.id)) {
                emergencyContactRepository.insertContact(contactToSave, featureName, OP_SAVE)
            } else {
                emergencyContactRepository.updateContact(contactToSave, featureName, OP_SAVE)
            }
            
            sendViewEvent(EmergencyContactViewEvent.SaveSuccess)
            dismissEdit()
        }
    }

    fun deleteContact(contact: EmergencyContact) {
        // 二重実行防止
        if (deleteJob?.isActive == true) return

        deleteJob = safeLaunch(
            operation = OP_DELETE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_NAME
                affectedId = contact.id
            }
        ) {
            emergencyContactRepository.deleteContact(contact, featureName, OP_DELETE)
            sendViewEvent(EmergencyContactViewEvent.DeleteSuccess)
        }
    }

    override fun copyWithLoadingState(state: EmergencyContactUiState, isLoading: Boolean): EmergencyContactUiState {
        return state.copy(isLoading = isLoading)
    }

    class Factory(
        private val emergencyContactRepository: EmergencyContactRepository,
        private val personRepository: PersonRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return EmergencyContactEditViewModel(
                savedStateHandle,
                emergencyContactRepository,
                personRepository,
                userSettingsRepository,
                auditLogRepository
            ) as T
        }
    }
}
