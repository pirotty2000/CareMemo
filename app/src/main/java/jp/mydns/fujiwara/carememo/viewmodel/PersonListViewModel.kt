package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.CareMemoRepository
import jp.mydns.fujiwara.carememo.data.InitialDataLoader
import jp.mydns.fujiwara.carememo.data.Person
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context

class PersonListViewModel(private val repository: CareMemoRepository) : ViewModel() {
    
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
            repository.insertPerson(person)
        }
    }

    // 更新
    fun updatePerson(person: Person) {
        viewModelScope.launch {
            repository.updatePerson(person)
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
