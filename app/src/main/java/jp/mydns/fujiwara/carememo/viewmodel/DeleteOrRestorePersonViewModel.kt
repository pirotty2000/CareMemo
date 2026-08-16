package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonLogic
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonUiState
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonViewEvent
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * ViewModel：DeleteOrRestorePersonViewModel
 *
 * 【役割】
 * 利用者の復元（RESTORE）および物理抹消（DELETE）画面の状態管理と実行制御を担当します。
 * アーカイブされた利用者の一覧表示と、複数選択による一括操作機能を提供します。
 *
 * 【設計指針：UI 境界の責務】
 * UI に公開する利用者リスト (`archivedPersons`) および選択 ID セット (`selectedIds`) は、
 * UI 境界において ImmutableList / ImmutableSet へ変換し、不変性を保証します。
 *
 * 【この ViewModel では行わないこと】
 * ・一括選択や選択トグルの具体的な計算ロジック（DeleteOrRestorePersonLogic が担当）。
 */
class DeleteOrRestorePersonViewModel(
    private val repository: DeleteOrRestorePersonRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository,
    savedStateHandle: SavedStateHandle
) : BaseUiStateViewModel<DeleteOrRestorePersonUiState, DeleteOrRestorePersonViewEvent>(
    userSettingsRepository,
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

    /** データ更新処理（復帰・抹消）用の Job */
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

        // 初期化完了後に購読を開始
        scope.launch {
            startArchivedListObservation()
        }
    }

    private fun startArchivedListObservation() {
        safeCollect(
            operation = "archivedPersonListFlow",
            mode = CollectMode.INITIAL,
            loadingState = loadingStateProxy,
            contextBuilder = { tableName = TABLE_PERSON },
            flowProvider = { repository.getArchivedPersons() }
        ) { newList ->
            // UI 境界において ImmutableList へ変換し、不変性を保証する
            updateUiState { it.copy(archivedPersons = newList.toImmutableList()) }
        }
    }

    override fun copyWithLoadingState(state: DeleteOrRestorePersonUiState, isLoading: Boolean): DeleteOrRestorePersonUiState {
        return state.copy(isLoading = isLoading)
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
        // 二重実行防止
        if (actionJob?.isActive == true) return

        val selectedIds = currentState.selectedIds
        actionJob = safeLaunch(
            operation = OP_RESTORE,
            loadingState = loadingStateProxy,
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
        // 二重実行防止
        if (actionJob?.isActive == true) return

        val selectedIds = currentState.selectedIds
        actionJob = safeLaunch(
            operation = OP_DELETE,
            loadingState = loadingStateProxy,
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
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return DeleteOrRestorePersonViewModel(repository, userSettingsRepository, auditLogRepository, savedStateHandle) as T
        }
    }
}
