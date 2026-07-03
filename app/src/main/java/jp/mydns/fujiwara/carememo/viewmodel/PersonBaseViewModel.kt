package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 利用者情報を扱う ViewModel の共通基底クラス。
 * 詳細画面や服薬画面など、特定の利用者をコンテキストに持つ画面で使用します。
 */
abstract class PersonBaseViewModel(
    protected val repository: PersonRepository,
    protected val summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository
) : BaseViewModel(userSettingsRepository) {

    protected val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    protected val _currentPerson = MutableStateFlow<Person?>(null)
    val currentPerson: StateFlow<Person?> = _currentPerson.asStateFlow()

    private var loadPersonJob: Job? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val personCategorySummary: StateFlow<PersonCategorySummary?> = _currentPerson
        .flatMapLatest { person ->
            if (person != null) summaryRepository.getPersonCategorySummaryById(person.id)
            else flowOf(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * 利用者情報をロードします。
     */
    open fun loadPerson(personId: Int) {
        // すでに同じ利用者がロードされている場合は、不必要に _isLoading を true にしない。
        // これにより、画面回転等による再コンポーズ時に「読み込み中」で固まるのを防ぐ。
        if (_currentPerson.value?.id == personId) return

        _isLoading.value = true
        _currentPerson.value = null // 新しい利用者をロードする前に、現在の情報をクリアして「忘れる」

        loadPersonJob?.cancel() // 既存のロード処理がある場合はキャンセルし、重複を防ぐ
        loadPersonJob = viewModelScope.launch {
            repository.getPersonById(personId).collectLatest {
                _currentPerson.value = it
                // loadPerson 自体は基本情報のロード完了のみを扱う。
                // データのロード中フラグの解除は、各サブクラスのデータ取得 Flow (flatMapLatest) 側で行う。
            }
        }
    }
}
