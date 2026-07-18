package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.ui.mapping.BirthEraDisplayMapper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Logic層テスト：BirthEra
 */
class BirthEraTest {

    // region 1. 元号定義テスト (Enum)

    @Test
    fun ERA_01_enum_values_exist() {
        // 定義された元号がすべて存在することを確認
        val eras = BirthEra.entries.toTypedArray()
        assertEquals(4, eras.size)
        assertEquals(BirthEra.AD, eras[0])
        assertEquals(BirthEra.SHOWA, eras[1])
        assertEquals(BirthEra.HEISEI, eras[2])
        assertEquals(BirthEra.REIWA, eras[3])
    }

    // endregion

    // region 2. 表示名マッピングテスト (BirthEraDisplayMapper)

    @Test
    fun MAP_01_ad_mapping() {
        assertEquals(R.string.common_era_ad, BirthEraDisplayMapper.getDisplayNameRes(BirthEra.AD))
    }

    @Test
    fun MAP_02_showa_mapping() {
        assertEquals(R.string.common_era_showa, BirthEraDisplayMapper.getDisplayNameRes(BirthEra.SHOWA))
    }

    @Test
    fun MAP_03_heisei_mapping() {
        assertEquals(R.string.common_era_heisei, BirthEraDisplayMapper.getDisplayNameRes(BirthEra.HEISEI))
    }

    @Test
    fun MAP_04_reiwa_mapping() {
        assertEquals(R.string.common_era_reiwa, BirthEraDisplayMapper.getDisplayNameRes(BirthEra.REIWA))
    }

    // endregion
}
