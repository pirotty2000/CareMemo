package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Person

/**
 * 利用修了者（アーカイブ）の管理画面用 UI 状態。
 *
 * @param persons 全アーカイブ対象者のリスト
 * @param filteredPersons 検索等で絞り込まれたリスト
 * @param selectedPersonIds 現在選択されている利用者のIDセット
 * @param searchQuery 検索キーワード
 * @param isLoading 読み込み中フラグ
 * @param isProcessing 削除や復旧の実行中フラグ
 * @param isNameMaskingEnabled 氏名を伏せ字にするかどうか
 */
data class DeleteOrRestorePersonUiState(
    val persons: List<Person> = emptyList(),
    val filteredPersons: List<Person> = emptyList(),
    val selectedPersonIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val isNameMaskingEnabled: Boolean = true
)

/**
 * アーカイブ管理画面固有のナビゲーションイベント。
 */
sealed interface DeleteOrRestorePersonViewEvent {
    /** 処理完了後の画面終了を要求する */
    data object Finish : DeleteOrRestorePersonViewEvent
}

/**
 * Logic：DeleteOrRestorePersonLogic
 *
 * 【役割】
 * 利用終了者（アーカイブ）の復帰、または完全抹消を行う画面における選択管理とフィルタリングのロジックを提供します。
 *
 * 【主な機能】
 * ・チェックボックスによる複数選択状態の管理。
 * ・氏名およびフリガナに基づくリアルタイム検索。
 * ・選択人数に応じた要約情報の生成。
 *
 * 【設計指針】
 * 1. 大人数の利用者を扱う可能性があるため、選択状態は Set で高速に管理する。
 * 2. 検索はユーザーの利便性を考慮し、漢字氏名と読み（ふりがな）の両方を対象とする。
 * 3. 抹消や復元といった重大な操作を行うため、選択人数を常に明示的に算出する。
 */
object DeleteOrRestorePersonLogic {

    /**
     * 指定された利用者IDの選択状態を反転（トグル）させ、新しいIDセットを返します。
     *
     * @param currentIds 現在の選択済みIDセット
     * @param personId 対象の利用者ID
     * @return 更新後のIDセット
     */
    fun toggleSelection(currentIds: Set<String>, personId: String): Set<String> {
        return if (currentIds.contains(personId)) {
            currentIds - personId
        } else {
            currentIds + personId
        }
    }

    /**
     * アーカイブ対象者のリストを検索クエリでフィルタリングします。
     * 氏名（姓・名）またはふりがな（せい・めい）のいずれかにクエリが含まれる人物を抽出します。
     *
     * @param persons 元のリスト
     * @param query 検索キーワード
     * @return フィルタリング後のリスト
     */
    fun filterRecords(persons: List<Person>, query: String): List<Person> {
        if (query.isBlank()) return persons
        return persons.filter { person ->
            val nameMatch = "${person.lastName}${person.firstName}".contains(query, ignoreCase = true)
            val furiganaMatch = "${person.lastNameFurigana}${person.firstNameFurigana}".contains(query, ignoreCase = true)
            nameMatch || furiganaMatch
        }
    }

    /**
     * 現在の選択状況に基づいたサマリー情報を計算します。
     * ボタンの活性化やメッセージ表示に使用します。
     *
     * @param selectedIds 選択中のIDセット
     * @return 選択人数
     */
    fun calculateSummary(selectedIds: Set<String>): Int {
        return selectedIds.size
    }
}
