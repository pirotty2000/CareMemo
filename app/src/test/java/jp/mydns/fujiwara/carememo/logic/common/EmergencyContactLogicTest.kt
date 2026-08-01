package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import org.junit.Assert.*
import org.junit.Test

/**
 * EmergencyContactLogic のユニットテスト
 */
class EmergencyContactLogicTest {

    @Test
    fun `validate - 正常系：最小構成`() {
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
    fun `validate - 異常系：施設名が空`() {
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
    fun `createSaveEntity - 余計な空白がトリミングされること`() {
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
    fun `createSaveEntity - 電話番号から記号が除去されること`() {
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
    fun `isChanged - 変更が正しく検知されること`() {
        val initial = EmergencyContact(
            personId = "person-1",
            contactType = EmergencyContactType.DOCTOR.value,
            facilityName = "病院",
            priority = 99
        )
        
        // 変更なし
        assertFalse(EmergencyContactLogic.isChanged(initial, initial.copy()))
        
        // 施設名変更
        assertTrue(EmergencyContactLogic.isChanged(initial, initial.copy(facilityName = "新しい病院")))
        
        // 優先度変更
        assertTrue(EmergencyContactLogic.isChanged(initial, initial.copy(priority = 1)))
    }
}
