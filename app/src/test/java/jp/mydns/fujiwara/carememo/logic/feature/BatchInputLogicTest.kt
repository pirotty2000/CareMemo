package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Logic層テスト：BatchInputLogic
 */
class BatchInputLogicTest {

    private val validDateInput = BatchInputSession(
        year = "2023", month = "10", day = "27", hour = "10", minute = "00"
    )

    // region 2. バリデーション (validate / isValid)

    @Test
    fun VL_01_validate_allEmpty() {
        val input = validDateInput.copy()
        assertEquals(BatchInputValidationResult.EMPTY_ALL, BatchInputLogic.validate(input))
        assertFalse(BatchInputLogic.isValid(input))
    }

    @Test
    fun VL_02_validate_validInput() {
        val input = validDateInput.copy(weight = "60.5")
        assertEquals(BatchInputValidationResult.SUCCESS, BatchInputLogic.validate(input))
        assertTrue(BatchInputLogic.isValid(input))
    }

    @Test
    fun VL_03_validate_invalidFormat() {
        val input = validDateInput.copy(height = "160.0.1", weight = "60.0")
        assertEquals(BatchInputValidationResult.INVALID_VALUE, BatchInputLogic.validate(input))
        assertFalse(BatchInputLogic.isValid(input))
    }

    @Test
    fun VL_04_validate_outOfRange() {
        val input = validDateInput.copy(bodyTemperature = "50.0")
        assertEquals(BatchInputValidationResult.INVALID_VALUE, BatchInputLogic.validate(input))
        assertFalse(BatchInputLogic.isValid(input))
    }

    @Test
    fun VL_05_validate_invalidDate() {
        val input = validDateInput.copy(day = "32")
        assertEquals(BatchInputValidationResult.INVALID_VALUE, BatchInputLogic.validate(input))
        assertFalse(BatchInputLogic.isValid(input))
    }

    @Test
    fun VL_06_validate_heightWithoutWeight() {
        val input = validDateInput.copy(height = "170")
        assertEquals(BatchInputValidationResult.INVALID_VALUE, BatchInputLogic.validate(input))
        assertFalse(BatchInputLogic.isValid(input))
    }

    // endregion

    // region 3. カテゴリ抽出 (getEffectiveCategories)

    @Test
    fun EX_01_getEffectiveCategories_multiple() {
        val input = validDateInput.copy(weight = "60.0", bpSystolic = "120")
        val categories = BatchInputLogic.getEffectiveCategories(input)
        assertEquals(2, categories.size)
        assertTrue(categories.contains(BatchInputCategory.HEIGHT_WEIGHT))
        assertTrue(categories.contains(BatchInputCategory.VITAL))
    }

    // endregion

    // region 4. Entity 生成 (createEntities)

    @Test
    fun CP_01_createEntities_multiple() {
        val input = validDateInput.copy(weight = "60.0", bpSystolic = "120")
        val time = BatchInputLogic.calculateRecordTime(input)!!
        val entities = BatchInputLogic.createEntities("1", time, input)

        assertEquals(2, entities.size)
        assertTrue(entities.any { it is HeightAndWeight })
        assertTrue(entities.any { it is BpAndPulse })

        entities.forEach { entity ->
            val id = when (entity) {
                is HeightAndWeight -> entity.id
                is BpAndPulse -> entity.id
                else -> ""
            }
            assertFalse("ID should be generated (not NEW/empty)", IdLogic.isNew(id))
        }
    }

    // endregion

    // region 5. 変更検知 (isChanged)

    @Test
    fun CHG_01_isChanged_initial() {
        val input = validDateInput.copy(
            initialYear = "2023", initialMonth = "10", initialDay = "27", initialHour = "10", initialMinute = "00"
        )
        assertFalse(BatchInputLogic.isChanged(input))
    }

    @Test
    fun CHG_02_isChanged_withInput() {
        val input = validDateInput.copy(
            initialYear = "2023", initialMonth = "10", initialDay = "27", initialHour = "10", initialMinute = "00",
            weight = "60"
        )
        assertTrue(BatchInputLogic.isChanged(input))
    }

    // endregion
}
