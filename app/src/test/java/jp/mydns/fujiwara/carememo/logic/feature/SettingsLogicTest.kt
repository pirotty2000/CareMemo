@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.logic.feature

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SettingsLogicTest {

    @Test
    fun `FL_01_正しいZipヘッダーを判定できること`() {
        val validHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        assertTrue(SettingsLogic.isValidZipHeader(validHeader))
    }

    @Test
    fun `FL_02_不正なZipヘッダーはfalseを返すこと`() {
        val invalidHeader = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        assertFalse(SettingsLogic.isValidZipHeader(invalidHeader))
    }

    @Test
    fun `VR_01_同じバージョンは互換性あり`() {
        assertTrue(SettingsLogic.isVersionCompatible(100, 100))
    }

    @Test
    fun `VR_02_古いバックアップは互換性あり`() {
        assertTrue(SettingsLogic.isVersionCompatible(90, 100))
    }

    @Test
    fun `VR_03_新しいバックアップは互換性なし`() {
        assertFalse(SettingsLogic.isVersionCompatible(110, 100))
    }

    @Test
    fun `SP_01_空き容量判定が動作すること`() {
        // 実際のファイルシステムに依存するが、1バイト以上はあるはず
        val dir = File(".")
        assertTrue(SettingsLogic.hasAvailableSpace(dir, 1L))
        
        // 非常に大きな値を指定すれば false になる（はずだが、StatFsは実機依存のため
        // ローカルJUnit環境での挙動は環境に依存する。ここでは存在確認程度に留める）
    }
}
