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
 * ViewModel：PersonDetailUiStateViewModel
 *
 * 【役割】
 * 利用者詳細画面の「共通フレームワーク」としての状態管理を担当します。
 * 画面上部の利用者基本情報表示や、下部のカテゴリ（経過記録、健康状態、基本情報など）切り替えの制御を提供します。
 *
 * 【主要な機能】
 * ・利用者基本情報およびカテゴリ別サマリー情報（未読数等）の保持と更新。
 * ・表示カテゴリの切り替え制御。
 * ・利用者情報のロード開始・完了に伴う UiState の原子的な更新。
 *
 * 【依存している Repository】
 * ・PersonRepository: 利用者基本情報の取得（基底クラス経由）。
 * ・PersonSummaryRepository: カテゴリ別のサマリー情報の取得（基底クラス経由）。
 * ・AuditLogRepository: 操作ログの記録（基底クラスの例外ハンドラで使用）。
 * ・UserSettingsRepository: 共通設定の参照。
 *
 * 【設計指針】
 * 1. フレームワーク化：詳細画面内の各サブ画面で共通して必要となる利用者情報を一元管理し、コードの重複を防ぐ。
 * 2. 単一ソース：`PersonBaseUiStateViewModel` の仕組みを利用し、データ取得ロジックを標準化されたフローに乗せる。
 * 3. 疎結合：各カテゴリの具体的な業務ロジックは別の ViewModel が担当し、本クラスは「どのカテゴリを表示するか」というコンテキスト管理に専念する。
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
        // ロードされた利用者基本情報とサマリー情報を State に反映する
        return state.copy(
            personId = person.id,
            person = person,
            personSummary = summary
        )
    }

    override fun onPrepareLoadPerson(state: PersonDetailUiState): PersonDetailUiState {
        // 利用者のロード開始前に、以前の利用者情報をクリアして表示の混線を防ぐ
        return state.copy(personId = null, person = null, personSummary = null)
    }

    /**
     * 表示するカテゴリを設定します。
     *
     * @param category 切り替え先のカテゴリ（経過記録、健康状態など）
     */
    fun setCategory(category: Category) {
        if (currentState.currentCategory != category) {
            updateUiState { it.copy(currentCategory = category) }
        }
    }

    /**
     * PersonDetailUiStateViewModel を生成するための Factory クラス。
     */
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
