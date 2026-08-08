package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R

/**
 * 機能識別子 (featureName) を表示用の日本語名称（リソースID）に変換する拡張プロパティ
 */
val String.toFeatureLabelRes: Int
    get() = when (this) {
        "PersonList" -> R.string.audit_feature_person_list
        "PersonEdit" -> R.string.audit_feature_person_edit
        "DeleteOrRestorePerson" -> R.string.audit_feature_person_archive
        "PersonBase" -> R.string.audit_feature_person_base
        "PersonHealth" -> R.string.audit_feature_health
        "BatchInput" -> R.string.audit_feature_batch_input
        "PersonDetail/HEIGHT_AND_WEIGHT" -> R.string.audit_feature_detail_height_weight
        "PersonDetail/BP_AND_PULSE" -> R.string.audit_feature_detail_vital
        "PersonDetail/GLUCOSE_AND_HBA1C" -> R.string.audit_feature_detail_glucose
        "PersonCondition" -> R.string.audit_feature_condition
        "PersonDetail/CONDITION" -> R.string.audit_feature_detail_condition
        "PersonMedication" -> R.string.audit_feature_medication
        "PersonDetail/MEDICATION" -> R.string.audit_feature_detail_medication
        "Settings" -> R.string.audit_feature_settings
        "PersonDetail/Base" -> R.string.audit_feature_detail_base
        else -> 0 // 該当なし
    }
