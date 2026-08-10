package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Logic層テスト：IdLogic
 */
class IdLogicTest {

    @Test
    fun ID_01_isNew_newRecordId() {
        assertTrue(IdLogic.isNew(AppSpecifications.Id.NEW_RECORD_ID))
    }

    @Test
    fun ID_02_isNew_empty() {
        assertTrue(IdLogic.isNew(""))
    }

    @Test
    fun ID_03_isNew_null() {
        assertTrue(IdLogic.isNew(null))
    }

    @Test
    fun ID_04_isNew_persistedId() {
        assertFalse(IdLogic.isNew("some-uuid-12345"))
    }
}
