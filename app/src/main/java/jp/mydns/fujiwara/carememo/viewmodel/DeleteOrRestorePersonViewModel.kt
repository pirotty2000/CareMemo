package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonLogic
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonUiState
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonViewEvent
import kotlinx.coroutines.launch

/**
 * 利用者の復帰（論理削除解除）および完全抹消（物理削除）を担当する ViewModel (System B)
 */
class DeleteOrRestorePersonViewModel(
    private val repository: DeleteOrRestorePersonRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : BaseUiStateViewModel<DeleteOrRestorePersonUiState, DeleteOrRestorePersonViewEvent>(
    userSettingsRepository,
    DeleteOrRestorePersonUiState()
) {

    companion object {
        private const val FEATURE_NAME = "DeleteOrRestorePerson"
        private const val OP_RESTORE = "restoreSelectedPersons"
        private const val OP_DELETE = "deleteSelectedPersons"
        private const val TABLE_PERSON = "person_db"
    }

    override val featureName: String = FEATURE_NAME

    /**
     * 操作モードの定義
     */
    enum class OperationMode {
        RESTORE, // 復帰モード
        DELETE   // 完全抹消モード
    }

    init {
        // (B)系統標準のエラーハンドラをセットアップ
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // 共通設定の同期
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }

        // アーカイブ済み利用者の購読
        safeCollect(
            operation = "archivedPersonListFlow",
            mode = CollectMode.INITIAL,
            loadingState = loadingStateProxy,
            contextBuilder = { tableName = TABLE_PERSON },
            flowProvider = { repository.getArchivedPersons() }
        ) { newList ->
            updateUiState { it.copy(archivedPersons = newList) }
        }
    }

    override fun copyWithLoadingState(state: DeleteOrRestorePersonUiState, isLoading: Boolean): DeleteOrRestorePersonUiState {
        return state.copy(isLoading = isLoading)
    }

    /**
     * モードを設定します。
     */
    fun setMode(newMode: OperationMode) {
        updateUiState { 
            it.copy(
                mode = newMode,
                selectedIds = emptySet() // モード変更時に選択状態をクリア
            )
        }
    }

    /**
     * 利用者の選択状態を切り替えます。
     */
    fun toggleSelection(personId: String) {
        updateUiState { current ->
            val nextIds = DeleteOrRestorePersonLogic.toggleSelection(current.selectedIds, personId)
            current.copy(selectedIds = nextIds)
        }
    }

    /**
     * 全選択（RESTOREモード時のみ利用可能に制限することを想定）
     */
    fun selectAll(persons: List<Person>) {
        updateUiState { current -> 
            current.copy(selectedIds = DeleteOrRestorePersonLogic.selectAll(persons))
        }
    }

    /**
     * 選択解除
     */
    fun clearSelection() {
        updateUiState { it.copy(selectedIds = emptySet()) }
    }

    /**
     * 選択された利用者を一覧（アクティブ）に復元します。
     */
    fun restoreSelectedPersons(persons: List<Person>) {
        safeLaunch(
            operation = OP_RESTORE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_PERSON
            }
        ) {
            // バリデーション
            val validationResult = DeleteOrRestorePersonLogic.validate(currentState.selectedIds)
            if (validationResult != DeleteOrRestorePersonLogic.DeleteOrRestoreValidationResult.SUCCESS) {
                throw AppValidationException(
                    messageResId = R.string.archive_err_no_selection,
                    logMessage = "No persons selected for restore"
                )
            }

            val targets = DeleteOrRestorePersonLogic.filterTargets(persons, currentState.selectedIds)
            targets.forEach { 
                repository.restorePerson(it.id, featureName, OP_RESTORE) 
            }
            
            showSnackbar(R.string.archive_msg_restored, targets.size)
            clearSelection()
        }
    }

    /**
     * 選択された利用者を完全に抹消（物理削除）します。
     */
    fun deleteSelectedPersons(persons: List<Person>) {
        safeLaunch(
            operation = OP_DELETE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_PERSON
            }
        ) {
            // バリデーション
            val validationResult = DeleteOrRestorePersonLogic.validate(currentState.selectedIds)
            if (validationResult != DeleteOrRestorePersonLogic.DeleteOrRestoreValidationResult.SUCCESS) {
                throw AppValidationException(
                    messageResId = R.string.archive_err_no_selection,
                    logMessage = "No persons selected for delete"
                )
            }

            val targets = DeleteOrRestorePersonLogic.filterTargets(persons, currentState.selectedIds)
            targets.forEach { 
                repository.permanentlyDeletePerson(it.id, featureName, OP_DELETE)
            }
            
            showSnackbar(R.string.archive_msg_deleted, targets.size)
            clearSelection()
        }
    }

    class Factory(
        private val repository: DeleteOrRestorePersonRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeleteOrRestorePersonViewModel(repository, userSettingsRepository, auditLogRepository) as T
        }
    }
}
