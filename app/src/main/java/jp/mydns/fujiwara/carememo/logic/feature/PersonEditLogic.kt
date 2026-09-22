package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.runtime.Immutable
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import jp.mydns.fujiwara.carememo.logic.common.JapaneseDateLogic
import jp.mydns.fujiwara.carememo.ui.navigation.EditResult
import java.time.ZoneOffset

/**
 * UI State：PersonEditUiState
 *
 * 【役割】
 * 利用者の新規登録および情報編集画面における、すべての入力値と画面状態を保持します。
 *
 * @param screenState 構造的状態（Loading / Active / Error）
 * @param initialData 編集開始時の元のデータ（新規時は null）
 * @param input 現在の入力フィールド群
 * @param isNew 新規登録モードかどうか
 * @param isNameMaskingEnabled 氏名のマスキング（伏せ字）が有効か
 * @param isValid 入力内容がバリデーションを通過しているか
 * @param isChanged 初期状態から変更があるか
 * @param operation 実行中の操作状態
 * @param fieldErrors フィールドごとのエラーリソースID
 * @param touchedFields 操作済みのフィールド名のセット
 */
@Immutable
data class PersonEditUiState(
    val screenState: PersonEditScreenState = PersonEditScreenState.Loading,
    val initialData: Person? = null,
    val input: PersonEditInput = PersonEditInput(),
    val isNew: Boolean = false,
    val isNameMaskingEnabled: Boolean = true,
    val isValid: Boolean = false,
    val isChanged: Boolean = false,
    val operation: PersonEditOperation = PersonEditOperation.Idle,
    val fieldErrors: Map<String, Int?> = emptyMap(),
    val touchedFields: Set<String> = emptySet()
)

/**
 * UI Input：PersonEditInput
 * 利用者登録・編集画面の入力フォーム状態を保持するデータクラスです。
 */
