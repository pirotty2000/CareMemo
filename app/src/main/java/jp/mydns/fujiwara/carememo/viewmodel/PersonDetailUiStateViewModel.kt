package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailViewEvent
import kotlinx.coroutines.launch

/**
 * ViewModel：PersonDetailUiStateViewModel
 *
 * 【役割】
 * 各利用者詳細画面（健康記録、所見メモ、服薬管理）の「共通フレームワーク」としての状態管理と制御を担当します。
 *
 * 【設計指針：レイヤー責務】
 * 1. 共通情報の統合：画面上部のヘッダー情報（利用者名、年齢）およびカテゴリ選択バーの記録状況サマリーを一元管理し、
 *    複数の専門 ViewModel を跨ぐ共通の利用者コンテキストを提供します。
 * 2. ナビゲーションの統括：カテゴリ間の遷移や、詳細画面からメイン一覧への戻り遷移など、
 *    画面全体のナビゲーションロジックを制御します。
 *
 * 【この ViewModel では行わないこと】
 * ・個別の健康カテゴリ（バイタル、服薬等）のデータ保存や入力管理（各専門 ViewModel が担当）。
 */
class PersonDetailUiStateViewModel(
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    securitySession: SecuritySession,
    auditLogRepository: AuditLogRepository,
    savedStateHandle: SavedStateHandle
) : PersonBaseUiStateViewModel<PersonDetailUiState, PersonDetailViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
    securitySession,
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
                    } catch (_: Exception) {
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
        private val securitySession: SecuritySession,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return PersonDetailUiStateViewModel(
                personRepository,
                summaryRepository,
                userSettingsRepository,
                securitySession,
                auditLogRepository,
                savedStateHandle
            ) as T
        }
    }
}
