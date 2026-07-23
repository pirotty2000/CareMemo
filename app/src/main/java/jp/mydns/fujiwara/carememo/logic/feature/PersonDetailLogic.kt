package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.viewmodel.PersonAwareState

/**
 * 利用者詳細画面（共通フレームワーク）用の UI 状態
 */
data class PersonDetailUiState(
    val personId: Int? = null,
    val person: Person? = null,
    val personSummary: PersonCategorySummary? = null,
    override val currentCategory: Category = Category.HEIGHT_AND_WEIGHT,
    override val isLoading: Boolean = false
) : PersonAwareState

/**
 * 利用者詳細画面（共通フレームワーク）固有のイベント
 */
sealed interface PersonDetailViewEvent {
    // 必要に応じて定義
}
