package jp.mydns.fujiwara.carememo.data

import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.ui.navigation.Destination

/**
 * 記録カテゴリの定義（アプリの設計原則）
 * 
 * 原則：ある利用者のある日時のひとつのデータであること
 */
enum class Category(
    val displayNameRes: Int,
    val hasOption: Boolean = false
) {
    /** 身長・体重 (A) */
    HEIGHT_AND_WEIGHT(
        displayNameRes = R.string.common_category_height_weight
    ),

    /** バイタル (A) */
    BP_AND_PULSE(
        displayNameRes = R.string.common_category_vital
    ),

    /** 血糖値・HbA1c (A) */
    GLUCOSE_AND_HBA1C(
        displayNameRes = R.string.common_category_glucose
    ),

    /** 所見メモ (B) */
    CONDITION_AT_VISIT(
        displayNameRes = R.string.common_category_condition,
        hasOption = true
    ),

    /** 服薬管理 (C) */
    MEDICATION(
        displayNameRes = R.string.common_category_medication
    );

    /**
     * このカテゴリを表示するための型安全な目的地 (Destination) を生成する
     * 
     * @param personId 利用者ID
     * @param query 検索クエリ (所見メモのみ有効)
     * @return [Destination] オブジェクト
     */
    fun toDestination(personId: String, query: String? = null): Destination {
        return when (this) {
            MEDICATION -> Destination.MedicationDetail(personId, this.name)
            CONDITION_AT_VISIT -> Destination.ConditionDetail(personId, this.name, query)
            else -> Destination.HealthDetail(personId, this.name)
        }
    }
}
