package jp.mydns.fujiwara.carememo.data.spec

/**
 * Spec：SettingsSpecifications
 *
 * 【役割】
 * 設定画面で使用する選択肢（監査ログの保持期間等）のバリエーションを定義します。
 */
object SettingsSpecifications {
    /** 監査ログ保持期間の選択肢 (日 to ラベル) */
    val AUDIT_LOG_RETENTION_OPTIONS = listOf(
        7 to "1週間",
        14 to "2週間",
        30 to "1ヶ月",
        90 to "3ヶ月",
        180 to "半年",
        365 to "1年",
        0 to "残さない"
    )
}
