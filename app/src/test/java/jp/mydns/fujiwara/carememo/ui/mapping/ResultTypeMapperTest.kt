package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mapping層テスト：ResultTypeMapper
 */
class ResultTypeMapperTest {

    @Test
    fun MAP_01_success_mapping() {
        assertEquals(R.string.audit_result_success, "SUCCESS".toResultLabelRes)
    }

    @Test
    fun MAP_02_dbError_mapping() {
        assertEquals(R.string.audit_result_db_error, "DB_ERROR".toResultLabelRes)
    }

    @Test
    fun MAP_03_ioError_mapping() {
        assertEquals(R.string.audit_result_io_error, "IO_ERROR".toResultLabelRes)
    }

    @Test
    fun MAP_04_formatError_mapping() {
        assertEquals(R.string.audit_result_format_error, "FORMAT_ERROR".toResultLabelRes)
    }

    @Test
    fun MAP_05_validationError_mapping() {
        assertEquals(R.string.audit_result_validation_error, "VALIDATION_ERROR".toResultLabelRes)
    }

    @Test
    fun MAP_06_otherError_mapping() {
        assertEquals(R.string.audit_result_other_error, "OTHER_ERROR".toResultLabelRes)
    }

    @Test
    fun MAP_07_unknown_mapping() {
        assertEquals(R.string.audit_result_unknown, "UNKNOWN".toResultLabelRes)
    }

    @Test
    fun MAP_08_securityError_mapping() {
        assertEquals(R.string.audit_result_security_error, "SECURITY_ERROR".toResultLabelRes)
    }

    @Test
    fun MAP_09_externalError_mapping() {
        assertEquals(R.string.audit_result_external_error, "EXTERNAL_ERROR".toResultLabelRes)
    }

    @Test
    fun MAP_10_guardSkipped_mapping() {
        assertEquals(R.string.audit_result_guard_skipped, "GUARD_SKIPPED".toResultLabelRes)
    }

    @Test
    fun MAP_11_undefined_mapping() {
        assertEquals(R.string.audit_result_unknown, "UNDEFINED".toResultLabelRes)
    }
}
