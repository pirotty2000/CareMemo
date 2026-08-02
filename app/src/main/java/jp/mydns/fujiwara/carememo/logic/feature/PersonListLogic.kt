package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils

/**
 * UI State：PersonListUiState
 *
 * 【役割】
 * 利用者一覧画面（MainScreen）全体の表示状態を保持します。
 * 
 * @param isLoading 全体の読み込み中フラグ
 * @param selectedSection 現在選択されている五十音セクション
 * @param searchQuery 検索キーワード
 * @param userList 画面に表示される加工済みの利用者リスト
 * @param isNameMaskingEnabled 氏名のマスキング（伏せ字）が有効か
 * @param selectedPersonForQuickMenu クイックメニュー対象の利用者 (develop追加分)
 * @param isQuickActionMenuExpanded クイックメニュー表示フラグ (develop追加分)
 * @param emergencyContactsForSheet ボトムシート用連絡先リスト (develop追加分)
 * @param isEmergencyContactLoading 連絡先読み込み中フラグ (develop追加分)
 */
data class PersonListUiState(
    val isLoading: Boolean = true,
    val selectedSection: String = AppSpecifications.Search.SECTION_ALL,
    val searchQuery: String = "",
    val userList: List<PersonUiState> = emptyList(),
    val isNameMaskingEnabled: Boolean = true,
    // --- 緊急連絡先機能の実装に伴う追加フィールド ---
    val selectedPersonForQuickMenu: Person? = null,
    val isQuickActionMenuExpanded: Boolean = false,
    val emergencyContactsForSheet: List<EmergencyContact>? = null,
    val isEmergencyContactLoading: Boolean = false
)

/**
 * View Event：PersonListViewEvent
 */
sealed interface PersonListViewEvent {
    // 将来的な拡張用
}

/**
 * UI State：PersonUiState
 *
 * 【役割】
 * 利用者一覧の各行（1名分）の表示状態を保持する UI 専用モデルです。
 */
data class PersonUiState(
    val person: Person,
    val maskedName: String,
    val maskedFurigana: String,
    val age: Int,
    val formattedBirthday: String,
    val summary: PersonCategorySummary
)

/**
 * 利用者追加・更新時の重複判定結果（事実）。
 */
enum class PersonDuplicateResult {
    /** 重複なし */
    SUCCESS,
    /** アクティブな利用者に重複が存在 */
    DUPLICATE_ACTIVE,
    /** 利用終了（アーカイブ）の中に重複が存在 */
    DUPLICATE_ARCHIVED
}

/**
 * Logic：PersonListLogic
 *
 * 【役割】
 * 利用者一覧画面における表示データの加工（五十音判定、フィルタリング、UI用変換）および
 * 利用者情報の妥当性チェックに関するドメインロジックを提供します。
 */
object PersonListLogic {

    /**
     * ふりがなから、所属する五十音行（あ、か、さ...）を判定します。
     *
     * @param furigana 判定対象のふりがな（姓）
     * @return 行の頭文字（あ〜わ）、または「他」
     */
    fun getSection(furigana: String): String {
        val firstChar = furigana.firstOrNull() ?: return AppSpecifications.Search.SECTION_OTHER
        return when (firstChar) {
            in 'あ'..'お' -> "あ"
            in 'か'..'こ', in 'が'..'ご' -> "か"
            in 'さ'..'そ', in 'ざ'..'ぞ' -> "さ"
            in 'た'..'と', in 'だ'..'ど', in 'っ'..'っ' -> "た"
            in 'な'..'の' -> "な"
            in 'は'..'ほ', in 'ば'..'ぼ', in 'ぱ'..'ぽ' -> "は"
            in 'ま'..'も' -> "ま"
            in 'や'..'よ' -> "や"
            in 'ら'..'ろ' -> "ら"
            in 'わ'..'ん' -> "わ"
            else -> AppSpecifications.Search.SECTION_OTHER
        }
    }

    /**
     * 各種条件に基づき、利用者リストをフィルタリングします。
     *
     * @param allPersons 全利用者リスト
     * @param section 選択された五十音セクション
     * @param matchedIds キーワード検索に合致した利用者IDのリスト（null なら全件対象）
     * @return フィルタリング後の利用者リスト
     */
    fun filterPersons(
        allPersons: List<Person>,
        section: String,
        matchedIds: List<String>?
    ): List<Person> {
        var filtered = allPersons
        
        // 1. 五十音フィルタの適用
        if (section != AppSpecifications.Search.SECTION_ALL) {
            filtered = filtered.filter { person ->
                getSection(person.lastNameFurigana) == section
            }
        }
        
        // 2. 検索（キーワードマッチ）フィルタの適用
        if (matchedIds != null) {
            filtered = filtered.filter { person ->
                matchedIds.contains(person.id)
            }
        }
        
        return filtered
    }

    /**
     * 利用者エンティティを表示用の UI 状態（PersonUiState）へ変換します。
     *
     * @param person 変換元の利用者 Entity
     * @param isMasking 伏せ字を適用するかどうか
     * @param summary カテゴリ別の記録状況サマリー
     * @return 構築済みの PersonUiState
     */
    fun createPersonUiState(
        person: Person,
        isMasking: Boolean,
        summary: PersonCategorySummary?
    ): PersonUiState {
        return PersonUiState(
            person = person,
            maskedName = person.getMaskedName(isMasking),
            maskedFurigana = person.getMaskedFurigana(isMasking),
            age = DateTimeUtils.calculateAge(person.birthday),
            formattedBirthday = DateTimeUtils.formatDateJapaneseEra(person.birthday),
            summary = summary ?: PersonCategorySummary()
        )
    }

    /**
     * 利用者の新規追加または更新時に、既存データとの重複を判定します。
     * 姓名、生年月日、および備考がすべて一致する人物を「重複」とみなします。
     *
     * @param input 保存しようとしている情報
     * @param existing DB内に存在する、条件の一致する人物（いなければ null）
     * @return 重複判定の結果
     */
    fun validateDuplicate(input: Person, existing: Person?): PersonDuplicateResult {
        if (existing == null) return PersonDuplicateResult.SUCCESS
        
        // 更新時、自分自身（IDが一致するレコード）であれば重複エラーとはしない
        if (input.id == existing.id) return PersonDuplicateResult.SUCCESS

        return if (existing.deletedAt == null) {
            PersonDuplicateResult.DUPLICATE_ACTIVE
        } else {
            PersonDuplicateResult.DUPLICATE_ARCHIVED
        }
    }
}
