package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.runtime.Immutable
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * UI State：PersonListUiState
 *
 * 【役割】
 * 利用者一覧画面（MainScreen）全体の表示状態を保持します。
 * 
 * @param screenState 構造的状態（Loading / Active / Error）
 * @param userList 画面に表示される加工済みの利用者リスト
 * @param emergencyContactsForSheet ボトムシート用連絡先リスト
 * @param searchQuery 検索キーワード
 * @param selectedSection 現在選択されている五十音セクション
 * @param isNameMaskingEnabled 氏名のマスキング（伏せ字）が有効か
 * @param selectedPersonForQuickMenu クイックメニュー対象の利用者
 * @param isQuickActionMenuExpanded クイックメニュー表示フラグ
 * @param operation 実行中の操作状態
 * @param isEmergencyContactLoading 連絡先読み込み中フラグ（局所制御用）
 */
data class PersonListUiState(
    val screenState: PersonListScreenState = PersonListScreenState.Loading,
    val userList: ImmutableList<PersonUiState> = persistentListOf(),
    val emergencyContactsForSheet: ImmutableList<EmergencyContact>? = null,
    val searchQuery: String = "",
    val selectedSection: String = AppSpecifications.Search.SECTION_ALL,
    val isNameMaskingEnabled: Boolean = true,
    val selectedPersonForQuickMenu: Person? = null,
    val isQuickActionMenuExpanded: Boolean = false,
    val operation: PersonListOperation = PersonListOperation.Idle,
    val isEmergencyContactLoading: Boolean = false
)

/**
 * 構造的状態 (Structural State)
 */
sealed interface PersonListScreenState {
    /** 画面構築に必要なデータが揃っておらず、取得中の状態（全画面シマー等） */
    data object Loading : PersonListScreenState
    /** データ取得に成功し、表示可能な Content (Empty含む) が存在する状態 */
    data object Active : PersonListScreenState
    /** 画面構築に必要なデータを取得できず、Content を構築できない致命的状態 */
    data class Error(val throwable: Throwable) : PersonListScreenState
}

/**
 * 操作状態 (Operation State)
 */
sealed interface PersonListOperation {
    /** 待機状態 */
    data object Idle : PersonListOperation
    /** Content 表示中におけるバックグラウンド再取得中 */
    data object Refreshing : PersonListOperation
    /** 利用者追加処理中 */
    data object Adding : PersonListOperation
    /** 特定利用者の削除処理中 */
    data class Deleting(val targetId: String) : PersonListOperation
    /** 特定利用者の復旧処理中 */
    data class Restoring(val targetId: String) : PersonListOperation
}

/**
 * View Event：PersonListViewEvent
 */
sealed interface PersonListViewEvent {
    /** 詳細画面（各カテゴリ）へ遷移 */
    data class NavigateToDetail(
        val personId: String,
        val category: jp.mydns.fujiwara.carememo.data.Category,
        val query: String? = null
    ) : PersonListViewEvent
    /** 一括入力画面へ遷移 */
    data class NavigateToBatchInput(val personId: String) : PersonListViewEvent
    /** 利用者追加画面へ遷移 */
    object NavigateToAddPerson : PersonListViewEvent
    /** 利用者編集画面へ遷移 */
    data class NavigateToEditPerson(val personId: String) : PersonListViewEvent
    /** 設定画面へ遷移 */
    object NavigateToSettings : PersonListViewEvent
    /** アラート・レポート画面へ遷移 */
    data class NavigateToAlertReport(val personId: String? = null) : PersonListViewEvent
    /** 緊急連絡先画面へ遷移 */
    data class NavigateToMedicalContacts(val personId: String) : PersonListViewEvent
}

/**
 * UI State：PersonUiState
 *
 * 【役割】
 * 利用者一覧の各行（1名分）の表示状態を保持する UI 専用モデルです。
 */
@Immutable
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
            in 'ぁ'..'お' -> "あ"
            in 'か'..'ご' -> "か"
            in 'さ'..'ぞ' -> "さ"
            in 'た'..'ど' -> "た"
            in 'な'..'の' -> "な"
            in 'は'..'ぽ' -> "は"
            in 'ま'..'も' -> "ま"
            in 'ゃ'..'よ' -> "や"
            in 'ら'..'ろ' -> "ら"
            in 'ゎ'..'ん' -> "わ"
            else -> AppSpecifications.Search.SECTION_OTHER
        }
    }

    /**
     * 各種条件に基づき、利用者リストをフィルタリングします。
     *
     * 【設計意図】
     * Logic レイヤーの純粋性を保つため、戻り値には標準の [List] を使用します。
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