@Immutable
data class PersonEditInput(
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
 * 構造的状態 (Structural State)
 */
sealed interface PersonEditScreenState {
    data object Loading : PersonEditScreenState
    data object Active : PersonEditScreenState
    data class Error(val throwable: Throwable) : PersonEditScreenState
}

/**
 * 操作状態 (Operation State)
 */
sealed interface PersonEditOperation {
    data object Idle : PersonEditOperation
    data object Saving : PersonEditOperation
}

/**
 * View Event：PersonEditViewEvent
 *
 * 【役割】
 * 利用者編集画面において、コルーチン等から一過性のアクションを通知するために使用します。
 */
sealed interface PersonEditViewEvent {
    /** 前の画面に戻る */
    data class NavigateBack(val result: EditResult? = null, val personName: String? = null) : PersonEditViewEvent
}

/**
 * 利用者情報のバリデーション結果（事実）。
 */
enum class PersonEditValidationResult {
    /** バリデーション成功 */
    SUCCESS,
    /** 姓が未入力 */
    EMPTY_LAST_NAME,
    /** 名が未入力 */
    EMPTY_FIRST_NAME,
    /** 姓（ふりがな）が未入力 */
    EMPTY_LAST_FURIGANA,
    /** 名（ふりがな）が未入力 */
    EMPTY_FIRST_FURIGANA,
    /** 生年月日が不正（暦に存在しない、または形式不正） */
    INVALID_BIRTHDAY,
    /** 姓名が制限文字数を超過 */
    NAME_TOO_LONG,
    /** ふりがなが制限文字数を超過 */
    FURIGANA_TOO_LONG,
    /** 備考が制限文字数を超過 */
    NOTE_TOO_LONG
}

/**
 * Logic：PersonEditLogic
 *
 * 【役割】
 * 利用者の基本情報（氏名、生年月日等）の登録・編集に関するドメインロジックを提供します。
 */
object PersonEditLogic {

    /**
     * 現在の入力内容が初期状態（またはDBの元の値）から変更されているかどうかを判定します。
     *
     * @param input 現在の入力内容
     * @param initial 編集開始時の元の利用者情報（新規なら null）
     * @return 1箇所でも変更があれば true
     */
    fun isChanged(input: PersonEditInput, initial: Person?): Boolean {
        if (initial == null) {
            return input.lastName.isNotBlank() ||
                    input.firstName.isNotBlank() ||
                    input.lastNameFurigana.isNotBlank() ||
                    input.firstNameFurigana.isNotBlank() ||
                    input.note.isNotBlank() ||
                    input.year.isNotBlank() ||
                    input.month.isNotBlank() ||
                    input.day.isNotBlank()
        }

        val initialDate = initial.birthday.atZone(ZoneOffset.UTC).toLocalDate()
        val (initialEra, initialYear) = JapaneseDateLogic.toJapaneseDate(initialDate)

        return input.lastName != initial.lastName ||
                input.firstName != initial.firstName ||
                input.lastNameFurigana != initial.lastNameFurigana ||
                input.firstNameFurigana != initial.firstNameFurigana ||
                input.note != initial.note ||
                input.era != initialEra ||
                input.year != initialYear.toString() ||
                input.month != initialDate.monthValue.toString() ||
                input.day != initialDate.dayOfMonth.toString()
    }

    /**
     * 入力内容の妥当性を詳細に判定し、フィールドごとのエラーを返します。
     *
     * @param input 検証対象の入力内容
     * @return フィールド名をキー、バリデーション結果を値とするマップ
     */
    fun validateAll(input: PersonEditInput): Map<String, PersonEditValidationResult> {
        val errors = mutableMapOf<String, PersonEditValidationResult>()
        val spec = AppSpecifications.Constraints.Person.Validation

        if (input.lastName.isBlank()) {
            errors["lastName"] = PersonEditValidationResult.EMPTY_LAST_NAME
        } else if (input.lastName.length > spec.MAX_LENGTH_LAST_NAME) {
            errors["lastName"] = PersonEditValidationResult.NAME_TOO_LONG
        }

        if (input.firstName.isBlank()) {
            errors["firstName"] = PersonEditValidationResult.EMPTY_FIRST_NAME
        } else if (input.firstName.length > spec.MAX_LENGTH_FIRST_NAME) {
            errors["firstName"] = PersonEditValidationResult.NAME_TOO_LONG
        }

        if (input.lastNameFurigana.isBlank()) {
            errors["lastNameFurigana"] = PersonEditValidationResult.EMPTY_LAST_FURIGANA
        } else if (input.lastNameFurigana.length > spec.MAX_LENGTH_LAST_NAME_FURIGANA) {
            errors["lastNameFurigana"] = PersonEditValidationResult.FURIGANA_TOO_LONG
        }

        if (input.firstNameFurigana.isBlank()) {
            errors["firstNameFurigana"] = PersonEditValidationResult.EMPTY_FIRST_FURIGANA
        } else if (input.firstNameFurigana.length > spec.MAX_LENGTH_FIRST_NAME_FURIGANA) {
            errors["firstNameFurigana"] = PersonEditValidationResult.FURIGANA_TOO_LONG
        }

        if (input.note.length > spec.MAX_LENGTH_NOTE) {
            errors["note"] = PersonEditValidationResult.NOTE_TOO_LONG
        }

        val y = input.year.toIntOrNull()
        val m = input.month.toIntOrNull()
        val d = input.day.toIntOrNull()

        if (y == null || m == null || d == null || !JapaneseDateLogic.isValid(input.era, y, m, d)) {
            errors["birthday"] = PersonEditValidationResult.INVALID_BIRTHDAY
        }

        return errors
    }

    /**
     * 入力内容の妥当性を判定します。
     *
     * @param input 検証対象の入力内容
     * @return [PersonEditValidationResult]
     */
    fun validate(input: PersonEditInput): PersonEditValidationResult {
        val spec = AppSpecifications.Constraints.Person.Validation
        
        if (input.lastName.isBlank()) return PersonEditValidationResult.EMPTY_LAST_NAME
        if (input.firstName.isBlank()) return PersonEditValidationResult.EMPTY_FIRST_NAME
        if (input.lastNameFurigana.isBlank()) return PersonEditValidationResult.EMPTY_LAST_FURIGANA
        if (input.firstNameFurigana.isBlank()) return PersonEditValidationResult.EMPTY_FIRST_FURIGANA

        if (input.lastName.length > spec.MAX_LENGTH_LAST_NAME || input.firstName.length > spec.MAX_LENGTH_FIRST_NAME) {
            return PersonEditValidationResult.NAME_TOO_LONG
        }
        if (input.lastNameFurigana.length > spec.MAX_LENGTH_LAST_NAME_FURIGANA || input.firstNameFurigana.length > spec.MAX_LENGTH_FIRST_NAME_FURIGANA) {
            return PersonEditValidationResult.FURIGANA_TOO_LONG
        }
        if (input.note.length > spec.MAX_LENGTH_NOTE) {
            return PersonEditValidationResult.NOTE_TOO_LONG
        }

        val y = input.year.toIntOrNull() ?: return PersonEditValidationResult.INVALID_BIRTHDAY
        val m = input.month.toIntOrNull() ?: return PersonEditValidationResult.INVALID_BIRTHDAY
        val d = input.day.toIntOrNull() ?: return PersonEditValidationResult.INVALID_BIRTHDAY

        if (!JapaneseDateLogic.isValid(input.era, y, m, d)) {
            return PersonEditValidationResult.INVALID_BIRTHDAY
        }

        return PersonEditValidationResult.SUCCESS
    }

    /**
     * 保存ボタンを活性化して良いかどうかを簡易的に判定します。
     */
    fun isValid(input: PersonEditInput): Boolean {
        return validate(input) == PersonEditValidationResult.SUCCESS
    }

    /**
     * 入力内容から保存用の Person Entity を構築します。
     *
     * @param input 現在の入力内容
     * @param initial 元の Entity（編集時のコピー元。新規なら null）
     * @return 構築および正規化済みの Person インスタンス
     */
    fun createPerson(input: PersonEditInput, initial: Person?): Person {
        val y = input.year.toIntOrNull() ?: throw IllegalArgumentException("Invalid year")
        val m = input.month.toIntOrNull() ?: throw IllegalArgumentException("Invalid month")
        val d = input.day.toIntOrNull() ?: throw IllegalArgumentException("Invalid day")

        val birthday = JapaneseDateLogic.toLocalDate(input.era, y, m, d)
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant() ?: throw IllegalArgumentException("Invalid date")

        return (initial?.copy(
            lastName = input.lastName.trim(),
            firstName = input.firstName.trim(),
            lastNameFurigana = input.lastNameFurigana.trim(),
            firstNameFurigana = input.firstNameFurigana.trim(),
            note = input.note.trim(),
            birthday = birthday
        ) ?: Person(
            lastName = input.lastName.trim(),
            firstName = input.firstName.trim(),
            lastNameFurigana = input.lastNameFurigana.trim(),
            firstNameFurigana = input.firstNameFurigana.trim(),
            note = input.note.trim(),
            birthday = birthday
        ))
    }
}
