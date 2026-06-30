package jp.mydns.fujiwara.carememo.data

import jp.mydns.fujiwara.carememo.R

/**
 * 記録カテゴリの定義（アプリの設計原則）
 * 
 * 原則：ある利用者のある日時のひとつのデータであること
 * 
 * 1. (A) 数値データ型:
 *    - 健康指標（Thresholds）に基づきアラートを定義・表示できる
 *    - 時系列グラフを表示できる
 *    - 文字列検索は行わない
 * 
 * 2. (B) 文字列データ型:
 *    - 自由な文字列入力が可能
 *    - 文字列による履歴の検索ができる
 *    - アラート判定およびグラフ表示は行わない
 *    - 写真等の追加情報（Option）を付与できる
 *
 * 3. (C) 状態データ型 (服薬管理など):
 *    - 年月日で集約し、カレンダーで状態を可視化する
 *    - グラフ表示や文字列検索は行わない
 */
enum class Category(
    val displayNameRes: Int,
    val hasGraph: Boolean = true,
    val hasOption: Boolean = false
) {
    /** 身長・体重 (A) */
    HEIGHT_AND_WEIGHT(
        displayNameRes = R.string.category_height_weight
    ),

    /** バイタル (A) */
    BP_AND_PULSE(
        displayNameRes = R.string.category_vital
    ),

    /** 血糖値・HbA1c (A) */
    GLUCOSE_AND_HBA1C(
        displayNameRes = R.string.category_glucose
    ),

    /** 所見メモ (B) */
    CONDITION_AT_VISIT(
        displayNameRes = R.string.category_condition,
        hasGraph = false,
        hasOption = true
    ),

    /** 服薬管理 (C) */
    MEDICATION(
        displayNameRes = R.string.category_medication,
        hasGraph = false
    );

    /**
     * このカテゴリを表示するためのナビゲーションルートを生成する
     */
    fun getRoute(personId: Int, query: String = ""): String {
        return when (this) {
            MEDICATION -> "medication/$personId"
            CONDITION_AT_VISIT -> "observation/$personId?query=$query"
            else -> "detail/$personId/${this.name}?query=$query"
        }
    }
}
