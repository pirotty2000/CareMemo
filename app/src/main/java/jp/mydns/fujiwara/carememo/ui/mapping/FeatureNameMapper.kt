package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R

/**
 * Component：FeatureNameMapper
 *
 * 【役割】
 * 監査ログに出力される内部的な機能識別子（featureName）を、
 * ユーザーが理解可能な日本語名称（リソースID）に変換するマッパーです。
 *
 * 【全体像：機能名マッピング】
 *
 * "PersonList"            ➔ 利用者一覧
 * "PersonEdit"            ➔ 利用者登録・編集
 * "DeleteOrRestorePerson" ➔ 利用者アーカイブ管理
 * "PersonHealth"          ➔ 健康記録
 * "BatchInput"            ➔ 一括入力
 * "PersonCondition"       ➔ 所見メモ
 * "PersonMedication"      ➔ 服薬管理
 * "Settings"              ➔ 設定
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
