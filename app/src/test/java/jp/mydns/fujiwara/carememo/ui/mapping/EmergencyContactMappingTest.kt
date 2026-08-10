package jp.mydns.fujiwara.carememo.ui.mapping

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mapping層テスト：EmergencyContactMapping
 */
class EmergencyContactMappingTest {

    // region 2. アイコンマッピングテスト (getIcon)

    @Test
    fun ICO_01_doctor_icon() {
        assertEquals(Icons.Rounded.LocalHospital, EmergencyContactMapping.getIcon("DOCTOR"))
    }

    @Test
    fun ICO_02_nursing_icon() {
        assertEquals(Icons.Rounded.MedicalServices, EmergencyContactMapping.getIcon("NURSING_STATION"))
    }

    @Test
    fun ICO_03_supportCenter_icon() {
        assertEquals(Icons.Rounded.AccountBalance, EmergencyContactMapping.getIcon("SUPPORT_CENTER"))
    }

    @Test
    fun ICO_04_caseWorker_icon() {
        assertEquals(Icons.Rounded.AssignmentInd, EmergencyContactMapping.getIcon("CASE_WORKER"))
    }

    @Test
    fun ICO_05_family_icon() {
        assertEquals(Icons.Rounded.FamilyRestroom, EmergencyContactMapping.getIcon("FAMILY"))
    }

    @Test
    fun ICO_06_other_icon() {
        assertEquals(Icons.Rounded.ContactPage, EmergencyContactMapping.getIcon("OTHER"))
    }

    @Test
    fun ICO_07_unknown_icon() {
        assertEquals(Icons.Rounded.ContactPage, EmergencyContactMapping.getIcon("UNKNOWN"))
    }

    // endregion

    // region 3. 電話番号整形テスト (formatPhoneNumber)

    @Test
    fun FMT_01_format_mobile11Digits() {
        assertEquals("090-1234-5678", EmergencyContactMapping.formatPhoneNumber("09012345678"))
    }

    @Test
    fun FMT_02_format_tokyoFixed() {
        assertEquals("03-1234-5678", EmergencyContactMapping.formatPhoneNumber("0312345678"))
    }

    @Test
    fun FMT_03_format_osakaFixed() {
        assertEquals("06-1234-5678", EmergencyContactMapping.formatPhoneNumber("0612345678"))
    }

    @Test
    fun FMT_04_format_generalFixed() {
        assertEquals("048-123-4567", EmergencyContactMapping.formatPhoneNumber("0481234567"))
    }

    @Test
    fun FMT_05_format_dirtyInput() {
        assertEquals("090-1234-5678", EmergencyContactMapping.formatPhoneNumber("090-1234-5678"))
    }

    @Test
    fun FMT_06_format_unsupportedLength() {
        assertEquals("12345", EmergencyContactMapping.formatPhoneNumber("12345"))
    }

    @Test
    fun FMT_07_format_emptyOrNull() {
        assertNull(EmergencyContactMapping.formatPhoneNumber(""))
        assertNull(EmergencyContactMapping.formatPhoneNumber(null))
    }

    // endregion
}
