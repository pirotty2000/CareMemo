package jp.mydns.fujiwara.carememo.data.spec

/**
 * Spec：IdSpecifications
 *
 * 【役割】
 * ID 管理に関連するシステム予約語（新規レコードを示す ID 等）を定義します。
 */
object IdSpecifications {
    /**
     * 新規レコードであることを示すシステム共通の識別子.
     * 各機能（健康データ、所見メモ、服薬等）で新規作成時のテンポラリIDとして使用する.
     */
    const val NEW_RECORD_ID = "__NEW__"
}
