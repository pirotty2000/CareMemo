package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import jp.mydns.fujiwara.carememo.logic.common.JapaneseDateLogic
import java.time.ZoneOffset

/**
 * 利用者の新規登録・編集画面用の UI 状態
 */
data class PersonEditUiState(
    val lastName: String = "",
    val firstName: String = "",
    val lastNameFurigana: String = "",
    val firstNameFurigana: String = "",
    val note: String = "",
    val era: BirthEra = BirthEra.SHOWA,
    val year: String = "",
    val month: String = "",
    val day: String = ""
)

/**
 * 利用者編集画面のバリデーション結果
 */
enum class PersonEditValidationResult {
    SUCCESS,
    EMPTY_LAST_NAME,
    EMPTY_FIRST_NAME,
    INVALID_BIRTHDAY
}

/**
 * 利用者編集画面のドメインロジック
 */
object PersonEditLogic {

    /**
     * 現在の入力内容が初期状態から変更されているかどうかを判定します。
     */
    fun isChanged(current: PersonEditUiState, initial: Person?): Boolean {
        if (initial == null) {
            // 新規登録時は、何かしら入力があれば変更ありとみなす
            return current.lastName.isNotBlank() ||
                    current.firstName.isNotBlank() ||
                    current.lastNameFurigana.isNotBlank() ||
                    current.firstNameFurigana.isNotBlank() ||
                    current.note.isNotBlank() ||
                    current.year.isNotBlank() ||
                    current.month.isNotBlank() ||
                    current.day.isNotBlank()
        }

        // 既存編集時は、各フィールドを比較
        val initialDate = initial.birthday.atZone(ZoneOffset.UTC).toLocalDate()
        val (initialEra, initialYear) = JapaneseDateLogic.toJapaneseDate(initialDate)

        return current.lastName != initial.lastName ||
                current.firstName != initial.firstName ||
                current.lastNameFurigana != initial.lastNameFurigana ||
                current.firstNameFurigana != initial.firstNameFurigana ||
                current.note != initial.note ||
                current.era != initialEra ||
                current.year != initialYear.toString() ||
                current.month != initialDate.monthValue.toString() ||
                current.day != initialDate.dayOfMonth.toString()
    }

    /**
     * 入力内容の妥当性を判定します。
     */
    fun validate(current: PersonEditUiState): PersonEditValidationResult {
        if (current.lastName.isBlank()) return PersonEditValidationResult.EMPTY_LAST_NAME
        if (current.firstName.isBlank()) return PersonEditValidationResult.EMPTY_FIRST_NAME

        val y = current.year.toIntOrNull() ?: return PersonEditValidationResult.INVALID_BIRTHDAY
        val m = current.month.toIntOrNull() ?: return PersonEditValidationResult.INVALID_BIRTHDAY
        val d = current.day.toIntOrNull() ?: return PersonEditValidationResult.INVALID_BIRTHDAY

        if (!JapaneseDateLogic.isValid(current.era, y, m, d)) {
            return PersonEditValidationResult.INVALID_BIRTHDAY
        }

        return PersonEditValidationResult.SUCCESS
    }

    /**
     * 保存可能かどうかを簡易的に判定します（UI用）。
     */
    fun isValid(current: PersonEditUiState): Boolean {
        return validate(current) == PersonEditValidationResult.SUCCESS
    }

    /**
     * UI状態から保存用の Person Entity を構築します。
     * バリデーションに失敗している場合は例外をスローします。
     */
    fun createPerson(current: PersonEditUiState, initial: Person?): Person {
        val y = current.year.toIntOrNull() ?: throw IllegalArgumentException("Invalid year")
        val m = current.month.toIntOrNull() ?: throw IllegalArgumentException("Invalid month")
        val d = current.day.toIntOrNull() ?: throw IllegalArgumentException("Invalid day")

        val birthday = JapaneseDateLogic.toLocalDate(current.era, y, m, d)
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant() ?: throw IllegalArgumentException("Invalid date")

        return (initial?.copy(
            lastName = current.lastName.trim(),
            firstName = current.firstName.trim(),
            lastNameFurigana = current.lastNameFurigana.trim(),
            firstNameFurigana = current.firstNameFurigana.trim(),
            note = current.note.trim(),
            birthday = birthday
        ) ?: Person(
            lastName = current.lastName.trim(),
            firstName = current.firstName.trim(),
            lastNameFurigana = current.lastNameFurigana.trim(),
            firstNameFurigana = current.firstNameFurigana.trim(),
            note = current.note.trim(),
            birthday = birthday
        ))
    }
}
