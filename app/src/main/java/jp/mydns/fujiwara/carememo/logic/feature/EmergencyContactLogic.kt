package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.runtime.Immutable
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * UI State：EmergencyContactUiState
 */
@Immutable
data class EmergencyContactUiState(
    val screenState: EmergencyContactScreenState = EmergencyContactScreenState.Loading,
    val operation: EmergencyContactOperation = EmergencyContactOperation.Idle,

    val personId: String = "",
    val personName: String = "",
    val contacts: ImmutableList<EmergencyContact> = persistentListOf(),

    val session: EmergencyContactSession = EmergencyContactSession(),

    val isNameMaskingEnabled: Boolean = true,

    @Deprecated("Use screenState and operation")
    val isLoading: Boolean = false
)

/**
 * 構造的状態 (Structural State)
 */
sealed interface EmergencyContactScreenState {
    data object Loading : EmergencyContactScreenState
    data object Active : EmergencyContactScreenState
    data class Error(val throwable: Throwable) : EmergencyContactScreenState
}

/**
 * 操作状態 (Operation State)
 */
sealed interface EmergencyContactOperation {
    data object Idle : EmergencyContactOperation
    data object Saving : EmergencyContactOperation
    data object Deleting : EmergencyContactOperation
}

/**
 * 編集セッション状態 (UI Content Details)
 */
@Immutable
data class EmergencyContactSession(
    val isEditing: Boolean = false,
    val editingContact: EmergencyContact? = null,
    val initialContact: EmergencyContact? = null,
    val isChanged: Boolean = false,
    val isValid: Boolean = false,
    val fieldErrors: Map<String, Int?> = emptyMap(),
    val touchedFields: Set<String> = emptySet()
)

/**
 * View Event：EmergencyContactViewEvent
 */
sealed interface EmergencyContactViewEvent {
    data object NavigateBack : EmergencyContactViewEvent
    data object SaveSuccess : EmergencyContactViewEvent
    data object DeleteSuccess : EmergencyContactViewEvent
}

/**
 * 緊急連絡先の種別定義。
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
 * 緊急連絡先編集画面のバリデーション結果。
 */
enum class EmergencyContactValidationResult {
    SUCCESS,
    EMPTY_FACILITY_NAME,
    FACILITY_NAME_TOO_LONG,
    PERSON_NAME_TOO_LONG,
    PHONE_NUMBER_TOO_LONG
}

/**
 * Logic：EmergencyContactLogic
 *
 * 【役割】
 * 緊急連絡先（MedicalContact）に関連するドメインロジックを提供します。
 */
object EmergencyContactLogic {

    /**
     * 新規登録用の初期エンティティを作成します。
     */
    fun createInitialEntity(personId: String): EmergencyContact {
        return EmergencyContact(
            id = AppSpecifications.Id.NEW_RECORD_ID,
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
        if (contact.facilityName.length > spec.MAX_LENGTH_FACILITY_NAME) return EmergencyContactValidationResult.FACILITY_NAME_TOO_LONG
        contact.personName?.let { if (it.length > spec.MAX_LENGTH_PERSON_NAME) return EmergencyContactValidationResult.PERSON_NAME_TOO_LONG }
        contact.phoneNumber?.let { if (it.length > spec.MAX_LENGTH_PHONE_NUMBER) return EmergencyContactValidationResult.PHONE_NUMBER_TOO_LONG }
        return EmergencyContactValidationResult.SUCCESS
    }

    /**
     * 保存可能かどうかを判定します。
     */
    fun isValid(contact: EmergencyContact?): Boolean {
        if (contact == null) return false
        return validate(contact) == EmergencyContactValidationResult.SUCCESS
    }

    /**
     * 初期状態から変更があるかどうかを判定します。
     */
    fun isChanged(current: EmergencyContact?, initial: EmergencyContact?): Boolean {
        return current != initial
    }

    /**
     * 保存用に正規化された Entity を生成します。
     */
    fun createSaveEntity(current: EmergencyContact): EmergencyContact {
        return current.copy(
            facilityName = current.facilityName.trim(),
            personName = current.personName?.trim()?.takeIf { it.isNotBlank() },
            phoneNumber = current.phoneNumber?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }
        )
    }
}
