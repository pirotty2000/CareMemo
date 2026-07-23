package jp.mydns.fujiwara.carememo.data

import jp.mydns.fujiwara.carememo.data.spec.*

/**
 * アプリの仕様定義（辞書）の窓口オブジェクト。
 * 実際の実装は jp.mydns.fujiwara.carememo.data.spec パッケージ配下の各ファイルに委譲する。
 */
object AppSpecifications {
    /** 健康データに関する仕様 */
    val Health = HealthSpecifications

    /** 所見メモに関する仕様 */
    val Condition = ConstraintSpecifications.Condition

    /** 服薬管理に関する仕様 */
    val Medication = MedicationSpecifications

    /** 日本の暦（和暦）に関する仕様 */
    val JapaneseCalendar = CalendarSpecifications

    /** 出力・エクスポートに関する仕様 */
    val Export = ExportSpecifications

    /** 各種制約（文字数制限等）に関する仕様 */
    val Constraints = ConstraintSpecifications

    /** 検索・インデックスに関する仕様 */
    val Search = SearchSpecifications

    /** 設定項目に関する仕様 */
    val Settings = SettingsSpecifications
}
