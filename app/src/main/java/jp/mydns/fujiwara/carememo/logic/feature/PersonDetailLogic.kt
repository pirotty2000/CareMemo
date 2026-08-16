package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.runtime.Immutable
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.viewmodel.PersonAwareState

/**
 * UI State：PersonDetailUiState
 *
 * 【役割】
 * 各利用者詳細画面（健康記録、所見メモ、服薬管理）の「共通フレームワーク」としての表示状態を管理します。
 *
 * 【設計指針：レイヤー責務】
 * 1. 共通情報の保持：画面上部のヘッダー情報（氏名・年齢）や、カテゴリ選択バーの記録状況サマリーを一元保持し、
 *    各カテゴリ画面で共通の利用者コンテキストを提供します。
 * 2. 状態の不変化：UI 境界として、表示に必要な情報を不変な State として定義し、Compose の安定した描画に寄与します。
 */
@Immutable
data class PersonDetailUiState(
    override val personId: String? = null,
    val person: Person? = null,
    val personSummary: PersonCategorySummary? = null,
    override val currentCategory: Category = Category.HEIGHT_AND_WEIGHT,
    override val isLoading: Boolean = false
) : PersonAwareState

/**
 * View Event：PersonDetailViewEvent
 *
 * 【役割】
 * 利用者詳細画面（共通フレームワーク層）において発生する、一過性の通知やアクションを定義します。
 */
sealed interface PersonDetailViewEvent {
    /** カテゴリを切り替える */
    data class NavigateToCategory(val category: Category) : PersonDetailViewEvent
    /** 一覧画面へ戻る */
    object NavigateBackToMain : PersonDetailViewEvent
}

/*
 * 利用者詳細画面における共通的なナビゲーションや情報の集約に関するドメインロジックを提供します。
 * 現時点では UiState と Event の定義が中心であり、動的なロジックが必要になった際に復活させるため保持。
object PersonDetailLogic {
    // 必要に応じて、詳細画面共通の表示ロジック（例：特定の条件下でのカテゴリ制限など）をここに追加します
}
*/
