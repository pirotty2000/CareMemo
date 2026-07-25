package jp.mydns.fujiwara.carememo.data.spec

/**
 * ID体系に関する仕様定義.
 */
object IdSpecifications {
    /**
     * 新規レコードであることを示すシステム共通の識別子.
     * 各機能（健康データ、所見メモ、服薬等）で新規作成時のテンポラリIDとして使用する.
     */
    const val NEW_RECORD_ID = "__NEW__"
}
