package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val TAG = "PersonDetailViewModel"

    private val _currentCategory = MutableStateFlow<Category?>(null)
    val currentCategory: StateFlow<Category?> = _currentCategory.asStateFlow()

    /**
     * サマリー情報の取得（フレームワーク部分のデータロード完了の基準とする）
     */
    override val personCategorySummary: StateFlow<PersonCategorySummary?> = _currentPerson
        .flatMapLatest { person ->
            if (person == null) {
                // 利用者が指定されていない場合は、ロード完了とみなして null を流すが、
                // loadPerson 中（isLoading=true）は勝手に解除しない。
                flowOf(null)
            } else {
                summaryRepository.getPersonCategorySummaryById(person.id)
                    .onEach { _isLoading.value = false }
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            _isLoading.value = false
            Log.e(TAG, "Frame summary load error", e)
            auditLogRepository.log(
                screenName = "PersonDetail",
                operation = "personCategorySummaryFlow",
                tableName = "summary_view",
                actionType = "ERROR",
                affectedId = _currentPerson.value?.id?.toString() ?: "0",
                details = e.toString()
            )
            showError(R.string.common_error_title_error, R.string.common_error_unknown, e.localizedMessage ?: "")
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
     */
    override fun loadPerson(personId: Int) {
        if (_currentPerson.value?.id == personId) return

        _isLoading.value = true
        _currentPerson.value = null

        loadPersonJob?.cancel()
        loadPersonJob = viewModelScope.launch {
            try {
                repository.getPersonById(personId).collectLatest {
                    _currentPerson.value = it
                    if (it == null) _isLoading.value = false // 利用者が見つからない場合も解除
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _isLoading.value = false
                Log.e(TAG, "loadPerson error", e)
                auditLogRepository.log(
                    screenName = "PersonDetail",
                    operation = "loadPerson",
                    tableName = "person_db",
                    actionType = "ERROR",
                    affectedId = personId.toString(),
                    details = e.toString()
                )
                showError(R.string.common_error_title_error, R.string.common_error_unknown, e.localizedMessage ?: "")
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
