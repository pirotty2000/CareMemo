package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.CareMemoRepository
import jp.mydns.fujiwara.carememo.data.InitialDataLoader
import jp.mydns.fujiwara.carememo.data.Person
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import android.database.sqlite.SQLiteConstraintException

class PersonListViewModel(private val repository: CareMemoRepository) : ViewModel() {

    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow: StateFlow<String?> = _errorFlow.asStateFlow()

    fun clearError() {
        _errorFlow.value = null
    }
    
    // データベースから全利用者をリアルタイムで取得（StateFlowとして保持）
    val userList: StateFlow<List<Person>> = repository.getAllPersons()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 削除済みの利用者をリアルタイムで取得
    val deletedUserList: StateFlow<List<Person>> = repository.getDeletedPersons()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // デバッグ用：初期データのロード
    fun loadInitialData(context: Context) {
        viewModelScope.launch {
            InitialDataLoader(context, repository).loadInitialData()
        }
    }

    // 新規登録
    fun addPerson(person: Person) {
        viewModelScope.launch {
            try {
                repository.insertPerson(person)
            } catch (e: SQLiteConstraintException) {
                _errorFlow.value = "この利用者は既に登録されています。同姓同名・同生年月日の場合は「識別用メモ」を入力して区別してください。"
            }
        }
    }

    // 更新
    fun updatePerson(person: Person) {
        viewModelScope.launch {
            try {
                repository.updatePerson(person)
            } catch (e: SQLiteConstraintException) {
                _errorFlow.value = "変更後の内容は既に他の利用者として登録されています。「識別用メモ」などを調整してください。"
            }
        }
    }

    // 論理削除
    fun logicalDeletePerson(person: Person) {
        viewModelScope.launch {
            repository.logicalDeletePerson(person.id)
        }
    }

    // 復元
    fun restorePerson(person: Person) {
        viewModelScope.launch {
            repository.restorePerson(person.id)
        }
    }

    // 物理削除
    fun deletePerson(person: Person) {
        viewModelScope.launch {
            repository.deletePerson(person)
        }
    }

    // ViewModelFactory の定義
    class Factory(private val repository: CareMemoRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonListViewModel::class.java)) {
                return PersonListViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
