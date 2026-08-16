package jp.mydns.fujiwara.carememo.data.spec

/**
 * Spec：SearchSpecifications
 *
 * 【役割】
 * 利用者一覧の五十音インデックスバーで使用する各行（あ、か、さ...）の定義を管理します。
 */
object SearchSpecifications {
    /** 利用者一覧の五十音インデックス定義 */
    val KANA_GROUPS = listOf("全", "あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ", "他")
    
    const val SECTION_ALL = "全"
    const val SECTION_OTHER = "他"
}
