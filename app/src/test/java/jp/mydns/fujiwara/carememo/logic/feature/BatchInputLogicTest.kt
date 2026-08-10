package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Logic層テスト：BatchInputLogic
 */
class BatchInputLogicTest {

    // region 2. バリデーション (validate / isValid)

    @Test
    fun VL_01_validate_allEmpty() {
        val state = BatchInputUiState()
        assertEquals(BatchInputValidationResult.EMPTY_ALL, BatchInputLogic.validate(state))
        assertFalse(BatchInputLogic.isValid(state))
    }

    @Test
    fun VL_02_validate_validInput() {
        val state = BatchInputUiState(weight = "60.5")
        assertEquals(BatchInputValidationResult.SUCCESS, BatchInputLogic.validate(state))
        assertTrue(BatchInputLogic.isValid(state))
    }

    @Test
    fun VL_03_validate_invalidFormat() {
        val state = BatchInputUiState(height = "160.0.1", weight = "60.0")
        assertEquals(BatchInputValidationResult.INVALID_VALUE, BatchInputLogic.validate(state))
        assertFalse(BatchInputLogic.isValid(state))
    }

    @Test
    fun VL_04_validate_outOfRange() {
        val state = BatchInputUiState(bodyTemperature = "50.0")
        assertEquals(BatchInputValidationResult.INVALID_VALUE, BatchInputLogic.validate(state))
        assertFalse(BatchInputLogic.isValid(state))
    }

    // endregion

    // region 3. カテゴリ抽出 (getEffectiveCategories)

    @Test
    fun EX_01_getEffectiveCategories_multiple() {
        val state = BatchInputUiState(weight = "60.0", bpSystolic = "120")
        val categories = BatchInputLogic.getEffectiveCategories(state)
        assertEquals(2, categories.size)
        assertTrue(categories.contains(BatchInputCategory.HEIGHT_WEIGHT))
        assertTrue(categories.contains(BatchInputCategory.VITAL))
    }

    @Test
    fun EX_02_getEffectiveCategories_single() {
        val state = BatchInputUiState(glucose = "100")
        val categories = BatchInputLogic.getEffectiveCategories(state)
        assertEquals(1, categories.size)
        assertEquals(BatchInputCategory.GLUCOSE, categories[0])
    }

    @Test
    fun EX_03_getEffectiveCategories_empty() {
        val state = BatchInputUiState()
        val categories = BatchInputLogic.getEffectiveCategories(state)
        assertTrue(categories.isEmpty())
    }

    // endregion

    // region 4. Entity 生成 (createEntities)

    @Test
    fun CP_01_createEntities_multiple() {
        val state = BatchInputUiState(weight = "60.0", bpSystolic = "120")
        val entities = BatchInputLogic.createEntities("1", Instant.now(), state)
        
        assertEquals(2, entities.size)
        assertTrue(entities.any { it is HeightAndWeight })
        assertTrue(entities.any { it is BpAndPulse })
    }

    @Test
    fun CP_02_createEntities_single() {
        val state = BatchInputUiState(glucose = "100")
        val entities = BatchInputLogic.createEntities("1", Instant.now(), state)
        
        assertEquals(1, entities.size)
        assertTrue(entities[0] is GlucoseAndHbA1c)
    }

    @Test(expected = IllegalArgumentException::class)
    fun CP_03_createEntities_invalidData() {
        val state = BatchInputUiState(height = "abc")
        BatchInputLogic.createEntities("1", Instant.now(), state)
    }

    // endregion

    // region 5. 変更検知 (isChanged)

    @Test
    fun CHG_01_isChanged_initial() {
        val now = Instant.now()
        val state = BatchInputUiState(recordTime = now, initialRecordTime = now)
        assertFalse(BatchInputLogic.isChanged(state))
    }

    @Test
    fun CHG_02_isChanged_withInput() {
        val now = Instant.now()
        val state = BatchInputUiState(weight = "60", recordTime = now, initialRecordTime = now)
        assertTrue(BatchInputLogic.isChanged(state))
    }

    @Test
    fun CHG_03_isChanged_timeChanged() {
        val now = Instant.now()
        val later = now.plusSeconds(60)
        val state = BatchInputUiState(recordTime = later, initialRecordTime = now)
        assertTrue(BatchInputLogic.isChanged(state))
    }

    // endregion
}
