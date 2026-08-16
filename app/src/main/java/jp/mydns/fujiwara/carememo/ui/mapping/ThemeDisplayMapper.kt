package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.ThemeSetting

/**
 * Component：ThemeDisplayMapper
 *
 * 【役割】
 * テーマ設定（ThemeSetting）に対応する、ユーザー向けの表示名称および説明文のリソースIDを解決します。
 */
object ThemeDisplayMapper {

    /**
     * テーマ設定に対応するラベルのリソースIDを取得します。
     */
    fun getLabelRes(theme: ThemeSetting): Int = when (theme) {
        ThemeSetting.SYSTEM -> R.string.theme_system_label
        ThemeSetting.LIGHT -> R.string.theme_light_label
        ThemeSetting.DARK -> R.string.theme_dark_label
        ThemeSetting.HEALING_GREEN -> R.string.theme_healing_green_label
        ThemeSetting.SERENE_BLUE -> R.string.theme_serene_blue_label
        ThemeSetting.WARM_APRICOT -> R.string.theme_warm_apricot_label
        ThemeSetting.MIDNIGHT_NAVY -> R.string.theme_midnight_navy_label
        ThemeSetting.CLASSIC_SAND -> R.string.theme_classic_sand_label
    }

    /**
     * テーマ設定に対応する説明文のリソースIDを取得します。
     */
    fun getDescriptionRes(theme: ThemeSetting): Int = when (theme) {
        ThemeSetting.SYSTEM -> R.string.theme_system_desc
        ThemeSetting.LIGHT -> R.string.theme_light_desc
        ThemeSetting.DARK -> R.string.theme_dark_desc
        ThemeSetting.HEALING_GREEN -> R.string.theme_healing_green_desc
        ThemeSetting.SERENE_BLUE -> R.string.theme_serene_blue_desc
        ThemeSetting.WARM_APRICOT -> R.string.theme_warm_apricot_desc
        ThemeSetting.MIDNIGHT_NAVY -> R.string.theme_midnight_navy_desc
        ThemeSetting.CLASSIC_SAND -> R.string.theme_classic_sand_desc
    }
}
