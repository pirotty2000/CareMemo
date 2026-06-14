package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import jp.mydns.fujiwara.carememo.data.Person
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class PersonListViewModel : ViewModel() {
    // 実際の実装は後ほど行いますが、画面表示のために空のリストを保持します
    val userList: Flow<List<Person>> = MutableStateFlow(emptyList())
}
