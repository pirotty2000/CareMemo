package jp.mydns.fujiwara.carememo.logic.feature

import org.junit.Assert.*
import org.junit.Test

/**
 * Logic層テスト：SettingsLogic
 */
class SettingsLogicTest {

    // region 2. ファイル形式判定テスト (validateImportFormat)

    @Test
    fun FMT_01_validateImportFormat_validZip() {
        val header = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())
        assertEquals(ImportValidationResult.SUCCESS, SettingsLogic.validateImportFormat(header))
    }

    @Test
    fun FMT_02_validateImportFormat_shortHeader() {
        val header = byteArrayOf(0x50.toByte(), 0x4B.toByte())
        assertEquals(ImportValidationResult.NOT_A_ZIP, SettingsLogic.validateImportFormat(header))
    }

    @Test
    fun FMT_03_validateImportFormat_invalidHeader() {
        val header = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())
        assertEquals(ImportValidationResult.NOT_A_ZIP, SettingsLogic.validateImportFormat(header))
    }

    // endregion

    // region 3. バージョン互換性テスト (validateVersion)

    @Test
    fun VER_01_validateVersion_same() {
        assertEquals(ImportValidationResult.SUCCESS, SettingsLogic.validateVersion(100, 100, false))
    }

    @Test
    fun VER_02_validateVersion_olderBackup() {
        assertEquals(ImportValidationResult.SUCCESS, SettingsLogic.validateVersion(90, 100, false))
    }

    @Test
    fun VER_03_validateVersion_newerBackup() {
        assertEquals(ImportValidationResult.INCOMPATIBLE, SettingsLogic.validateVersion(110, 100, false))
    }

    @Test
    fun VER_04_validateVersion_forcedByDevMode() {
        assertEquals(ImportValidationResult.SUCCESS, SettingsLogic.validateVersion(110, 100, true))
    }

    // endregion

    // region 4. 容量・書き込み判定テスト (validateStorageSpace)

    @Test
    fun SPC_01_validateStorageSpace_enough() {
        assertEquals(StorageValidationResult.SUCCESS, SettingsLogic.validateStorageSpace(100L, 50L))
    }

    @Test
    fun SPC_02_validateStorageSpace_insufficient() {
        assertEquals(StorageValidationResult.INSUFFICIENT_SPACE, SettingsLogic.validateStorageSpace(40L, 50L))
    }

    @Test
    fun SPC_03_validateStorageSpace_boundary() {
        assertEquals(StorageValidationResult.SUCCESS, SettingsLogic.validateStorageSpace(50L, 50L))
    }

    // endregion

    // region 5. 開発者モード判定テスト (shouldEnableDeveloperMode)

    @Test
    fun DEV_01_shouldEnableDeveloperMode_below() {
        assertFalse(SettingsLogic.shouldEnableDeveloperMode(6))
    }

    @Test
    fun DEV_02_shouldEnableDeveloperMode_reached() {
        assertTrue(SettingsLogic.shouldEnableDeveloperMode(7))
    }

    @Test
    fun DEV_03_shouldEnableDeveloperMode_above() {
        assertTrue(SettingsLogic.shouldEnableDeveloperMode(10))
    }

    // endregion

    // region 6. 表示ラベル生成テスト (getTimeoutLabel / getRetentionLabel)

    @Test
    fun LBL_01_getTimeoutLabel_zero() {
        assertEquals("即時", SettingsLogic.getTimeoutLabel(0))
    }

    @Test
    fun LBL_02_getTimeoutLabel_minusOne() {
        assertEquals("ロックしない", SettingsLogic.getTimeoutLabel(-1))
    }

    @Test
    fun LBL_03_getTimeoutLabel_value() {
        assertEquals("5分", SettingsLogic.getTimeoutLabel(5))
    }

    @Test
    fun LBL_04_getRetentionLabel_zero() {
        assertEquals("残さない", SettingsLogic.getRetentionLabel(0))
    }

    @Test
    fun LBL_05_getRetentionLabel_seven() {
        assertEquals("1週間", SettingsLogic.getRetentionLabel(7))
    }

    @Test
    fun LBL_06_getRetentionLabel_thirty() {
        assertEquals("1ヶ月", SettingsLogic.getRetentionLabel(30))
    }

    @Test
    fun LBL_07_getRetentionLabel_otherValue() {
        assertEquals("100日間", SettingsLogic.getRetentionLabel(100))
    }

    // endregion
}
