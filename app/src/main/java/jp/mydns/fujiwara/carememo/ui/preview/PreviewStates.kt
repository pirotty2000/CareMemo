package jp.mydns.fujiwara.carememo.ui.preview

import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Component：PreviewStates
 *
 * 【役割】
 * 各画面の Stateless な Composable に対して、プレビュー時に渡すべき「状態（UiState）」のバリエーションを定義します。
 * 複数の引数を一つのデータクラスに集約することで、PreviewParameterProvider での供給を容易にします。
 *
 * 【主な機能】
 * ・各機能画面（健康、所見）に対応したプレビュー用データクラスの提供。
 * ・MockData から取得したサンプル値をデフォルト値として設定。
 * ・ローディング中や空状態などのバリエーション表現への対応。
 *
 * 【このコンポーネントでは行わないこと】
 * ・ViewModel の保持や操作。
 * ・ビジネスロジック（判定等）の実行。
 */

/**
 * 利用者健康記録画面（PersonHealthScreenContent）のプレビュー用状態
 *
 * @property category 表示対象の健康カテゴリ
 * @property records 履歴データのリスト（ImmutableList）
 * @property isLoading ローディング中かどうか
 * @property person 表示対象の利用者情報
 * @property summary 各カテゴリの記録有無サマリー
 * @property selectedRecordId 現在選択（強調）されているレコードのID
 * @property preferredShowHistory 履歴リストを表示するかどうか（Phone版等のトグル用）
 */
data class PersonHealthPreviewState(
    val category: Category = Category.BP_AND_PULSE,
    val records: ImmutableList<HistoryRecord> = persistentListOf(),
    val isLoading: Boolean = false,
    val person: Person? = MockData.person,
    val summary: PersonCategorySummary? = null,
    val selectedRecordId: String? = null,
    val preferredShowHistory: Boolean = true
)

/**
 * 利用者所見記録画面（PersonConditionScreenContent）のプレビュー用状態
 *
 * @property records 所見レコードのリスト（ImmutableList）
 * @property isLoading ローディング中かどうか
 * @property selectedRecordId 現在選択されているレコードのID
 * @property isExpanded タブレット版などの拡張レイアウトで表示するかどうか
 */
data class PersonConditionPreviewState(
    val records: ImmutableList<jp.mydns.fujiwara.carememo.data.ConditionAtVisit> = persistentListOf(),
    val isLoading: Boolean = false,
    val selectedRecordId: String? = null,
    val isExpanded: Boolean = false
)
