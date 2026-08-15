package jp.mydns.fujiwara.carememo.data

/**
 * Data：ThemeSetting
 *
 * 【役割】
 * アプリケーションの配色テーマ設定を定義する Enum です。
 * システム連動（SYSTEM）、標準色（LIGHT, DARK）、および独自定義のカラーパレットを管理します。
 */
enum class ThemeSetting {
    SYSTEM,
    LIGHT,
    DARK,
    HEALING_GREEN,
    SERENE_BLUE,
    WARM_APRICOT,
    MIDNIGHT_NAVY,
    CLASSIC_SAND
}
