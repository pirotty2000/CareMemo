package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.viewmodel.PersonUiState

/**
 * 利用者一覧画面に関するドメインロジック。
 */
object PersonListLogic {

    /**
     * ふりがなから、所属する五十音行（あ、か、さ...）を判定します。
     */
    fun getSection(furigana: String): String {
        val firstChar = furigana.firstOrNull() ?: return "他"
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
            else -> "他"
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
        
        // 五十音フィルタ
        if (section != "全") {
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
}
