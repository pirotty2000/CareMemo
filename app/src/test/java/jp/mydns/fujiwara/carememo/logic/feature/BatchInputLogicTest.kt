@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BatchInputLogicTest {

    @Test
    fun `BI_VL_01_全項目が空なら無効`() {
        assertFalse(BatchInputLogic.isValid(BatchInputUiState()))
    }

    @Test
    fun `BI_VL_03_体重のみ入力されていれば有効`() {
        val state = BatchInputUiState(weight = "60.5")
        assertTrue(BatchInputLogic.isValid(state))
    }

    @Test
    fun `BI_VL_02_身長のみ入力で体重が空なら無効`() {
        val state = BatchInputUiState(height = "160.0", weight = "")
        assertFalse(BatchInputLogic.isValid(state))
    }

    @Test
    fun `BI_CP_01_入力されたカテゴリのみEntityが生成されること`() {
        val state = BatchInputUiState(weight = "60.0", bpSystolic = "120")
        val entities = BatchInputLogic.createEntities(1, Instant.now(), state)
        
        assertEquals(2, entities.size)
        assertTrue(entities[0] is HeightAndWeight)
        assertTrue(entities[1] is BpAndPulse)
    }

    @Test
    fun `BI_CP_02_血糖値のみ入力された場合`() {
        val state = BatchInputUiState(glucose = "100")
        val entities = BatchInputLogic.createEntities(1, Instant.now(), state)
        
        assertEquals(1, entities.size)
        assertTrue(entities[0] is GlucoseAndHbA1c)
    }
}
