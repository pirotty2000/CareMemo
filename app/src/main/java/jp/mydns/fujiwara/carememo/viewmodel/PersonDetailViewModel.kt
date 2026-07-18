package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

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

    private val _personCategorySummary = MutableStateFlow<PersonCategorySummary?>(null)

    /**
     * サマリー情報の取得
     */
    override val personCategorySummary: StateFlow<PersonCategorySummary?> = _personCategorySummary.asStateFlow()

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

        // 状態を同期的にリセット（テストでのブランキング抑制およびアサーションのため）
        _isLoading.value = true
        _currentPerson.value = null
        _personCategorySummary.value = null

        loadPersonJob?.cancel()
        loadPersonJob = safeCollect(
            operation = "loadPersonWithSummary",
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = personId.toString()
            },
            flowProvider = {
                combine(
                    repository.getPersonById(personId),
                    summaryRepository.getPersonCategorySummaryById(personId)
                ) { person, summary -> person to summary }
            }
        ) { (person, summary) ->
            _currentPerson.value = person
            _personCategorySummary.value = summary
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
