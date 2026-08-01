package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.EmergencyContact

/**
 * 緊急連絡先の種別定義
 */
enum class EmergencyContactType(val value: String) {
    DOCTOR(AppSpecifications.MedicalContact.Types.DOCTOR),
    NURSING_STATION(AppSpecifications.MedicalContact.Types.NURSING_STATION),
    SUPPORT_CENTER(AppSpecifications.MedicalContact.Types.SUPPORT_CENTER),
    CASE_WORKER(AppSpecifications.MedicalContact.Types.CASE_WORKER),
    FAMILY(AppSpecifications.MedicalContact.Types.FAMILY),
    OTHER(AppSpecifications.MedicalContact.Types.OTHER);

    companion object {
        fun fromValue(value: String): EmergencyContactType? = entries.find { it.value == value }
    }
}

/**
 * 緊急連絡先編集画面のバリデーション結果
 */
enum class EmergencyContactValidationResult {
    SUCCESS,
    EMPTY_FACILITY_NAME,
    FACILITY_NAME_TOO_LONG,
    PERSON_NAME_TOO_LONG,
    PHONE_NUMBER_TOO_LONG
}

/**
 * 緊急連絡先に関するドメインロジック
 */
object EmergencyContactLogic {

    /**
     * 新規登録用の初期エンティティを作成します。
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
     */
    fun validate(contact: EmergencyContact): EmergencyContactValidationResult {
        val spec = AppSpecifications.MedicalContact.Validation
        
        if (contact.facilityName.isBlank()) return EmergencyContactValidationResult.EMPTY_FACILITY_NAME
        
        if (contact.facilityName.length > spec.MAX_LENGTH_FACILITY_NAME) {
            return EmergencyContactValidationResult.FACILITY_NAME_TOO_LONG
        }
        
        contact.personName?.let {
            if (it.length > spec.MAX_LENGTH_PERSON_NAME) return EmergencyContactValidationResult.PERSON_NAME_TOO_LONG
        }
        
        contact.phoneNumber?.let {
            if (it.length > spec.MAX_LENGTH_PHONE_NUMBER) return EmergencyContactValidationResult.PHONE_NUMBER_TOO_LONG
        }

        return EmergencyContactValidationResult.SUCCESS
    }

    /**
     * 保存可能かどうかを判定します（UIのボタン有効化用）。
     */
    fun isValid(contact: EmergencyContact?): Boolean {
        if (contact == null) return false
        return validate(contact) == EmergencyContactValidationResult.SUCCESS
    }

    /**
     * 変更があるかどうかを判定します。
     */
    fun isChanged(current: EmergencyContact?, initial: EmergencyContact?): Boolean {
        return current != initial
    }

    /**
     * 保存用の正規化された Entity を生成します。
     */
    fun createSaveEntity(current: EmergencyContact): EmergencyContact {
        return current.copy(
            facilityName = current.facilityName.trim(),
            personName = current.personName?.trim()?.takeIf { it.isNotBlank() },
            phoneNumber = current.phoneNumber?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }
        )
    }
}
