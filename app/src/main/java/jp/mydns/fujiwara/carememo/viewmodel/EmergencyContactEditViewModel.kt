package jp.mydns.fujiwara.carememo.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import androidx.navigation.toRoute
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.EmergencyContactRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.EmergencyContactLogic
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.Instant

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
    val isNameMaskingEnabled: Boolean = true,
    val isChanged: Boolean = false,
    val isValid: Boolean = false
)

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
 *
 * 【役割】
 * 特定の利用者に紐付く緊急連絡先の一覧表示、および新規追加・編集画面の状態管理と保存を制御します。
 * 
 * 【設計指針：レイヤー責務】
 * 1. 複数モードの統合：一覧表示と個別の編集セッションを単一の ViewModel でシームレスに切り替えます。
 * 2. 状態管理の標準化: `updateState` ヘルパーを通じて `isChanged` および `isValid` を算出し、
 *    データクラスのプロパティとして保持することで、UI 層への単一方向データフローを維持します。
 *
 * 【この ViewModel では行わないこと】
 * ・緊急連絡先の保存用 Entity の詳細な構築ロジック（EmergencyContactLogic が担当）。
 * ・電話番号の書式整形（Logic または UI 側の VisualTransformation が担当）。
 */
class EmergencyContactEditViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val emergencyContactRepository: EmergencyContactRepository,
    private val personRepository: PersonRepository,
    userSettingsRepository: UserSettingsRepository,
    securitySession: SecuritySession,
    auditLogRepository: AuditLogRepository
) : BaseUiStateViewModel<EmergencyContactUiState, EmergencyContactViewEvent>(
    userSettingsRepository,
    securitySession,
    EmergencyContactUiState()
) {

    companion object {
        private const val FEATURE_NAME = "MedicalContact"
        private const val OP_SAVE = "saveContact"
        private const val OP_DELETE = "deleteContact"
        private const val TABLE_NAME = "emergency_contact_db"

        // --- Shared Keys (Navigation & Restoration) ---
        private const val KEY_PERSON_ID = "personId"
        private const val KEY_CONTACT_ID = "contactId"
        private const val KEY_RESTORE_VERSION = "restoration_version"
        private const val RESTORE_VERSION = 1
        private const val KEY_IS_EDITING = "restoration_is_editing"
        
        // Input Fields (Current)
        private const val KEY_IN_ID = "restoration_in_id"
        private const val KEY_IN_TYPE = "restoration_in_type"
        private const val KEY_IN_FACILITY = "restoration_in_facility"
        private const val KEY_IN_PERSON_NAME = "restoration_in_person_name"
        private const val KEY_IN_PHONE = "restoration_in_phone"
        private const val KEY_IN_PRIORITY = "restoration_in_priority"

        // Snapshot Fields (Baseline)
        private const val KEY_BASE_ID = "restoration_base_id"
        private const val KEY_BASE_TYPE = "restoration_base_type"
        private const val KEY_BASE_FACILITY = "restoration_base_facility"
        private const val KEY_BASE_PERSON_NAME = "restoration_base_person_name"
        private const val KEY_BASE_PHONE = "restoration_base_phone"
        private const val KEY_BASE_PRIORITY = "restoration_base_priority"
    }

    override val featureName: String = FEATURE_NAME

    /** 復元中であることを示すフラグ */
    private var isRestoring = false

    /** 保存処理用の Job */
    private var saveJob: Job? = null

    /** 削除処理用の Job */
    private var deleteJob: Job? = null

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // 1. 引数から personId を先に確定させる（復元の前提条件）
        val personId = savedStateHandle.get<String>(KEY_PERSON_ID) 
            ?: try { savedStateHandle.toRoute<Destination.MedicalContacts>().personId } catch (_: Exception) { null }
            ?: try { savedStateHandle.toRoute<Destination.MedicalContactEdit>().personId } catch (_: Exception) { "" }

        if (personId.isNotBlank()) {
            updateUiState { it.copy(personId = personId) }
            loadPersonInfo(personId)
            loadEmergencyContacts(personId)
        }

        // 2. 状態復元
        if (savedStateHandle.contains(KEY_RESTORE_VERSION)) {
            isRestoring = true
            restoreState()
        }

        // 3. ナビゲーション引数に基づく初期化（復元中でない場合のみ）
        if (!isRestoring && personId.isNotBlank()) {
            initializeFromNavigation()
        }

        // 共通設定の変更を購読
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }
    }

    private fun initializeFromNavigation() {
        val contactId = savedStateHandle.get<String>(KEY_CONTACT_ID)
            ?: try { savedStateHandle.toRoute<Destination.MedicalContactEdit>().contactId } catch (_: Exception) { null }

        if (savedStateHandle.contains(KEY_CONTACT_ID) || contactId != null) {
            if (IdLogic.isNew(contactId)) {
                startAdd()
            } else if (contactId != null) {
                scope.launch {
                    val contact = emergencyContactRepository.getContactById(contactId)
                    if (contact != null) startEdit(contact)
                }
            }
        }
    }

    /**
     * SavedStateHandle から状態を復元します。
     */
    private fun restoreState() {
        val handle = savedStateHandle ?: return
        val isEditing = handle.get<Boolean>(KEY_IS_EDITING) ?: false
        
        // 1. Current Input の復元
        val input = if (handle.contains(KEY_IN_ID)) {
            EmergencyContact(
                id = handle.get<String>(KEY_IN_ID) ?: "",
                personId = currentState.personId, // personId は navArgs から別途復元
                contactType = handle.get<String>(KEY_IN_TYPE) ?: "DOCTOR",
                facilityName = handle.get<String>(KEY_IN_FACILITY) ?: "",
                personName = handle.get<String>(KEY_IN_PERSON_NAME),
                phoneNumber = handle.get<String>(KEY_IN_PHONE),
                priority = handle.get<Int>(KEY_IN_PRIORITY) ?: 99
            )
        } else null

        // 2. Baseline の復元
        val snapshot = if (handle.contains(KEY_BASE_ID)) {
            EmergencyContact(
                id = handle.get<String>(KEY_BASE_ID) ?: "",
                personId = currentState.personId,
                contactType = handle.get<String>(KEY_BASE_TYPE) ?: "DOCTOR",
                facilityName = handle.get<String>(KEY_BASE_FACILITY) ?: "",
                personName = handle.get<String>(KEY_BASE_PERSON_NAME),
                phoneNumber = handle.get<String>(KEY_BASE_PHONE),
                priority = handle.get<Int>(KEY_BASE_PRIORITY) ?: 99
            )
        } else null

        updateUiState { current ->
            current.copy(
                isEditing = isEditing,
                editingContact = input,
                initialContact = snapshot,
                isChanged = EmergencyContactLogic.isChanged(input, snapshot),
                isValid = EmergencyContactLogic.isValid(input)
            )
        }
    }

    /**
     * 復元対象の状態をバックアップします。
     */
    private fun backupRestorableState(state: EmergencyContactUiState) {
        val handle = savedStateHandle ?: return
        handle[KEY_RESTORE_VERSION] = RESTORE_VERSION
        handle[KEY_IS_EDITING] = state.isEditing

        // Input Backup
        state.editingContact?.let { contact ->
            handle[KEY_IN_ID] = contact.id
            handle[KEY_IN_TYPE] = contact.contactType
            handle[KEY_IN_FACILITY] = contact.facilityName
            handle[KEY_IN_PERSON_NAME] = contact.personName
            handle[KEY_IN_PHONE] = contact.phoneNumber
            handle[KEY_IN_PRIORITY] = contact.priority
        }

        // Baseline Backup
        state.initialContact?.let { base ->
            handle[KEY_BASE_ID] = base.id
            handle[KEY_BASE_TYPE] = base.contactType
            handle[KEY_BASE_FACILITY] = base.facilityName
            handle[KEY_BASE_PERSON_NAME] = base.personName
            handle[KEY_BASE_PHONE] = base.phoneNumber
            handle[KEY_BASE_PRIORITY] = base.priority
        }
    }

    /**
     * 復元用データを破棄します。
     */
    private fun clearRestorableState() {
        val handle = savedStateHandle ?: return
        handle.remove<Int>(KEY_RESTORE_VERSION)
        handle.remove<Boolean>(KEY_IS_EDITING)
        
        listOf(
            KEY_IN_ID, KEY_IN_TYPE, KEY_IN_FACILITY, KEY_IN_PERSON_NAME, KEY_IN_PHONE, KEY_IN_PRIORITY,
            KEY_BASE_ID, KEY_BASE_TYPE, KEY_BASE_FACILITY, KEY_BASE_PERSON_NAME, KEY_BASE_PHONE, KEY_BASE_PRIORITY
        ).forEach { handle.remove<Any>(it) }
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
        updateState {
            val next = it.copy(
                editingContact = initial,
                initialContact = initial,
                isEditing = true
            )
            backupRestorableState(next)
            next
        }
    }

    fun startEdit(contact: EmergencyContact) {
        updateState {
            val next = it.copy(
                editingContact = contact,
                initialContact = contact,
                isEditing = true
            )
            backupRestorableState(next)
            next
        }
    }

    fun updateEditingContact(reducer: (EmergencyContact) -> EmergencyContact) {
        updateState { current ->
            val next = current.editingContact?.let {
                current.copy(editingContact = reducer(it))
            } ?: current
            backupRestorableState(next)
            next
        }
    }

    fun dismissEdit() {
        updateState { 
            val next = it.copy(isEditing = false, editingContact = null, initialContact = null)
            clearRestorableState()
            next
        }
    }

    /**
     * UiState の更新と同時に、バリデーション (isValid) および 変更検知 (isChanged) を実行するヘルパー。
     */
    private fun updateState(reducer: (EmergencyContactUiState) -> EmergencyContactUiState) {
        updateUiState { current ->
            val next = reducer(current)
            next.copy(
                isChanged = EmergencyContactLogic.isChanged(next.editingContact, next.initialContact),
                isValid = EmergencyContactLogic.isValid(next.editingContact)
            )
        }
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
            val isUpdate = !IdLogic.isNew(contact.id)
            val normalizedContact = EmergencyContactLogic.createSaveEntity(contact)

            // 新規の場合は ID を確定させる (ADR #8)
            val contactToSave = if (isUpdate) {
                normalizedContact
            } else {
                normalizedContact.copy(id = java.util.UUID.randomUUID().toString())
            }

            emergencyContactRepository.saveContact(
                contact = contactToSave,
                isUpdate = isUpdate,
                featureName = featureName,
                operation = OP_SAVE
            )

            sendViewEvent(EmergencyContactViewEvent.SaveSuccess)
            dismissEdit()
            clearRestorableState()
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
        private val securitySession: SecuritySession,
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
                securitySession,
                auditLogRepository
            ) as T
        }
    }
}
