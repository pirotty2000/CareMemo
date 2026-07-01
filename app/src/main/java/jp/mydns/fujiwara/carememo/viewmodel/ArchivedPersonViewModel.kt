package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.ArchivedPersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 利用終了者（アーカイブ済み利用者）の管理を担当する ViewModel
 */
class ArchivedPersonViewModel(
    private val repository: ArchivedPersonRepository,
    userSettingsRepository: UserSettingsRepository
) : BaseViewModel(userSettingsRepository) {

    /**
     * 利用終了者（論理削除された利用者）のリスト
     */
    val archivedPersonList: StateFlow<List<Person>> = repository.getDeletedPersons()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * 利用者を一覧（アクティブ）に復元します。
     */
    fun restorePerson(person: Person) {
        viewModelScope.launch {
            try {
                repository.restorePerson(person.id)
                showSnackbar("${person.getMaskedName(isNameMaskingEnabled.value)} さんを一覧に戻しました")
            } catch (e: Exception) {
                showError("復元エラー", "利用者の復元に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    /**
     * 利用終了者全員のデータを物理削除します（完全に抹消）。
     */
    fun deleteAllArchivedPersons() {
        viewModelScope.launch {
            try {
                repository.deleteEndedPersons()
                showSnackbar("利用終了者のデータを完全に削除しました")
            } catch (e: Exception) {
                showError("削除エラー", "データの完全削除に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    class Factory(
        private val repository: ArchivedPersonRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArchivedPersonViewModel::class.java)) {
                return ArchivedPersonViewModel(repository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
