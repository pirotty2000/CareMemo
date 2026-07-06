package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 利用者の復帰（論理削除解除）および完全抹消（物理削除）を担当する ViewModel
 */
class DeleteOrRestorePersonViewModel(
    private val repository: DeleteOrRestorePersonRepository,
    userSettingsRepository: UserSettingsRepository
) : BaseViewModel(userSettingsRepository) {

    /**
     * 操作モードの定義
     */
    enum class OperationMode {
        RESTORE, // 復帰モード
        DELETE   // 完全抹消モード
    }

    private val _mode = MutableStateFlow(OperationMode.RESTORE)

    /**
     * モードを設定します。
     */
    fun setMode(newMode: OperationMode) {
        _mode.value = newMode
        // モード変更時に選択状態をクリア
        _selectedIds.value = emptySet()
    }

    /**
     * アーカイブ済み（論理削除された）利用者のリスト
     */
    val archivedPersonList: StateFlow<List<Person>> = repository.getArchivedPersons()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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
        viewModelScope.launch {
            try {
                val targets = persons.filter { _selectedIds.value.contains(it.id) }
                targets.forEach { repository.restorePerson(it.id) }
                
                showSnackbar(R.string.archive_msg_restored, targets.size)
                clearSelection()
            } catch (e: Exception) {
                showError(R.string.common_error_title_error, R.string.archive_err_restore_failure, e.localizedMessage ?: "")
            }
        }
    }

    /**
     * 選択された利用者を完全に抹消（物理削除）します。
     */
    fun deleteSelectedPersons(persons: List<Person>) {
        viewModelScope.launch {
            try {
                val targets = persons.filter { _selectedIds.value.contains(it.id) }
                targets.forEach { repository.permanentlyDeletePerson(it.id) }
                
                showSnackbar(R.string.archive_msg_deleted, targets.size)
                clearSelection()
            } catch (e: Exception) {
                showError(R.string.common_error_title_delete, R.string.archive_err_delete_failure, e.localizedMessage ?: "")
            }
        }
    }

    class Factory(
        private val repository: DeleteOrRestorePersonRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DeleteOrRestorePersonViewModel::class.java)) {
                return DeleteOrRestorePersonViewModel(repository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
