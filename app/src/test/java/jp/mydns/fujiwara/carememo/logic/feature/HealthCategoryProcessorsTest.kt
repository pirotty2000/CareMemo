package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Logic層テスト：HealthCategoryProcessors
 */
class HealthCategoryProcessorsTest {

    private val personId = "u1"
    private val now = Instant.now()

    // region 2. 身長・体重プロセッサ (HeightWeightProcessor)

    @Test
    fun HW_01_isEmpty_allEmpty() {
        val state = BatchInputUiState()
        assertTrue(HeightWeightProcessor.isEmpty(state))
    }

    @Test
    fun HW_02_isEmpty_hasInput() {
        val state = BatchInputUiState(height = "170")
        assertFalse(HeightWeightProcessor.isEmpty(state))
    }

    @Test
    fun HW_03_validate_success() {
        val state = BatchInputUiState(height = "170", weight = "60")
        assertEquals(HealthInputValidationResult.SUCCESS, HeightWeightProcessor.validate(state))
    }

    @Test
    fun HW_04_validate_outOfRange() {
        val state = BatchInputUiState(weight = "500")
        assertEquals(HealthInputValidationResult.OUT_OF_RANGE, HeightWeightProcessor.validate(state))
    }

    @Test
    fun HW_05_createEntity_success() {
        val state = BatchInputUiState(height = "170.5", weight = "60.2")
        val entity = HeightWeightProcessor.createEntity(personId, now, state) as HeightAndWeight
        assertEquals(170.5, entity.height!!, 0.0)
        assertEquals(60.2, entity.weight!!, 0.0)
        assertEquals(personId, entity.personId)
        assertEquals(now, entity.recordTime)
    }

    // endregion

    // region 3. バイタルプロセッサ (VitalProcessor)

    @Test
    fun VT_01_isEmpty_allEmpty() {
        val state = BatchInputUiState()
        assertTrue(VitalProcessor.isEmpty(state))
    }

    @Test
    fun VT_02_isEmpty_hasInput() {
        val state = BatchInputUiState(bpSystolic = "120")
        assertFalse(VitalProcessor.isEmpty(state))
    }

    @Test
    fun VT_03_validate_success() {
        val state = BatchInputUiState(bpSystolic = "120", bpDiastolic = "80", bodyTemperature = "36.5")
        assertEquals(HealthInputValidationResult.SUCCESS, VitalProcessor.validate(state))
    }

    @Test
    fun VT_04_validate_invalidFormat() {
        val state = BatchInputUiState(sat = "abc")
        assertEquals(HealthInputValidationResult.INVALID_FORMAT, VitalProcessor.validate(state))
    }

    @Test
    fun VT_05_createEntity_success() {
        val state = BatchInputUiState(bpSystolic = "120", sat = "98", bodyTemperature = "36.5")
        val entity = VitalProcessor.createEntity(personId, now, state) as BpAndPulse
        assertEquals(120, entity.bpSystolic)
        assertEquals(98, entity.sat)
        assertEquals(36.5, entity.bodyTemperature!!, 0.0)
    }

    // endregion

    // region 4. 血糖値プロセッサ (GlucoseProcessor)

    @Test
    fun GL_01_isEmpty_allEmpty() {
        val state = BatchInputUiState()
        assertTrue(GlucoseProcessor.isEmpty(state))
    }

    @Test
    fun GL_02_validate_success() {
        val state = BatchInputUiState(glucose = "100", hba1c = "5.5")
        assertEquals(HealthInputValidationResult.SUCCESS, GlucoseProcessor.validate(state))
    }

    @Test
    fun GL_03_createEntity_success() {
        val state = BatchInputUiState(glucose = "150", hba1c = "6.0")
        val entity = GlucoseProcessor.createEntity(personId, now, state) as GlucoseAndHbA1c
        assertEquals(150, entity.glucose)
        assertEquals(6.0, entity.hba1c!!, 0.0)
    }

    // endregion

    // region 5. 共通メソッドテスト (個別編集画面用)

    @Test
    fun CM_01_validateFromMap_success() {
        val values = mapOf("height" to "170", "weight" to "60")
        assertEquals(HealthInputValidationResult.SUCCESS, HeightWeightProcessor.validateFromMap(values))
    }

    @Test
    fun CM_02_createEntityFromValues_success() {
        val values = mapOf("bpSystolic" to 120, "pulse" to 70)
        val entity = VitalProcessor.createEntityFromValues(personId, "record-1", now, values) as BpAndPulse
        assertEquals("record-1", entity.id)
        assertEquals(120, entity.bpSystolic)
        assertEquals(70, entity.pulse)
    }

    @Test
    fun CM_03_createEntityFromValues_safeConversion() {
        // Int 型で値を渡しても、Double を期待するプロパティに正しくセットされること
        val values = mapOf(
            "height" to 180,       // Int
            "weight" to 100,       // Int
            "bodyTemperature" to 36, // Int
            "hba1c" to 10           // Int
        )

        val hwEntity = HeightWeightProcessor.createEntityFromValues(personId, "id1", now, values) as HeightAndWeight
        assertEquals(180.0, hwEntity.height!!, 0.0)
        assertEquals(100.0, hwEntity.weight!!, 0.0)

        val vitalEntity = VitalProcessor.createEntityFromValues(personId, "id2", now, values) as BpAndPulse
        assertEquals(36.0, vitalEntity.bodyTemperature!!, 0.0)

        val glucoseEntity = GlucoseProcessor.createEntityFromValues(personId, "id3", now, values) as GlucoseAndHbA1c
        assertEquals(10.0, glucoseEntity.hba1c!!, 0.0)
    }

    // endregion
}
