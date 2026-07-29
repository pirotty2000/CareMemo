package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailViewEvent

/**
 * (B)系統：利用者詳細画面の共通フレームワークを担当する ViewModel。
 * 
 * カテゴリ切り替え、共通サマリー情報の管理を提供します。
 */
class PersonDetailUiStateViewModel(
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : PersonBaseUiStateViewModel<PersonDetailUiState, PersonDetailViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
    auditLogRepository,
    PersonDetailUiState()
) {
    override val featureName: String = "PersonDetail"

    override fun copyWithLoadingState(state: PersonDetailUiState, isLoading: Boolean): PersonDetailUiState {
        return state.copy(isLoading = isLoading)
    }

    override fun updateWithPersonData(
        state: PersonDetailUiState,
        person: Person,
        summary: PersonCategorySummary?
    ): PersonDetailUiState {
        return state.copy(
            personId = person.id,
            person = person,
            personSummary = summary
        )
    }

    override fun onPrepareLoadPerson(state: PersonDetailUiState): PersonDetailUiState {
        return state.copy(personId = null, person = null, personSummary = null)
    }

    /**
     * 表示するカテゴリを設定します。
     */
    fun setCategory(category: Category) {
        if (currentState.currentCategory != category) {
            updateUiState { it.copy(currentCategory = category) }
        }
    }

    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PersonDetailUiStateViewModel(
                personRepository,
                summaryRepository,
                userSettingsRepository,
                auditLogRepository
            ) as T
        }
    }
}
