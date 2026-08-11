package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mapping層テスト：ThemeDisplayMapper
 */
class ThemeDisplayMapperTest {

    // region 2. ラベルマッピングテスト (getLabelRes)

    @Test
    fun LBL_01_systemLabel() {
        assertEquals(R.string.theme_system_label, ThemeDisplayMapper.getLabelRes(ThemeSetting.SYSTEM))
    }

    @Test
    fun LBL_02_lightLabel() {
        assertEquals(R.string.theme_light_label, ThemeDisplayMapper.getLabelRes(ThemeSetting.LIGHT))
    }

    @Test
    fun LBL_03_darkLabel() {
        assertEquals(R.string.theme_dark_label, ThemeDisplayMapper.getLabelRes(ThemeSetting.DARK))
    }

    @Test
    fun LBL_04_healingGreenLabel() {
        assertEquals(R.string.theme_healing_green_label, ThemeDisplayMapper.getLabelRes(ThemeSetting.HEALING_GREEN))
    }

    @Test
    fun LBL_05_sereneBlueLabel() {
        assertEquals(R.string.theme_serene_blue_label, ThemeDisplayMapper.getLabelRes(ThemeSetting.SERENE_BLUE))
    }

    @Test
    fun LBL_06_warmApricotLabel() {
        assertEquals(R.string.theme_warm_apricot_label, ThemeDisplayMapper.getLabelRes(ThemeSetting.WARM_APRICOT))
    }

    @Test
    fun LBL_07_midnightNavyLabel() {
        assertEquals(R.string.theme_midnight_navy_label, ThemeDisplayMapper.getLabelRes(ThemeSetting.MIDNIGHT_NAVY))
    }

    @Test
    fun LBL_08_classicSandLabel() {
        assertEquals(R.string.theme_classic_sand_label, ThemeDisplayMapper.getLabelRes(ThemeSetting.CLASSIC_SAND))
    }

    // endregion

    // region 3. 説明文マッピングテスト (getDescriptionRes)

    @Test
    fun DSC_01_systemDescription() {
        assertEquals(R.string.theme_system_desc, ThemeDisplayMapper.getDescriptionRes(ThemeSetting.SYSTEM))
    }

    @Test
    fun DSC_02_lightDescription() {
        assertEquals(R.string.theme_light_desc, ThemeDisplayMapper.getDescriptionRes(ThemeSetting.LIGHT))
    }

    @Test
    fun DSC_03_darkDescription() {
        assertEquals(R.string.theme_dark_desc, ThemeDisplayMapper.getDescriptionRes(ThemeSetting.DARK))
    }

    @Test
    fun DSC_04_healingGreenDescription() {
        assertEquals(R.string.theme_healing_green_desc, ThemeDisplayMapper.getDescriptionRes(ThemeSetting.HEALING_GREEN))
    }

    @Test
    fun DSC_05_sereneBlueDescription() {
        assertEquals(R.string.theme_serene_blue_desc, ThemeDisplayMapper.getDescriptionRes(ThemeSetting.SERENE_BLUE))
    }

    @Test
    fun DSC_06_warmApricotDescription() {
        assertEquals(R.string.theme_warm_apricot_desc, ThemeDisplayMapper.getDescriptionRes(ThemeSetting.WARM_APRICOT))
    }

    @Test
    fun DSC_07_midnightNavyDescription() {
        assertEquals(R.string.theme_midnight_navy_desc, ThemeDisplayMapper.getDescriptionRes(ThemeSetting.MIDNIGHT_NAVY))
    }

    @Test
    fun DSC_08_classicSandDescription() {
        assertEquals(R.string.theme_classic_sand_desc, ThemeDisplayMapper.getDescriptionRes(ThemeSetting.CLASSIC_SAND))
    }

    // endregion
}
