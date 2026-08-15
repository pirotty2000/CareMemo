package jp.mydns.fujiwara.carememo.ui.mapping

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.feature.EmergencyContactType

/**
 * Component：EmergencyContactMapping
 *
 * 【役割】
 * 緊急連絡先に関連するデータの表示用マッピングおよび書式整形を担当します。
 * 種別に応じた名称・アイコンの解決や、電話番号のハイフン付与ロジックを提供します。
 */
object EmergencyContactMapping {

    /** 種別ごとの表示名を取得 */
    @Composable
    fun getLabel(type: String): String {
        val enumType = EmergencyContactType.fromValue(type)
        return when (enumType) {
            EmergencyContactType.DOCTOR -> stringResource(R.string.medical_contact_type_doctor)
            EmergencyContactType.NURSING_STATION -> stringResource(R.string.medical_contact_type_nursing)
            EmergencyContactType.SUPPORT_CENTER -> stringResource(R.string.medical_contact_type_support)
            EmergencyContactType.CASE_WORKER -> stringResource(R.string.medical_contact_type_caseworker)
            EmergencyContactType.FAMILY -> stringResource(R.string.medical_contact_type_family)
            EmergencyContactType.OTHER, null -> stringResource(R.string.medical_contact_type_other)
        }
    }

    /** 種別ごとのアイコンを取得 */
    fun getIcon(type: String): ImageVector {
        val enumType = EmergencyContactType.fromValue(type)
        return when (enumType) {
            EmergencyContactType.DOCTOR -> Icons.Rounded.LocalHospital
            EmergencyContactType.NURSING_STATION -> Icons.Rounded.MedicalServices
            EmergencyContactType.SUPPORT_CENTER -> Icons.Rounded.AccountBalance
            EmergencyContactType.CASE_WORKER -> Icons.Rounded.AssignmentInd
            EmergencyContactType.FAMILY -> Icons.Rounded.FamilyRestroom
            EmergencyContactType.OTHER, null -> Icons.Rounded.ContactPage
        }
    }

    /** 電話番号をハイフン付きに整形して表示用に返す */
    fun formatPhoneNumber(number: String?): String? {
        if (number.isNullOrBlank()) return null

        val digits = number.filter { it.isDigit() }

        return when (digits.length) {
            11 ->  // 携帯・IP電話等 (3-4-4)
                "${digits.take(3)}-${digits.substring(3, 7)}-${digits.takeLast(4)}"
            10 ->
                if (digits.startsWith("03") || digits.startsWith("06")) { // 東京・大阪 (2-4-4)
                    "${digits.take(2)}-${digits.substring(2, 6)}-${digits.takeLast(4)}"
                } else { // その他固定電話 (3-3-4)
                    "${digits.take(3)}-${digits.substring(3, 6)}-${digits.takeLast(4)}"
                }
            else -> number // それ以外は整形せずそのまま返す
        }
    }
}
