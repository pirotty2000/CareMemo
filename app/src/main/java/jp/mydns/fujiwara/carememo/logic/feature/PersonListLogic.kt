package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary

/**
 * UI State：PersonListUiState
 *
 * 【役割】
 * 利用者一覧画面における、リストデータ、検索条件、選択状態、および各利用者の記録サマリーを保持します。
 *
 * @param persons 現在アクティブな全利用者のリスト
 * @param filteredPersons 検索・五十音・メモ検索により絞り込まれた表示対象のリスト
 * @param summaryMap 利用者IDをキーとした、各カテゴリの記録有無サマリーのマップ
 * @param searchQuery 名前検索用キーワード
 * @param selectedSection 現在選択されている五十音セクション（「全」または「あ」「か」等）
 * @param isLoading データの読み込み中フラグ
 * @param isNameMaskingEnabled 氏名のマスキング（伏せ字）が有効か
 */
data class PersonListUiState(
    val persons: List<Person> = emptyList(),
    val filteredPersons: List<Person> = emptyList(),
    val summaryMap: Map<String, PersonCategorySummary> = emptyMap(),
    val searchQuery: String = "",
    val selectedSection: String = "全",
    val isLoading: Boolean = false,
    val isNameMaskingEnabled: Boolean = true
)

/**
 * Logic：PersonListLogic
 *
 * 【役割】
 * 利用者一覧画面（MainScreen）における表示データの加工（絞り込み、並び替え、関連データの統合）に関するドメインロジックを提供します。
 *
 * 【主な機能】
 * ・氏名（漢字・ふりがな）および所見メモの内容に基づいたリアルタイム検索。
 * ・五十音行（あかさたな）によるカテゴリカルな絞り込み。
 * ・ふりがなに基づいた利用者の五十音順ソート。
 * ・利用者情報と記録サマリーの統合。
 *
 * 【設計指針】
 * 1. 検索キーワードと五十音セクションが両方指定されている場合、キーワード検索を優先し、
 *    キーワードでの一致が 0 件の場合のみセクションフィルタを適用する（ユーザーの期待する「直接検索」を優先）。
 * 2. 検索対象には、氏名だけでなく所見メモ（ConditionAtVisit）の内容も含まれることを考慮する（上位レイヤーでの結果を persons として受け取る）。
 * 3. 並び替えは、姓（lastNameFurigana）を第一キー、名（firstNameFurigana）を第二キーとして一貫性を保つ。
 */
object PersonListLogic {

    /**
     * 指定された条件に基づいて、利用者リストをフィルタリングおよび並び替えします。
     *
     * @param persons 元の利用者リスト
     * @param query 検索キーワード（空文字可）
     * @param section 五十音セクション（「全」なら全件）
     * @return 処理後の利用者リスト
     */
    fun filterPersons(persons: List<Person>, query: String, section: String): List<Person> {
        // 1. キーワード検索（氏名・ふりがな・所見メモ一致）
        val keywordMatched = if (query.isNotBlank()) {
            persons.filter { person ->
                person.lastName.contains(query, ignoreCase = true) ||
                person.firstName.contains(query, ignoreCase = true) ||
                person.lastNameFurigana.contains(query, ignoreCase = true) ||
                person.firstNameFurigana.contains(query, ignoreCase = true)
            }
        } else {
            persons
        }

        // 2. 五十音セクションでの絞り込み
        // ただし、キーワード検索で結果が得られている場合は、セクションの指定に関わらずその結果を優先して表示する
        val finalFiltered = if (query.isNotBlank() && keywordMatched.isNotEmpty()) {
            keywordMatched
        } else if (section != "全") {
            // セクション指定がある場合は、ふりがなの先頭1文字で判定
            keywordMatched.filter { person ->
                person.lastNameFurigana.startsWith(section)
            }
        } else {
            keywordMatched
        }

        // 3. ふりがな順（姓 -> 名）でソートして返す
        return finalFiltered.sortedWith(
            compareBy<Person> { it.lastNameFurigana }.thenBy { it.firstNameFurigana }
        )
    }

    /**
     * 利用者リストと記録サマリーを紐付けた表示用のデータを構築します。
     *
     * @param person 対象の利用者
     * @param summaryMap IDをキーとしたサマリーマップ
     * @return 該当するサマリー。存在しない場合は新規の空サマリーを返す。
     */
    fun getSummaryForPerson(person: Person, summaryMap: Map<String, PersonCategorySummary>): PersonCategorySummary {
        return summaryMap[person.id] ?: PersonCategorySummary()
    }
}
