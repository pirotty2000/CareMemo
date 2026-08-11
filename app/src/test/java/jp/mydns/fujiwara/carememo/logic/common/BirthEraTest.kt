package jp.mydns.fujiwara.carememo.logic.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Logic層テスト：BirthEra
 */
class BirthEraTest {

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
}
