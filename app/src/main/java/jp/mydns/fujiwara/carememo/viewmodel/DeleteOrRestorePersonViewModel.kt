package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 利用者の復帰（論理削除解除）および完全抹消（物理削除）を担当する ViewModel
 */
class DeleteOrRestorePersonViewModel(
    private val repository: DeleteOrRestorePersonRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : BaseViewModel(userSettingsRepository) {

    companion object {
        private const val FEATURE_NAME = "DeleteOrRestorePerson"
        private const val OP_RESTORE = "restoreSelectedPersons"
        private const val OP_DELETE = "deleteSelectedPersons"
        private const val TABLE_PERSON = "person_db"
    }

    override val featureName: String = FEATURE_NAME

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * 操作モードの定義
     */
    enum class OperationMode {
        RESTORE, // 復帰モード
        DELETE   // 完全抹消モード
    }

    private val _mode = MutableStateFlow(OperationMode.RESTORE)

    private val _archivedPersonList = MutableStateFlow<List<Person>>(emptyList())

    /**
     * アーカイブ済み（論理削除された）利用者のリスト
     */
    val archivedPersonList: StateFlow<List<Person>> = _archivedPersonList.asStateFlow()

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        safeCollect(
            operation = "archivedPersonListFlow",
            loadingState = _isLoading,
            contextBuilder = { tableName = TABLE_PERSON },
            flowProvider = { repository.getArchivedPersons() }
        ) {
            _archivedPersonList.value = it
        }
    }

    /**
     * モードを設定します。
     */
    fun setMode(newMode: OperationMode) {
        _mode.value = newMode
        // モード変更時に選択状態をクリア
        _selectedIds.value = emptySet()
    }

    // 選択された利用者のIDセット（完全抹消モード用）
    private val _selectedIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedIds: StateFlow<Set<Int>> = _selectedIds.asStateFlow()

    /**
     * 利用者の選択状態を切り替えます。
     */
    fun toggleSelection(personId: Int) {
        val current = _selectedIds.value
        _selectedIds.value = if (current.contains(personId)) {
            current - personId
        } else {
            current + personId
        }
    }

    /**
     * 全選択（RESTOREモード時のみ利用可能に制限することを想定）
     */
    fun selectAll(persons: List<Person>) {
        _selectedIds.value = persons.map { it.id }.toSet()
    }

    /**
     * 選択解除
     */
    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /**
     * 選択された利用者を一覧（アクティブ）に復元します。
     */
    fun restoreSelectedPersons(persons: List<Person>) {
        safeLaunch(
            operation = OP_RESTORE,
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_PERSON
            }
        ) {
            val targets = persons.filter { _selectedIds.value.contains(it.id) }
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
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_PERSON
            }
        ) {
            val targets = persons.filter { _selectedIds.value.contains(it.id) }
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
            if (modelClass.isAssignableFrom(DeleteOrRestorePersonViewModel::class.java)) {
                return DeleteOrRestorePersonViewModel(repository, userSettingsRepository, auditLogRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
