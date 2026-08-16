package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Logic層テスト：PersonHealthLogic
 */
class PersonHealthLogicTest {

    private val now = Instant.now()

    // region 2. 数値妥当性テスト (validate)

    @Test
    fun VAL_01_validate_heightAndWeight_success() {
        val record = HeightAndWeight(id = "1", personId = "u1", height = 170.0, weight = 60.0, recordTime = now)
        assertEquals(HealthValidationResult.SUCCESS, PersonHealthLogic.validate(record))
    }

    @Test
    fun VAL_02_validate_height_invalid() {
        val record = HeightAndWeight(id = "1", personId = "u1", height = 300.0, weight = 60.0, recordTime = now)
        assertEquals(HealthValidationResult.INVALID_VALUE, PersonHealthLogic.validate(record))
    }

    @Test
    fun VAL_03_validate_bpAndPulse_success() {
        val record = BpAndPulse(id = "1", personId = "u1", bpSystolic = 120, bpDiastolic = 80, pulse = 70, recordTime = now)
        assertEquals(HealthValidationResult.SUCCESS, PersonHealthLogic.validate(record))
    }

    @Test
    fun VAL_04_validate_glucoseAndHbA1c_success() {
        val record = GlucoseAndHbA1c(id = "1", personId = "u1", glucose = 100, hba1c = 5.5, recordTime = now)
        assertEquals(HealthValidationResult.SUCCESS, PersonHealthLogic.validate(record))
    }

    @Test
    fun VAL_05_validate_nullFields_success() {
        val record = BpAndPulse(id = "1", personId = "u1", bpSystolic = null, bpDiastolic = null, pulse = null, recordTime = now)
        assertEquals(HealthValidationResult.SUCCESS, PersonHealthLogic.validate(record))
    }

    // endregion

    // region 3. 重複判定テスト (validateDuplicate)

    @Test
    fun DUP_01_validateDuplicate_noExisting() {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val current = HeightAndWeight(id = newId, personId = "u1", height = null, weight = null, recordTime = now)
        assertEquals(HealthValidationResult.SUCCESS, PersonHealthLogic.validateDuplicate(current, null))
    }

    @Test
    fun DUP_02_validateDuplicate_collisionNew() {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val current = HeightAndWeight(id = newId, personId = "u1", height = null, weight = null, recordTime = now)
        val existing = HeightAndWeight(id = "persisted-1", personId = "u1", height = null, weight = null, recordTime = now)
        assertEquals(HealthValidationResult.DUPLICATE_TIME, PersonHealthLogic.validateDuplicate(current, existing))
    }

    @Test
    fun DUP_03_validateDuplicate_sameRecordUpdate() {
        val current = HeightAndWeight(id = "p1", personId = "u1", height = null, weight = null, recordTime = now)
        val existing = HeightAndWeight(id = "p1", personId = "u1", height = null, weight = null, recordTime = now)
        assertEquals(HealthValidationResult.SUCCESS, PersonHealthLogic.validateDuplicate(current, existing))
    }

    @Test
    fun DUP_04_validateDuplicate_differentRecordUpdate() {
        val current = HeightAndWeight(id = "p1", personId = "u1", height = null, weight = null, recordTime = now)
        val existing = HeightAndWeight(id = "p2", personId = "u1", height = null, weight = null, recordTime = now)
        assertEquals(HealthValidationResult.DUPLICATE_TIME, PersonHealthLogic.validateDuplicate(current, existing))
    }

    // endregion

    // region 4. UI入力一括評価テスト (validateInputs)

    @Test
    fun UI_01_validateInputs_heightAndWeight() {
        val values = mapOf("height" to "170", "weight" to "60")
        assertEquals(HealthInputValidationResult.SUCCESS, PersonHealthLogic.validateInputs(Category.HEIGHT_AND_WEIGHT, values))
    }

    @Test
    fun UI_02_validateInputs_vitalPartial() {
        val values = mapOf("bpSystolic" to "120", "pulse" to "70")
        assertEquals(HealthInputValidationResult.SUCCESS, PersonHealthLogic.validateInputs(Category.BP_AND_PULSE, values))
    }

    @Test
    fun UI_03_validateInputs_invalidFormat() {
        val values = mapOf("height" to "abc")
        assertEquals(HealthInputValidationResult.INVALID_FORMAT, PersonHealthLogic.validateInputs(Category.HEIGHT_AND_WEIGHT, values))
    }

    // endregion

    // region 5. Entity 生成テスト (createEntity)

    @Test
    fun CRT_01_createEntity_mapping() {
        val values = mapOf("height" to 170.0, "weight" to 60.0)
        val result = PersonHealthLogic.createEntity(Category.HEIGHT_AND_WEIGHT, "u1", "p1", now, values) as HeightAndWeight
        
        assertEquals("p1", result.id)
        assertEquals("u1", result.personId)
        assertEquals(170.0, result.height!!, 0.0)
        assertEquals(60.0, result.weight!!, 0.0)
        assertEquals(now, result.recordTime)
    }

    @Test
    fun CRT_02_createEntity_newId() {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val values = emptyMap<String, Any?>()
        val result = PersonHealthLogic.createEntity(Category.HEIGHT_AND_WEIGHT, "u1", newId, now, values) as HeightAndWeight
        
        assertNotEquals(newId, result.id)
        assertFalse(IdLogic.isNew(result.id))
    }

    @Test
    fun CRT_03_createEntity_maintainId() {
        val result = PersonHealthLogic.createEntity(Category.HEIGHT_AND_WEIGHT, "u1", "existing-id", now, emptyMap()) as HeightAndWeight
        assertEquals("existing-id", result.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun CRT_04_createEntity_unsupported() {
        PersonHealthLogic.createEntity(Category.CONDITION_AT_VISIT, "u1", "p1", now, emptyMap())
    }

    // endregion

    // region 6. 安全な型変換テスト (Safe Type Conversion)

    @Test
    fun SAF_01_getDouble_intInput() {
        val map = mapOf("val" to 100)
        assertEquals(100.0, map.getDouble("val")!!, 0.0)
    }

    @Test
    fun SAF_02_getDouble_doubleInput() {
        val map = mapOf("val" to 100.5)
        assertEquals(100.5, map.getDouble("val")!!, 0.0)
    }

    @Test
    fun SAF_03_getDouble_invalidInput() {
        val map = mapOf("val" to "abc")
        assertNull(map.getDouble("val"))
    }

    @Test
    fun SAF_04_getInt_intInput() {
        val map = mapOf("val" to 120)
        assertEquals(120, map.getInt("val"))
    }

    @Test
    fun SAF_05_getInt_doubleInput() {
        // Double が渡されても Number.toInt() により整数として取得できること
        val map = mapOf("val" to 36.5)
        assertEquals(36, map.getInt("val"))
    }

    @Test
    fun SAF_06_getInt_nullInput() {
        val map = mapOf("val" to null)
        assertNull(map.getInt("val"))
    }

    // endregion
}
