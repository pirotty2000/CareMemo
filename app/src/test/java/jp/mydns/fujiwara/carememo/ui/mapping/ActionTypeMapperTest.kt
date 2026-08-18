package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mapping層テスト：ActionTypeMapper
 */
class ActionTypeMapperTest {

    @Test
    fun MAP_01_insert_mapping() {
        assertEquals(R.string.audit_action_insert, "INSERT".toActionLabelRes)
    }

    @Test
    fun MAP_02_update_mapping() {
        assertEquals(R.string.audit_action_update, "UPDATE".toActionLabelRes)
    }

    @Test
    fun MAP_03_delete_mapping() {
        assertEquals(R.string.audit_action_delete, "DELETE".toActionLabelRes)
    }

    @Test
    fun MAP_04_logicalDelete_mapping() {
        assertEquals(R.string.audit_action_logical_delete, "LOGICAL_DELETE".toActionLabelRes)
    }

    @Test
    fun MAP_05_restore_mapping() {
        assertEquals(R.string.audit_action_restore, "RESTORE".toActionLabelRes)
    }

    @Test
    fun MAP_06_permanentDelete_mapping() {
        assertEquals(R.string.audit_action_permanent_delete, "PERMANENT_DELETE".toActionLabelRes)
    }

    @Test
    fun MAP_07_clearAllArchived_mapping() {
        assertEquals(R.string.audit_action_clear_all, "CLEAR_ALL_ARCHIVED".toActionLabelRes)
    }

    @Test
    fun MAP_08_info_mapping() {
        assertEquals(R.string.audit_action_info, "INFO".toActionLabelRes)
    }

    @Test
    fun MAP_09_error_mapping() {
        assertEquals(R.string.audit_action_error, "ERROR".toActionLabelRes)
    }

    @Test
    fun MAP_10_unknown_mapping() {
        assertEquals(0, "UNKNOWN".toActionLabelRes)
    }
}
