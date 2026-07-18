package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Logic層テスト：PersonHealthLogic
 */
class PersonHealthLogicTest {

    private val now = Instant.now()

    // region 1. 新規判定テスト (isNew)

    @Test
    fun NEW_01_isNew_newRecord_returnsTrue() {
        val record = HeightAndWeight(id = 0, personId = 1, height = 170.0, weight = 60.0, recordTime = now)
        assertTrue(PersonHealthLogic.isNew(record))
    }

    @Test
    fun NEW_02_isNew_existingRecord_returnsFalse() {
        val record = HeightAndWeight(id = 100, personId = 1, height = 170.0, weight = 60.0, recordTime = now)
        assertFalse(PersonHealthLogic.isNew(record))
    }

    @Test
    fun NEW_03_isNew_notHistoryRecord_returnsFalse() {
        assertFalse(PersonHealthLogic.isNew("Not a record"))
        assertFalse(PersonHealthLogic.isNew(null))
    }

    // endregion

    // region 2. 重複判定テスト (validateDuplicate)

    @Test
    fun DUP_01_validateDuplicate_noExisting_returnsSuccess() {
        val current = HeightAndWeight(id = 0, personId = 1, height = null, weight = null, recordTime = now)
        assertEquals(HealthValidationResult.SUCCESS, PersonHealthLogic.validateDuplicate(current, null))
    }

    @Test
    fun DUP_02_validateDuplicate_newRecordCollision_returnsDuplicate() {
        val current = HeightAndWeight(id = 0, personId = 1, height = null, weight = null, recordTime = now)
        val existing = HeightAndWeight(id = 1, personId = 1, height = null, weight = null, recordTime = now)
        assertEquals(HealthValidationResult.DUPLICATE_TIME, PersonHealthLogic.validateDuplicate(current, existing))
    }

    @Test
    fun DUP_03_validateDuplicate_updateSameRecord_returnsSuccess() {
        val current = HeightAndWeight(id = 1, personId = 1, height = null, weight = null, recordTime = now)
        val existing = HeightAndWeight(id = 1, personId = 1, height = null, weight = null, recordTime = now)
        assertEquals(HealthValidationResult.SUCCESS, PersonHealthLogic.validateDuplicate(current, existing))
    }

    @Test
    fun DUP_04_validateDuplicate_updateDifferentRecordCollision_returnsDuplicate() {
        val current = HeightAndWeight(id = 1, personId = 1, height = null, weight = null, recordTime = now)
        val existing = HeightAndWeight(id = 2, personId = 1, height = null, weight = null, recordTime = now)
        assertEquals(HealthValidationResult.DUPLICATE_TIME, PersonHealthLogic.validateDuplicate(current, existing))
    }

    // endregion

    // region 3. 数値妥当性テスト (validateValues)

    @Test
    fun VAL_01_validate_validHeightAndWeight_returnsSuccess() {
        val record = HeightAndWeight(id = 0, personId = 1, height = 170.0, weight = 60.0, recordTime = now)
        assertEquals(HealthValidationResult.SUCCESS, PersonHealthLogic.validate(record))
    }

    @Test
    fun VAL_02_validate_invalidHeight_returnsInvalidValue() {
        val record = HeightAndWeight(id = 0, personId = 1, height = 300.0, weight = 60.0, recordTime = now)
        assertEquals(HealthValidationResult.INVALID_VALUE, PersonHealthLogic.validate(record))
    }

    @Test
    fun VAL_03_validate_validBpAndPulse_returnsSuccess() {
        val record = BpAndPulse(id = 0, personId = 1, bpSystolic = 120, bpDiastolic = 80, pulse = 70, recordTime = now)
        assertEquals(HealthValidationResult.SUCCESS, PersonHealthLogic.validate(record))
    }

    @Test
    fun VAL_04_validate_validGlucoseAndHbA1c_returnsSuccess() {
        val record = GlucoseAndHbA1c(id = 0, personId = 1, glucose = 100, hba1c = 5.5, recordTime = now)
        assertEquals(HealthValidationResult.SUCCESS, PersonHealthLogic.validate(record))
    }

    @Test
    fun VAL_05_validate_nullTime_returnsInvalidTime() {
        // Javaの動的プロキシを使用して、Kotlinの非null型制約をバイパスしてnullを返却させる
        val record = java.lang.reflect.Proxy.newProxyInstance(
            HistoryRecord::class.java.classLoader,
            arrayOf(HistoryRecord::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getRecordTime" -> null // recordTime プロパティのゲッター
                "getId" -> 0
                "getPersonId" -> 1
                else -> null
            }
        } as HistoryRecord
        
        assertEquals(HealthValidationResult.INVALID_TIME, PersonHealthLogic.validate(record))
    }

    // endregion
}
