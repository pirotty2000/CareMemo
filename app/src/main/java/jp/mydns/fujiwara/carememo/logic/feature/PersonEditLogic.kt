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
 * @param lastName 姓
 * @param firstName 名
 * @param lastNameFurigana 姓（ふりがな）
 * @param firstNameFurigana 名（ふりがな）
 * @param note 同姓同名識別用メモ
 * @param era 生年月日の元号
 * @param year 生年月日の年
 * @param month 生年月日の月
 * @param day 生年月日の日
 * @param isLoading データの読み込み中フラグ
 * @param isValid 入力内容がバリデーションを通過しているか
 * @param isChanged 初期状態から変更があるか
 * @param isNameMaskingEnabled 氏名のマスキング（伏せ字）が有効か
 * @param isNew 新規登録モードかどうか
 */
@Immutable
data class PersonEditUiState(
    val lastName: String = "",
    val firstName: String = "",
    val lastNameFurigana: String = "",
    val firstNameFurigana: String = "",
    val note: String = "",
    val era: BirthEra = BirthEra.SHOWA,
    val year: String = "",
    val month: String = "",
    val day: String = "",
    // フェーズ 2 で追加: 集約された状態
    val isLoading: Boolean = false,
    val isValid: Boolean = false,
    val isChanged: Boolean = false,
    val isNameMaskingEnabled: Boolean = true,
    val isNew: Boolean = false
)

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
 *
 * 【主な機能】
 * ・入力内容の変更検知（初期状態と比較し、保存ボタンや戻る警告を制御）。
 * ・保存前のバリデーション（必須入力、文字数制限、和暦の妥当性）。
 * ・UI状態から永続化用エンティティ（Person）への変換と正規化。
 *
 * 【設計指針】
 * 1. 氏名およびふりがなは必須項目とし、保存時に前後の空白を自動除去（trim）する。
 * 2. 生年月日は和暦入力であっても内部的には西暦の Instant として正規化して保持する。
 * 3. 文字数制限は AppSpecifications.Constraints.Person に定義されたプロジェクト共通定数を参照する。
 */
object PersonEditLogic {

    /**
     * 現在の入力内容が初期状態（またはDBの元の値）から変更されているかどうかを判定します。
     *
     * @param current 現在のUI状態
     * @param initial 編集開始時の元の利用者情報（新規なら null）
     * @return 1箇所でも変更があれば true
     */
    fun isChanged(current: PersonEditUiState, initial: Person?): Boolean {
        if (initial == null) {
            // 新規登録時は、何かしら入力があれば変更ありとみなす（初期値は全て空・デフォルトを想定）
            return current.lastName.isNotBlank() ||
                    current.firstName.isNotBlank() ||
                    current.lastNameFurigana.isNotBlank() ||
                    current.firstNameFurigana.isNotBlank() ||
                    current.note.isNotBlank() ||
                    current.year.isNotBlank() ||
                    current.month.isNotBlank() ||
                    current.day.isNotBlank()
        }

        // 既存編集時は、各フィールドを元の Entity と比較
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
     * 入力内容の妥当性を詳細に判定します。
     * 必須チェック、文字数制限、および和暦日付の論理チェックを順次行います。
     *
     * @param current 検証対象のUI状態
     * @return [PersonEditValidationResult]
     */
    fun validate(current: PersonEditUiState): PersonEditValidationResult {
        val spec = AppSpecifications.Constraints.Person.Validation
        
        // 1. 必須チェック
        if (current.lastName.isBlank()) return PersonEditValidationResult.EMPTY_LAST_NAME
        if (current.firstName.isBlank()) return PersonEditValidationResult.EMPTY_FIRST_NAME
        if (current.lastNameFurigana.isBlank()) return PersonEditValidationResult.EMPTY_LAST_FURIGANA
        if (current.firstNameFurigana.isBlank()) return PersonEditValidationResult.EMPTY_FIRST_FURIGANA

        // 2. 文字数制限チェック
        if (current.lastName.length > spec.MAX_LENGTH_LAST_NAME || current.firstName.length > spec.MAX_LENGTH_FIRST_NAME) {
            return PersonEditValidationResult.NAME_TOO_LONG
        }
        if (current.lastNameFurigana.length > spec.MAX_LENGTH_LAST_NAME_FURIGANA || current.firstNameFurigana.length > spec.MAX_LENGTH_FIRST_NAME_FURIGANA) {
            return PersonEditValidationResult.FURIGANA_TOO_LONG
        }
        if (current.note.length > spec.MAX_LENGTH_NOTE) {
            return PersonEditValidationResult.NOTE_TOO_LONG
        }

        // 3. 生年月日の論理チェック（JapaneseDateLogic に委譲）
        val y = current.year.toIntOrNull() ?: return PersonEditValidationResult.INVALID_BIRTHDAY
        val m = current.month.toIntOrNull() ?: return PersonEditValidationResult.INVALID_BIRTHDAY
        val d = current.day.toIntOrNull() ?: return PersonEditValidationResult.INVALID_BIRTHDAY

        if (!JapaneseDateLogic.isValid(current.era, y, m, d)) {
            return PersonEditValidationResult.INVALID_BIRTHDAY
        }

        return PersonEditValidationResult.SUCCESS
    }

    /**
     * 保存ボタンを活性化して良いかどうかを簡易的に判定します。
     *
     * @param current 現在のUI状態
     * @return 妥当な場合は true
     */
    fun isValid(current: PersonEditUiState): Boolean {
        return validate(current) == PersonEditValidationResult.SUCCESS
    }

    /**
     * UI状態から保存用の Person Entity を構築します。
     * あわせて、各項目の前後の不要な空白をトリミングします。
     *
     * @param current 現在のUI状態
     * @param initial 元の Entity（編集時のコピー元。新規なら null）
     * @return 構築および正規化済みの Person インスタンス
     * @throws IllegalArgumentException バリデーションに失敗している（日付変換不能等）場合にスロー
     */
    fun createPerson(current: PersonEditUiState, initial: Person?): Person {
        val y = current.year.toIntOrNull() ?: throw IllegalArgumentException("Invalid year")
        val m = current.month.toIntOrNull() ?: throw IllegalArgumentException("Invalid month")
        val d = current.day.toIntOrNull() ?: throw IllegalArgumentException("Invalid day")

        // 和暦/西暦から正規化された日付（Instant）を取得
        val birthday = JapaneseDateLogic.toLocalDate(current.era, y, m, d)
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant() ?: throw IllegalArgumentException("Invalid date")

        // 各フィールドを trim して正規化
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
