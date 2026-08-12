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

    private val validDateState = BatchInputUiState(
        year = "2023", month = "10", day = "27", hour = "10", minute = "00"
    )

    // region 2. バリデーション (validate / isValid)

    @Test
    fun VL_01_validate_allEmpty() {
        val state = validDateState.copy()
        assertEquals(BatchInputValidationResult.EMPTY_ALL, BatchInputLogic.validate(state))
        assertFalse(BatchInputLogic.isValid(state))
    }

    @Test
    fun VL_02_validate_validInput() {
        val state = validDateState.copy(weight = "60.5")
        assertEquals(BatchInputValidationResult.SUCCESS, BatchInputLogic.validate(state))
        assertTrue(BatchInputLogic.isValid(state))
    }

    @Test
    fun VL_03_validate_invalidFormat() {
        val state = validDateState.copy(height = "160.0.1", weight = "60.0")
        assertEquals(BatchInputValidationResult.INVALID_VALUE, BatchInputLogic.validate(state))
        assertFalse(BatchInputLogic.isValid(state))
    }

    @Test
    fun VL_04_validate_outOfRange() {
        val state = validDateState.copy(bodyTemperature = "50.0")
        assertEquals(BatchInputValidationResult.INVALID_VALUE, BatchInputLogic.validate(state))
        assertFalse(BatchInputLogic.isValid(state))
    }

    @Test
    fun VL_05_validate_invalidDate() {
        val state = validDateState.copy(day = "32")
        assertEquals(BatchInputValidationResult.INVALID_VALUE, BatchInputLogic.validate(state))
        assertFalse(BatchInputLogic.isValid(state))
    }

    @Test
    fun VL_06_validate_heightWithoutWeight() {
        val state = validDateState.copy(height = "170")
        assertEquals(BatchInputValidationResult.INVALID_VALUE, BatchInputLogic.validate(state))
        assertFalse(BatchInputLogic.isValid(state))
    }

    // endregion

    // region 3. カテゴリ抽出 (getEffectiveCategories)

    @Test
    fun EX_01_getEffectiveCategories_multiple() {
        val state = validDateState.copy(weight = "60.0", bpSystolic = "120")
        val categories = BatchInputLogic.getEffectiveCategories(state)
        assertEquals(2, categories.size)
        assertTrue(categories.contains(BatchInputCategory.HEIGHT_WEIGHT))
        assertTrue(categories.contains(BatchInputCategory.VITAL))
    }

    // endregion

    // region 4. Entity 生成 (createEntities)

    @Test
    fun CP_01_createEntities_multiple() {
        val state = validDateState.copy(weight = "60.0", bpSystolic = "120")
        val time = state.recordTime!!
        val entities = BatchInputLogic.createEntities("1", time, state)
        
        assertEquals(2, entities.size)
        assertTrue(entities.any { it is HeightAndWeight })
        assertTrue(entities.any { it is BpAndPulse })
    }

    // endregion

    // region 5. 変更検知 (isChanged)

    @Test
    fun CHG_01_isChanged_initial() {
        val state = validDateState.copy(
            initialYear = "2023", initialMonth = "10", initialDay = "27", initialHour = "10", initialMinute = "00"
        )
        assertFalse(BatchInputLogic.isChanged(state))
    }

    @Test
    fun CHG_02_isChanged_withInput() {
        val state = validDateState.copy(
            initialYear = "2023", initialMonth = "10", initialDay = "27", initialHour = "10", initialMinute = "00",
            weight = "60"
        )
        assertTrue(BatchInputLogic.isChanged(state))
    }

    // endregion
}
