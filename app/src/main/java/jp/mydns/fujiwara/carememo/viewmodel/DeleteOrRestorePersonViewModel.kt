package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonLogic
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonOperation
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonScreenState
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonUiState
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonViewEvent
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * ViewModel：DeleteOrRestorePersonViewModel
 *
 * 【役割】
 * 利用者の復元（RESTORE）および物理抹消（DELETE）画面の状態管理と実行制御を担当します。
 */
class DeleteOrRestorePersonViewModel(
    private val repository: DeleteOrRestorePersonRepository,
    userSettingsRepository: UserSettingsRepository,
    securitySession: SecuritySession,
    auditLogRepository: AuditLogRepository,
    savedStateHandle: SavedStateHandle
) : BaseUiStateViewModel<DeleteOrRestorePersonUiState, DeleteOrRestorePersonViewEvent>(
    userSettingsRepository,
    securitySession,
    DeleteOrRestorePersonUiState()
) {

    companion object {
        private const val FEATURE_NAME = "DeleteOrRestorePerson"
        private const val OP_RESTORE = "restoreSelectedPersons"
        private const val OP_DELETE = "deleteSelectedPersons"
        private const val TABLE_PERSON = "person_db"
        private const val KEY_MODE = "mode"
    }

    override val featureName: String = FEATURE_NAME

    private var actionJob: Job? = null

    enum class OperationMode {
        RESTORE,
        DELETE
    }

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        savedStateHandle.get<String>(KEY_MODE)?.let { modeName ->
            try {
                val mode = OperationMode.valueOf(modeName)
                updateUiState { it.copy(mode = mode) }
            } catch (_: Exception) {
            }
        }

        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }

        scope.launch {
            startArchivedListObservation()
        }
    }

    private fun startArchivedListObservation() {
        safeCollect(
            operation = "archivedPersonListFlow",
            mode = CollectMode.INITIAL,
            loadingCategory = LoadingCategory.Structural,
            contextBuilder = { tableName = TABLE_PERSON },
            flowProvider = { 
                repository.getArchivedPersons().catch { e ->
                    updateUiState { it.copy(screenState = DeleteOrRestorePersonScreenState.Error(e)) }
                    throw e
                }
            }
        ) { newList ->
            updateUiState { it.copy(archivedPersons = newList.toImmutableList()) }
        }
    }

    override fun copyWithLoadingState(state: DeleteOrRestorePersonUiState, isLoading: Boolean, category: LoadingCategory): DeleteOrRestorePersonUiState {
        return when (category) {
            is LoadingCategory.Structural -> {
                val nextScreenState = if (!isLoading) {
                    if (state.screenState is DeleteOrRestorePersonScreenState.Error) state.screenState else DeleteOrRestorePersonScreenState.Active
                } else {
                    if (state.screenState is DeleteOrRestorePersonScreenState.Active) state.screenState else DeleteOrRestorePersonScreenState.Loading
                }
                state.copy(screenState = nextScreenState)
            }
            is LoadingCategory.Operation -> {
                if (!isLoading) state.copy(operation = DeleteOrRestorePersonOperation.Idle) else state
            }
            else -> state.copy(isLoading = isLoading)
        }
    }

    fun setMode(newMode: OperationMode) {
        updateUiState { it.copy(mode = newMode, selectedIds = persistentSetOf()) }
    }

    fun toggleSelection(personId: String) {
        updateUiState { current ->
            val nextIds = DeleteOrRestorePersonLogic.toggleSelection(current.selectedIds, personId).toImmutableSet()
            current.copy(selectedIds = nextIds)
        }
    }

    fun selectAll(persons: List<Person>) {
        if (currentState.mode == OperationMode.DELETE) return
        updateUiState { current -> current.copy(selectedIds = DeleteOrRestorePersonLogic.selectAll(persons).toImmutableSet()) }
    }

    fun clearSelection() {
        updateUiState { it.copy(selectedIds = persistentSetOf()) }
    }

    fun restoreSelectedPersons(persons: List<Person>) {
        if (actionJob?.isActive == true) return

        val selectedIds = currentState.selectedIds
        updateUiState { it.copy(operation = DeleteOrRestorePersonOperation.Restoring) }

        actionJob = safeLaunch(
            operation = OP_RESTORE,
            loadingCategory = LoadingCategory.Operation,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = "Count:${selectedIds.size}"
            }
        ) {
            val validationResult = DeleteOrRestorePersonLogic.validate(currentState.selectedIds)
            if (validationResult != DeleteOrRestorePersonLogic.DeleteOrRestoreValidationResult.SUCCESS) {
                throw AppValidationException(messageResId = R.string.archive_err_no_selection, logMessage = "No selection")
            }
            val targets = DeleteOrRestorePersonLogic.filterTargets(persons, currentState.selectedIds)
            repository.restorePersonsBatch(targets.map { it.id }, featureName, OP_RESTORE)
            showSnackbar(R.string.archive_msg_restored, targets.size)
            clearSelection()
        }
    }

    fun deleteSelectedPersons(persons: List<Person>) {
        if (actionJob?.isActive == true) return

        val selectedIds = currentState.selectedIds
        updateUiState { it.copy(operation = DeleteOrRestorePersonOperation.Deleting) }

        actionJob = safeLaunch(
            operation = OP_DELETE,
            loadingCategory = LoadingCategory.Operation,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = "Count:${selectedIds.size}"
            }
        ) {
            val validationResult = DeleteOrRestorePersonLogic.validate(currentState.selectedIds)
            if (validationResult != DeleteOrRestorePersonLogic.DeleteOrRestoreValidationResult.SUCCESS) {
                throw AppValidationException(messageResId = R.string.archive_err_no_selection, logMessage = "No selection")
            }
            val targets = DeleteOrRestorePersonLogic.filterTargets(persons, currentState.selectedIds)
            repository.permanentlyDeletePersonsBatch(targets.map { it.id }, featureName, OP_DELETE)
            showSnackbar(R.string.archive_msg_deleted, targets.size)
            clearSelection()
        }
    }

    fun navigateBack() {
        sendViewEvent(DeleteOrRestorePersonViewEvent.NavigateBack)
    }

    class Factory(
        private val repository: DeleteOrRestorePersonRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val securitySession: SecuritySession,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return DeleteOrRestorePersonViewModel(
                repository,
                userSettingsRepository,
                securitySession,
                auditLogRepository,
                savedStateHandle
            ) as T
        }
    }
}
