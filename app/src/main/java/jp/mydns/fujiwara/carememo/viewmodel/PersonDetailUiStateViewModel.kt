package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailViewEvent
import kotlinx.coroutines.launch

/**
 * ViewModel：PersonDetailUiStateViewModel
 */
class PersonDetailUiStateViewModel(
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository,
    savedStateHandle: SavedStateHandle
) : PersonBaseUiStateViewModel<PersonDetailUiState, PersonDetailViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
    auditLogRepository,
    PersonDetailUiState(),
    savedStateHandle
) {
    override val featureName: String = "PersonDetail"

    init {
        // 引数（categoryName）の変更を購読
        scope.launch {
            savedStateHandle.getStateFlow<String?>(KEY_CATEGORY_NAME, null).collect { name ->
                if (name != null) {
                    try {
                        setCategory(Category.valueOf(name))
                    } catch (e: Exception) {
                        // 無視
                    }
                }
            }
        }
        
        // 最後に監視を開始 (featureName が初期化された後)
        startObservePersonId()
    }

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

    fun setCategory(category: Category) {
        if (currentState.currentCategory != category) {
            updateUiState { it.copy(currentCategory = category) }
        }
    }

    fun navigateToCategory(category: Category) {
        sendViewEvent(PersonDetailViewEvent.NavigateToCategory(category))
    }

    fun navigateBackToMain() {
        sendViewEvent(PersonDetailViewEvent.NavigateBackToMain)
    }

    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return PersonDetailUiStateViewModel(
                personRepository,
                summaryRepository,
                userSettingsRepository,
                auditLogRepository,
                savedStateHandle
            ) as T
        }
    }
}
