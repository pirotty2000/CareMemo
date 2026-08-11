package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import org.junit.Assert.*
import org.junit.Test

/**
 * Logic層テスト：EmergencyContactLogic
 */
class EmergencyContactLogicTest {

    // region 2. バリデーションテスト (validate / isValid)

    @Test
    fun VL_01_validate_minimalSuccess() {
        val contact = EmergencyContact(
            personId = "person-1",
            contactType = EmergencyContactType.DOCTOR.value,
            facilityName = "A病院"
        )
        val result = EmergencyContactLogic.validate(contact)
        assertEquals(EmergencyContactValidationResult.SUCCESS, result)
        assertTrue(EmergencyContactLogic.isValid(contact))
    }

    @Test
    fun VL_02_validate_fullSuccess() {
        val contact = EmergencyContact(
            personId = "person-1",
            contactType = EmergencyContactType.FAMILY.value,
            facilityName = "実家",
            personName = "田中太郎",
            phoneNumber = "09012345678"
        )
        val result = EmergencyContactLogic.validate(contact)
        assertEquals(EmergencyContactValidationResult.SUCCESS, result)
        assertTrue(EmergencyContactLogic.isValid(contact))
    }

    @Test
    fun VL_03_validate_emptyFacilityName() {
        val contact = EmergencyContact(
            personId = "person-1",
            contactType = EmergencyContactType.DOCTOR.value,
            facilityName = "  "
        )
        val result = EmergencyContactLogic.validate(contact)
        assertEquals(EmergencyContactValidationResult.EMPTY_FACILITY_NAME, result)
        assertFalse(EmergencyContactLogic.isValid(contact))
    }

    @Test
    fun VL_04_validate_facilityNameTooLong() {
        val longName = "a".repeat(AppSpecifications.MedicalContact.Validation.MAX_LENGTH_FACILITY_NAME + 1)
        val contact = EmergencyContact(
            personId = "person-1",
            contactType = EmergencyContactType.DOCTOR.value,
            facilityName = longName
        )
        assertEquals(EmergencyContactValidationResult.FACILITY_NAME_TOO_LONG, EmergencyContactLogic.validate(contact))
    }

    // endregion

    // region 3. データ正規化テスト (createSaveEntity)

    @Test
    fun NM_01_createSaveEntity_trimSpaces() {
        val raw = EmergencyContact(
            personId = "person-1",
            contactType = EmergencyContactType.DOCTOR.value,
            facilityName = "  〇〇クリニック  ",
            personName = "  田中先生  "
        )
        val saved = EmergencyContactLogic.createSaveEntity(raw)
        assertEquals("〇〇クリニック", saved.facilityName)
        assertEquals("田中先生", saved.personName)
    }

    @Test
    fun NM_02_createSaveEntity_normalizePhoneNumber() {
        val raw = EmergencyContact(
            personId = "person-1",
            contactType = EmergencyContactType.DOCTOR.value,
            facilityName = "病院",
            phoneNumber = "090-1234-5678"
        )
        val saved = EmergencyContactLogic.createSaveEntity(raw)
        assertEquals("09012345678", saved.phoneNumber)
    }

    @Test
    fun NM_03_createSaveEntity_nullifyBlankFields() {
        val raw = EmergencyContact(
            personId = "person-1",
            contactType = EmergencyContactType.DOCTOR.value,
            facilityName = "病院",
            personName = "   ", // 空白のみ
            phoneNumber = ""   // 空文字
        )
        val saved = EmergencyContactLogic.createSaveEntity(raw)
        assertNull(saved.personName)
        assertNull(saved.phoneNumber)
    }

    // endregion

    // region 4. 状態判定テスト (isChanged)

    @Test
    fun ST_01_isChanged_noChange() {
        val initial = EmergencyContact(
            personId = "person-1",
            contactType = EmergencyContactType.DOCTOR.value,
            facilityName = "病院",
            priority = 99
        )
        assertFalse(EmergencyContactLogic.isChanged(initial, initial.copy()))
    }

    @Test
    fun ST_02_isChanged_basicFieldChanged() {
        val initial = EmergencyContact(personId = "p1", contactType = "DOCTOR", facilityName = "A")
        val current = initial.copy(facilityName = "B")
        assertTrue(EmergencyContactLogic.isChanged(current, initial))
    }

    @Test
    fun ST_03_isChanged_priorityChanged() {
        val initial = EmergencyContact(personId = "p1", contactType = "DOCTOR", facilityName = "A", priority = 10)
        val current = initial.copy(priority = 11)
        assertTrue(EmergencyContactLogic.isChanged(current, initial))
    }

    // endregion

    // region 5. 初期データ生成テスト (createInitialEntity)

    @Test
    fun INI_01_createInitialEntity_defaults() {
        val personId = "user-123"
        val initial = EmergencyContactLogic.createInitialEntity(personId)
        
        assertEquals(AppSpecifications.Id.NEW_RECORD_ID, initial.id)
        assertEquals(personId, initial.personId)
        assertEquals(EmergencyContactType.DOCTOR.value, initial.contactType)
        assertEquals("", initial.facilityName)
        assertEquals(AppSpecifications.MedicalContact.Validation.DEFAULT_PRIORITY, initial.priority)
    }

    // endregion
}
