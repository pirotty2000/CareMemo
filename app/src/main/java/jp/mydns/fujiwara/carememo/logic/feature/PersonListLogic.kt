package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils

/**
 * 利用者一覧画面全体の表示状態
 */
data class PersonListUiState(
    val isLoading: Boolean = true,
    val selectedSection: String = AppSpecifications.Search.SECTION_ALL,
    val searchQuery: String = "",
    val userList: List<PersonUiState> = emptyList(),
    val isNameMaskingEnabled: Boolean = true
)

/**
 * 利用者一覧画面固有のイベント
 */
sealed interface PersonListViewEvent {
    // 将来的な拡張用
}

/**
 * 利用者一覧の各項目の表示状態を保持するクラス
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
 * 利用者追加・更新時の重複判定結果（事実）
 */
enum class PersonDuplicateResult {
    SUCCESS,
    DUPLICATE_ACTIVE,
    DUPLICATE_ARCHIVED
}

/**
 * 利用者一覧画面に関するドメインロジック。
 */
object PersonListLogic {

    /**
     * ふりがなから、所属する五十音行（あ、か、さ...）を判定します。
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
     */
    fun filterPersons(
        allPersons: List<Person>,
        section: String,
        matchedIds: List<Int>?
    ): List<Person> {
        var filtered = allPersons
        
        // 五五十音フィルタ
        if (section != AppSpecifications.Search.SECTION_ALL) {
            filtered = filtered.filter { person ->
                getSection(person.lastNameFurigana) == section
            }
        }
        
        // 検索（キーワードマッチ）フィルタ
        if (matchedIds != null) {
            filtered = filtered.filter { person ->
                matchedIds.contains(person.id)
            }
        }
        
        return filtered
    }

    /**
     * 利用者エンティティを表示用の UI 状態へ変換します。
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
     * 新規追加または更新時に、既存データとの重複を判定します。
     */
    fun validateDuplicate(input: Person, existing: Person?): PersonDuplicateResult {
        if (existing == null) return PersonDuplicateResult.SUCCESS
        
        // 更新時、自分自身（ID一致）であれば重複とはみなさない
        if (input.id != 0 && input.id == existing.id) return PersonDuplicateResult.SUCCESS

        return if (existing.deletedAt == null) {
            PersonDuplicateResult.DUPLICATE_ACTIVE
        } else {
            PersonDuplicateResult.DUPLICATE_ARCHIVED
        }
    }
}
