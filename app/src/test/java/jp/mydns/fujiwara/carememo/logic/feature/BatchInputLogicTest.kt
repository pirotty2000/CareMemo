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

    // --- Validation (validate / isValid) ---

    @Test
    fun vl01_all_empty_invalid() {
        val state = BatchInputUiState()
        assertEquals(BatchInputValidationResult.EMPTY_ALL, BatchInputLogic.validate(state))
        assertFalse(BatchInputLogic.isValid(state))
    }

    @Test
    fun vl02_partial_valid_success() {
        val state = BatchInputUiState(weight = "60.5")
        assertEquals(BatchInputValidationResult.SUCCESS, BatchInputLogic.validate(state))
        assertTrue(BatchInputLogic.isValid(state))
    }

    @Test
    fun vl03_invalid_format_invalid() {
        val state = BatchInputUiState(height = "160.0.1", weight = "60.0")
        assertEquals(BatchInputValidationResult.INVALID_VALUE, BatchInputLogic.validate(state))
        assertFalse(BatchInputLogic.isValid(state))
    }

    @Test
    fun vl04_out_of_range_invalid() {
        val state = BatchInputUiState(bodyTemperature = "50.0") // Too high
        assertEquals(BatchInputValidationResult.INVALID_VALUE, BatchInputLogic.validate(state))
        assertFalse(BatchInputLogic.isValid(state))
    }

    // --- Category Extraction (getEffectiveCategories) ---

    @Test
    fun ex01_extract_multiple_categories() {
        val state = BatchInputUiState(weight = "60.0", bpSystolic = "120")
        val categories = BatchInputLogic.getEffectiveCategories(state)
        assertEquals(2, categories.size)
        assertTrue(categories.contains(BatchInputCategory.HEIGHT_WEIGHT))
        assertTrue(categories.contains(BatchInputCategory.VITAL))
    }

    @Test
    fun ex02_extract_single_category() {
        val state = BatchInputUiState(glucose = "100")
        val categories = BatchInputLogic.getEffectiveCategories(state)
        assertEquals(1, categories.size)
        assertEquals(BatchInputCategory.GLUCOSE, categories[0])
    }

    @Test
    fun ex03_extract_empty_when_all_blank() {
        val state = BatchInputUiState()
        val categories = BatchInputLogic.getEffectiveCategories(state)
        assertTrue(categories.isEmpty())
    }

    // --- Entity Creation (createEntities) ---

    @Test
    fun cp01_create_multiple_entities() {
        val state = BatchInputUiState(weight = "60.0", bpSystolic = "120")
        val entities = BatchInputLogic.createEntities(1, Instant.now(), state)
        
        assertEquals(2, entities.size)
        assertTrue(entities[0] is HeightAndWeight)
        assertTrue(entities[1] is BpAndPulse)
    }

    @Test
    fun cp02_create_single_entity() {
        val state = BatchInputUiState(glucose = "100")
        val entities = BatchInputLogic.createEntities(1, Instant.now(), state)
        
        assertEquals(1, entities.size)
        assertTrue(entities[0] is GlucoseAndHbA1c)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cp03_invalid_input_throws_exception() {
        val state = BatchInputUiState(height = "abc")
        BatchInputLogic.createEntities(1, Instant.now(), state)
    }
}
