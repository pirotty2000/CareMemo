package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import androidx.navigation.toRoute
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.EmergencyContactRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.EmergencyContactLogic
import jp.mydns.fujiwara.carememo.logic.feature.EmergencyContactOperation
import jp.mydns.fujiwara.carememo.logic.feature.EmergencyContactScreenState
import jp.mydns.fujiwara.carememo.logic.feature.EmergencyContactSession
import jp.mydns.fujiwara.carememo.logic.feature.EmergencyContactUiState
import jp.mydns.fujiwara.carememo.logic.feature.EmergencyContactValidationResult
import jp.mydns.fujiwara.carememo.logic.feature.EmergencyContactViewEvent
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * ViewModel：EmergencyContactEditViewModel
 *
 * 【役割】
 * 特定の利用者に紐付く緊急連絡先の一覧表示、および新規追加・編集画面の状態管理と保存を制御します。
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

        private const val KEY_PERSON_ID = "personId"
        private const val KEY_CONTACT_ID = "contactId"
        private const val KEY_RESTORE_VERSION = "restoration_version"
        private const val RESTORE_VERSION = 1
        private const val KEY_IS_EDITING = "restoration_is_editing"
        
        private const val KEY_IN_ID = "restoration_in_id"
        private const val KEY_IN_TYPE = "restoration_in_type"
        private const val KEY_IN_FACILITY = "restoration_in_facility"
        private const val KEY_IN_PERSON_NAME = "restoration_in_person_name"
        private const val KEY_IN_PHONE = "restoration_in_phone"
        private const val KEY_IN_PRIORITY = "restoration_in_priority"

        private const val KEY_BASE_ID = "restoration_base_id"
        private const val KEY_BASE_TYPE = "restoration_base_type"
        private const val KEY_BASE_FACILITY = "restoration_base_facility"
        private const val KEY_BASE_PERSON_NAME = "restoration_base_person_name"
        private const val KEY_BASE_PHONE = "restoration_base_phone"
        private const val KEY_BASE_PRIORITY = "restoration_base_priority"
    }

    override val featureName: String = FEATURE_NAME

    private var isRestoring = false
    private var saveJob: Job? = null
    private var deleteJob: Job? = null

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        val personId = savedStateHandle.get<String>(KEY_PERSON_ID) 
            ?: try { savedStateHandle.toRoute<Destination.MedicalContacts>().personId } catch (_: Exception) { null }
            ?: try { savedStateHandle.toRoute<Destination.MedicalContactEdit>().personId } catch (_: Exception) { "" }

        if (personId.isNotBlank()) {
            updateUiState { it.copy(personId = personId) }
            loadPersonInfo(personId)
            loadEmergencyContacts(personId)
        }

        if (savedStateHandle.contains(KEY_RESTORE_VERSION)) {
            isRestoring = true
            restoreState()
        }

        if (!isRestoring && personId.isNotBlank()) {
            initializeFromNavigation()
        }

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

    private fun restoreState() {
        val handle = savedStateHandle
        val isEditing = handle.get<Boolean>(KEY_IS_EDITING) ?: false
        
        val input = if (handle.contains(KEY_IN_ID)) {
            EmergencyContact(
                id = handle.get<String>(KEY_IN_ID) ?: "",
                personId = currentState.personId,
                contactType = handle.get<String>(KEY_IN_TYPE) ?: "DOCTOR",
                facilityName = handle.get<String>(KEY_IN_FACILITY) ?: "",
                personName = handle.get<String>(KEY_IN_PERSON_NAME),
                phoneNumber = handle.get<String>(KEY_IN_PHONE),
                priority = handle.get<Int>(KEY_IN_PRIORITY) ?: 99
            )
        } else null

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
                screenState = EmergencyContactScreenState.Active,
                session = EmergencyContactSession(
                    isEditing = isEditing,
                    editingContact = input,
                    initialContact = snapshot,
                    isChanged = EmergencyContactLogic.isChanged(input, snapshot),
                    isValid = EmergencyContactLogic.isValid(input)
                )
            )
        }
    }

    private fun backupRestorableState(state: EmergencyContactUiState) {
        val handle = savedStateHandle
        val session = state.session
        handle[KEY_RESTORE_VERSION] = RESTORE_VERSION
        handle[KEY_IS_EDITING] = session.isEditing

        session.editingContact?.let { contact ->
            handle[KEY_IN_ID] = contact.id
            handle[KEY_IN_TYPE] = contact.contactType
            handle[KEY_IN_FACILITY] = contact.facilityName
            handle[KEY_IN_PERSON_NAME] = contact.personName
            handle[KEY_IN_PHONE] = contact.phoneNumber
            handle[KEY_IN_PRIORITY] = contact.priority
        }

        session.initialContact?.let { base ->
            handle[KEY_BASE_ID] = base.id
            handle[KEY_BASE_TYPE] = base.contactType
            handle[KEY_BASE_FACILITY] = base.facilityName
            handle[KEY_BASE_PERSON_NAME] = base.personName
            handle[KEY_BASE_PHONE] = base.phoneNumber
            handle[KEY_BASE_PRIORITY] = base.priority
        }
    }

    private fun clearRestorableState() {
        val handle = savedStateHandle
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
            loadingCategory = LoadingCategory.Structural,
            contextBuilder = { tableName = TABLE_NAME; affectedId = id },
            flowProvider = { 
                emergencyContactRepository.getContactsByPersonId(id).catch { e ->
                    updateUiState { it.copy(screenState = EmergencyContactScreenState.Error(e)) }
                    throw e
                }
            }
        ) { contacts ->
            updateUiState { it.copy(contacts = contacts.toImmutableList()) }
        }
    }

    fun startAdd() {
        val initial = EmergencyContactLogic.createInitialEntity(currentState.personId)
        updateSession {
            it.copy(
                editingContact = initial,
                initialContact = initial,
                isEditing = true
            )
        }
    }

    fun startEdit(contact: EmergencyContact) {
        updateSession {
            it.copy(
                editingContact = contact,
                initialContact = contact,
                isEditing = true
            )
        }
    }

    fun updateEditingContact(reducer: (EmergencyContact) -> EmergencyContact) {
        updateSession { current ->
            val nextEditing = current.editingContact?.let { reducer(it) }
            val nextTouched = if (nextEditing != null) {
                    getNewlyTouchedFields(current.editingContact, nextEditing, current.touchedFields)
            } else current.touchedFields

            current.copy(
                editingContact = nextEditing,
                touchedFields = nextTouched
            )
        }
    }

    private fun getNewlyTouchedFields(old: EmergencyContact?, next: EmergencyContact, current: Set<String>): Set<String> {
        val touched = current.toMutableSet()
        if (old == null) return touched
        if (old.facilityName != next.facilityName) touched.add("facilityName")
        if (old.personName != next.personName) touched.add("personName")
        if (old.phoneNumber != next.phoneNumber) touched.add("phoneNumber")
        if (old.contactType != next.contactType) touched.add("contactType")
        if (old.priority != next.priority) touched.add("priority")
        return touched
    }

    fun markFieldAsTouched(fieldName: String) {
        updateSession { state ->
            val nextTouched = state.touchedFields + fieldName
            state.copy(touchedFields = nextTouched)
        }
    }

    private fun calculateFieldErrors(contact: EmergencyContact?, touched: Set<String>): Map<String, Int?> {
        if (contact == null) return emptyMap()
        val errors = mutableMapOf<String, Int?>()
        
        val result = EmergencyContactLogic.validate(contact)
        if (result != EmergencyContactValidationResult.SUCCESS) {
            val resId = translateValidationResult(result)
            val field = when (result) {
                EmergencyContactValidationResult.EMPTY_FACILITY_NAME,
                EmergencyContactValidationResult.FACILITY_NAME_TOO_LONG -> "facilityName"
                EmergencyContactValidationResult.PERSON_NAME_TOO_LONG -> "personName"
                EmergencyContactValidationResult.PHONE_NUMBER_TOO_LONG -> "phoneNumber"
                else -> null
            }
            if (field != null && touched.contains(field)) {
                errors[field] = resId
            }
        }
        return errors
    }

    private fun translateValidationResult(result: EmergencyContactValidationResult): Int? {
        return when (result) {
            EmergencyContactValidationResult.EMPTY_FACILITY_NAME -> R.string.medical_contact_err_empty_facility
            EmergencyContactValidationResult.FACILITY_NAME_TOO_LONG -> R.string.medical_contact_err_facility_too_long
            EmergencyContactValidationResult.PERSON_NAME_TOO_LONG -> R.string.medical_contact_err_person_name_too_long
            EmergencyContactValidationResult.PHONE_NUMBER_TOO_LONG -> R.string.medical_contact_err_phone_too_long
            else -> null
        }
    }

    fun dismissEdit() {
        updateUiState { current ->
            val next = current.copy(session = EmergencyContactSession())
            clearRestorableState()
            next
        }
    }

    private fun updateSession(reducer: (EmergencyContactSession) -> EmergencyContactSession) {
        updateUiState { current ->
            val nextSession = reducer(current.session)
            val finalSession = nextSession.copy(
                isChanged = EmergencyContactLogic.isChanged(nextSession.editingContact, nextSession.initialContact),
                isValid = EmergencyContactLogic.isValid(nextSession.editingContact),
                fieldErrors = calculateFieldErrors(nextSession.editingContact, nextSession.touchedFields)
            )
            val next = current.copy(session = finalSession)
            backupRestorableState(next)
            next
        }
    }

    fun saveContact() {
        if (saveJob?.isActive == true) return
        val contact = currentState.session.editingContact ?: return

        updateUiState { it.copy(operation = EmergencyContactOperation.Saving) }

        saveJob = safeLaunch(
            operation = OP_SAVE,
            loadingCategory = LoadingCategory.Operation,
            contextBuilder = {
                tableName = TABLE_NAME
                affectedId = contact.id
            }
        ) {
            val isUpdate = !IdLogic.isNew(contact.id)
            val normalizedContact = EmergencyContactLogic.createSaveEntity(contact)
            val contactToSave = if (isUpdate) normalizedContact else normalizedContact.copy(id = java.util.UUID.randomUUID().toString())

            emergencyContactRepository.saveContact(contactToSave, isUpdate, featureName, OP_SAVE)
            sendViewEvent(EmergencyContactViewEvent.SaveSuccess)
            dismissEdit()
            clearRestorableState()
        }
    }

    fun deleteContact(contact: EmergencyContact) {
        if (deleteJob?.isActive == true) return
        updateUiState { it.copy(operation = EmergencyContactOperation.Deleting) }

        deleteJob = safeLaunch(
            operation = OP_DELETE,
            loadingCategory = LoadingCategory.Operation,
            contextBuilder = {
                tableName = TABLE_NAME
                affectedId = contact.id
            }
        ) {
            emergencyContactRepository.deleteContact(contact, featureName, OP_DELETE)
            sendViewEvent(EmergencyContactViewEvent.DeleteSuccess)
        }
    }

    override fun copyWithLoadingState(state: EmergencyContactUiState, isLoading: Boolean, category: LoadingCategory): EmergencyContactUiState {
        return when (category) {
            is LoadingCategory.Structural -> {
                val nextScreenState = if (!isLoading) {
                    (state.screenState as? EmergencyContactScreenState.Error) ?: EmergencyContactScreenState.Active
                } else {
                    (state.screenState as? EmergencyContactScreenState.Active) ?: EmergencyContactScreenState.Loading
                }
                state.copy(screenState = nextScreenState)
            }
            is LoadingCategory.Operation -> {
                if (!isLoading) state.copy(operation = EmergencyContactOperation.Idle) else state
            }
            else -> state.copy(isLoading = isLoading)
        }
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
