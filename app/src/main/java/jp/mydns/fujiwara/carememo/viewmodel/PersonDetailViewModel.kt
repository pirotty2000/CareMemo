package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * 利用者詳細画面の共通フレームワーク（カテゴリ切り替え、共通状態管理）を担当する ViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailViewModel(
    repository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository,
) : PersonBaseViewModel(repository, summaryRepository, userSettingsRepository, auditLogRepository) {

    companion object {
        private const val FEATURE_BASE_NAME = "PersonDetail"
        private const val TABLE_PERSON = "person_db"
        private const val TABLE_SUMMARY = "summary_view"
    }

    override val featureName: String 
        get() = "$FEATURE_BASE_NAME/${currentCategory.value?.name ?: "Base"}"

    private val _currentCategory = MutableStateFlow<Category?>(null)
    val currentCategory: StateFlow<Category?> = _currentCategory.asStateFlow()

    /**
     * サマリー情報の取得（フレームワーク部分のデータロード完了の基準とする）
     */
    override val personCategorySummary: StateFlow<PersonCategorySummary?> = _currentPerson
        .flatMapLatest { person ->
            if (person == null) {
                flowOf(null)
            } else {
                summaryRepository.getPersonCategorySummaryById(person.id)
                    .onEach { _isLoading.value = false }
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            coroutineErrorHandler.handleException(e, ErrorContext(featureName, "personCategorySummaryFlow", TABLE_SUMMARY))
            _isLoading.value = false
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * 表示するカテゴリを設定します。
     */
    fun setCategory(category: Category) {
        _currentCategory.value = category
    }

    /**
     * 利用者情報をロードします。
     * 詳細画面ではサマリー取得まで完了を待ちたいため、基底クラスの実装をオーバーライドします。
     */
    override fun loadPerson(personId: Int) {
        if (_currentPerson.value?.id == personId) return

        _isLoading.value = true
        _currentPerson.value = null

        loadPersonJob?.cancel()
        loadPersonJob = safeLaunch(
            operation = "loadPerson",
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = personId.toString()
            }
        ) {
            try {
                repository.getPersonById(personId).collect {
                    _currentPerson.value = it
                    // 利用者が見つからない場合のみ、ここでロード完了とする
                    if (it == null) {
                        _isLoading.value = false
                    }
                }
            } catch (t: Throwable) {
                // 例外発生時は確実に解除する
                _isLoading.value = false
                throw t // safeLaunch に再スローしてハンドルさせる
            }
        }
    }

    class Factory(
        private val repository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonDetailViewModel::class.java)) {
                return PersonDetailViewModel(repository, summaryRepository, userSettingsRepository, auditLogRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
