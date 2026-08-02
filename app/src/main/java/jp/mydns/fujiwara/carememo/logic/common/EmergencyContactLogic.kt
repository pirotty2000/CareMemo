package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.EmergencyContact

/**
 * 緊急連絡先の種別定義。
 * AppSpecifications で定義された文字列値を持ちます。
 */
enum class EmergencyContactType(val value: String) {
    /** 病院・主治医 */
    DOCTOR(AppSpecifications.MedicalContact.Types.DOCTOR),
    /** 訪問看護ステーション */
    NURSING_STATION(AppSpecifications.MedicalContact.Types.NURSING_STATION),
    /** 地域包括支援センター */
    SUPPORT_CENTER(AppSpecifications.MedicalContact.Types.SUPPORT_CENTER),
    /** ケースワーカー */
    CASE_WORKER(AppSpecifications.MedicalContact.Types.CASE_WORKER),
    /** 家族 */
    FAMILY(AppSpecifications.MedicalContact.Types.FAMILY),
    /** その他 */
    OTHER(AppSpecifications.MedicalContact.Types.OTHER);

    companion object {
        /**
         * 保存値（String）から Enum 型を取得します。
         */
        fun fromValue(value: String): EmergencyContactType? = entries.find { it.value == value }
    }
}

/**
 * 緊急連絡先編集画面のバリデーション結果。
 */
enum class EmergencyContactValidationResult {
    /** バリデーション成功 */
    SUCCESS,
    /** 施設名（または続柄）が未入力 */
    EMPTY_FACILITY_NAME,
    /** 施設名が長すぎる */
    FACILITY_NAME_TOO_LONG,
    /** 担当者名が長すぎる */
    PERSON_NAME_TOO_LONG,
    /** 電話番号が長すぎる */
    PHONE_NUMBER_TOO_LONG
}

/**
 * Logic：EmergencyContactLogic
 *
 * 【役割】
 * 緊急連絡先（MedicalContact）に関連する共通のドメインロジックを提供します。
 * 連絡先のバリデーション、初期エンティティの生成、保存データの正規化を担当します。
 *
 * 【主な機能】
 * ・新規登録用の初期データ生成（デフォルト種別や優先順位の設定）。
 * ・入力値の妥当性検証（AppSpecifications に基づく文字数制限等）。
 * ・保存用データの正規化（不要な空白の除去、電話番号の数字抽出）。
 *
 * 【設計指針】
 * 1. 施設名（または続柄）は必須項目とし、空白のみの入力も不可とする。
 * 2. バリデーションは AppSpecifications の定数に厳密に従う。
 * 3. 電話番号は保存時に数字のみを抽出して正規化することで、検索や発信の安定性を高める。
 */
object EmergencyContactLogic {

    /**
     * 新規登録用の初期エンティティを作成します。
     *
     * @param personId 紐付ける利用者ID
     * @return デフォルト値が設定された EmergencyContact
     */
    fun createInitialEntity(personId: String): EmergencyContact {
        return EmergencyContact(
            personId = personId,
            contactType = EmergencyContactType.DOCTOR.value,
            facilityName = "",
            priority = AppSpecifications.MedicalContact.Validation.DEFAULT_PRIORITY
        )
    }

    /**
     * 入力内容の妥当性を判定します。
     *
     * @param contact 検証対象のエンティティ
     * @return バリデーション結果
     */
    fun validate(contact: EmergencyContact): EmergencyContactValidationResult {
        val spec = AppSpecifications.MedicalContact.Validation
        
        // 施設名は必須（空白のみも不可）
        if (contact.facilityName.isBlank()) return EmergencyContactValidationResult.EMPTY_FACILITY_NAME
        
        // 施設名の長さチェック
        if (contact.facilityName.length > spec.MAX_LENGTH_FACILITY_NAME) {
            return EmergencyContactValidationResult.FACILITY_NAME_TOO_LONG
        }
        
        // 担当者名の長さチェック（任意項目のため null 許容）
        contact.personName?.let {
            if (it.length > spec.MAX_LENGTH_PERSON_NAME) return EmergencyContactValidationResult.PERSON_NAME_TOO_LONG
        }
        
        // 電話番号の長さチェック（任意項目のため null 許容）
        contact.phoneNumber?.let {
            if (it.length > spec.MAX_LENGTH_PHONE_NUMBER) return EmergencyContactValidationResult.PHONE_NUMBER_TOO_LONG
        }

        return EmergencyContactValidationResult.SUCCESS
    }

    /**
     * 保存可能かどうかを判定します（UIのボタン有効化用）。
     *
     * @param contact 検証対象のエンティティ
     * @return 保存可能な場合は true
     */
    fun isValid(contact: EmergencyContact?): Boolean {
        if (contact == null) return false
        return validate(contact) == EmergencyContactValidationResult.SUCCESS
    }

    /**
     * 初期状態から変更があるかどうかを判定します。
     *
     * @param current 現在の入力内容
     * @param initial 編集開始時の内容
     * @return 変更がある場合は true
     */
    fun isChanged(current: EmergencyContact?, initial: EmergencyContact?): Boolean {
        return current != initial
    }

    /**
     * 保存用に正規化された Entity を生成します。
     * 
     * 前後の空白除去（trim）や、電話番号からのハイフン除去など、
     * データクレンジングを行った後のインスタンスを返します。
     *
     * @param current 現在の入力内容
     * @return 正規化後の EmergencyContact
     */
    fun createSaveEntity(current: EmergencyContact): EmergencyContact {
        return current.copy(
            facilityName = current.facilityName.trim(),
            personName = current.personName?.trim()?.takeIf { it.isNotBlank() },
            // 電話番号からは数字のみを抽出して保持する
            phoneNumber = current.phoneNumber?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }
        )
    }
}
