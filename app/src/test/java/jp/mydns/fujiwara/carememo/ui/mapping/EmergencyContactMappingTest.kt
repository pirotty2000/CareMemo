package jp.mydns.fujiwara.carememo.ui.mapping

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mapping層テスト：EmergencyContactMapping
 *
 * 電話番号整形が PhoneLogic へ正しく委譲されていることを検証します。
 */
class EmergencyContactMappingTest {

    @Test
    fun EM_01_formatPhoneNumber_delegation() {
        // PhoneLogic を介して 0120 が 4-3-3 になることを確認
        assertEquals("0120-123-456", EmergencyContactMapping.formatPhoneNumber("0120123456"))
        assertEquals("03-1234-5678", EmergencyContactMapping.formatPhoneNumber("0312345678"))
    }

    @Test
    fun EM_02_formatPhoneNumber_null_or_blank() {
        assertEquals(null, EmergencyContactMapping.formatPhoneNumber(null))
        assertEquals(null, EmergencyContactMapping.formatPhoneNumber(""))
    }
}
