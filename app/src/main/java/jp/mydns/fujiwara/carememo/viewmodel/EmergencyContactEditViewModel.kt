package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.EmergencyContactRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.EmergencyContactLogic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 緊急連絡先管理画面用の UI 状態
 */
data class EmergencyContactUiState(
    val isLoading: Boolean = false,
    val contacts: List<EmergencyContact> = emptyList(),
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
 * 緊急連絡先管理画面用のビューイベント
 */
sealed interface EmergencyContactViewEvent {
    object SaveSuccess : EmergencyContactViewEvent
    object DeleteSuccess : EmergencyContactViewEvent
}

/**
 * 緊急連絡先管理画面 (SCR-M-003) および編集画面用の ViewModel
 */
class EmergencyContactEditViewModel(
    private val personId: String,
    private val emergencyContactRepository: EmergencyContactRepository,
    private val personRepository: PersonRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : BaseUiStateViewModel<EmergencyContactUiState, EmergencyContactViewEvent>(
    userSettingsRepository,
    EmergencyContactUiState()
) {
    companion object {
        private const val FEATURE_NAME = "EmergencyContact"
        private const val TABLE_NAME = "emergency_contact_db"
    }

    override val featureName: String = FEATURE_NAME

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // 利用者名の読み込み
        scope.launch {
            personRepository.getPersonById(personId).first()?.let { person ->
                updateUiState { it.copy(personName = person.getMaskedName(currentState.isNameMaskingEnabled)) }
            }
        }

        // 共通設定の同期
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
                // 設定が変わったら表示名も更新
                personRepository.getPersonById(personId).first()?.let { person ->
                    updateUiState { it.copy(personName = person.getMaskedName(enabled)) }
                }
            }
        }

        loadContacts()
    }

    private fun loadContacts() {
        safeCollect(
            operation = "loadContacts",
            mode = CollectMode.INITIAL,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_NAME
                affectedId = personId
            },
            flowProvider = { emergencyContactRepository.getContactsByPersonId(personId) }
        ) { contacts ->
            updateUiState { it.copy(contacts = contacts) }
        }
    }

    override fun copyWithLoadingState(state: EmergencyContactUiState, isLoading: Boolean): EmergencyContactUiState {
        return state.copy(isLoading = isLoading)
    }

    /** 追加モード開始 */
    fun startAdd() {
        val newContact = EmergencyContactLogic.createInitialEntity(personId)
        updateUiState {
            it.copy(
                editingContact = newContact,
                initialContact = newContact,
                isEditing = true
            )
        }
    }

    /** 編集モード開始 */
    fun startEdit(contact: EmergencyContact) {
        updateUiState {
            it.copy(
                editingContact = contact,
                initialContact = contact,
                isEditing = true
            )
        }
    }

    /** 編集中の連絡先状態を更新 */
    fun updateEditingContact(reducer: (EmergencyContact) -> EmergencyContact) {
        updateUiState { state ->
            state.editingContact?.let {
                state.copy(editingContact = reducer(it))
            } ?: state
        }
    }

    /** 保存実行 */
    fun saveContact() {
        val contact = currentState.editingContact ?: return

        if (!currentState.isValid) return

        safeLaunch(
            operation = "save",
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_NAME
                affectedId = contact.id
            }
        ) {
            val entityToSave = EmergencyContactLogic.createSaveEntity(contact)
            val existing = emergencyContactRepository.getContactById(entityToSave.id)
            if (existing == null) {
                emergencyContactRepository.insertContact(entityToSave, FEATURE_NAME, "insert")
            } else {
                emergencyContactRepository.updateContact(entityToSave, FEATURE_NAME, "update")
            }
            updateUiState { it.copy(isEditing = false, editingContact = null) }
            sendViewEvent(EmergencyContactViewEvent.SaveSuccess)
        }
    }

    /** 削除実行 */
    fun deleteContact(contact: EmergencyContact) {
        safeLaunch(
            operation = "delete",
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_NAME
                affectedId = contact.id
            }
        ) {
            emergencyContactRepository.deleteContact(contact, FEATURE_NAME, "delete")
            sendViewEvent(EmergencyContactViewEvent.DeleteSuccess)
        }
    }

    class Factory(
        private val personId: String,
        private val emergencyContactRepository: EmergencyContactRepository,
        private val personRepository: PersonRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EmergencyContactEditViewModel(
                personId,
                emergencyContactRepository,
                personRepository,
                userSettingsRepository,
                auditLogRepository
            ) as T
        }
    }
}
