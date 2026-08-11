package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mapping層テスト：BirthEraDisplayMapper
 */
class BirthEraDisplayMapperTest {

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
}
