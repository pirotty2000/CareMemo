@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.AuditLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AuditLogLogicTest {

    // DAOからの取得結果と同様に、新しい順（降順）で定義する
    private val testLogs = listOf(
        AuditLog(id = 3, timestamp = Instant.ofEpochMilli(3000), featureName = "Settings", operation = "op3", tableName = "t1", actionType = "C", affectedId = "3", resultType = "SUCCESS"),
        AuditLog(id = 2, timestamp = Instant.ofEpochMilli(2000), featureName = "PersonList", operation = "op2", tableName = "t2", actionType = "B", affectedId = "2", resultType = "DB_ERROR"),
        AuditLog(id = 1, timestamp = Instant.ofEpochMilli(1000), featureName = "Settings", operation = "op1", tableName = "t1", actionType = "A", affectedId = "1", resultType = "SUCCESS")
    )

    @Test
    fun `AL_LG_01_フィルタなしの場合は全件取得できること`() {
        val result = AuditLogLogic.filterAuditLogs(testLogs, null, null, false)
        assertEquals(3, result.size)
    }

    @Test
    fun `AL_LG_02_機能名でフィルタリングできること`() {
        val result = AuditLogLogic.filterAuditLogs(testLogs, "Settings", null, false)
        assertEquals(2, result.size)
        assertTrue(result.all { it.featureName == "Settings" })
    }

    @Test
    fun `AL_LG_03_結果種別でフィルタリングできること`() {
        val result = AuditLogLogic.filterAuditLogs(testLogs, null, "DB_ERROR", false)
        assertEquals(1, result.size)
        assertEquals("DB_ERROR", result[0].resultType)
    }

    @Test
    fun `AL_LG_04_機能名と結果種別の両方でフィルタリングできること`() {
        val result = AuditLogLogic.filterAuditLogs(testLogs, "Settings", "SUCCESS", false)
        assertEquals(2, result.size)
    }

    @Test
    fun `AL_LG_05_昇順での並べ替えができること`() {
        // デフォルト(false)は降順（新しい順）、trueは昇順（古い順）
        val resultAsc = AuditLogLogic.filterAuditLogs(testLogs, null, null, true)
        assertEquals(1L, resultAsc[0].id)
        assertEquals(3L, resultAsc[2].id)

        val resultDesc = AuditLogLogic.filterAuditLogs(testLogs, null, null, false)
        assertEquals(3L, resultDesc[0].id)
        assertEquals(1L, resultDesc[2].id)
    }

    @Test
    fun `AL_LG_06_該当なしの条件では空リストを返すこと`() {
        val result = AuditLogLogic.filterAuditLogs(testLogs, "NonExistent", null, false)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `AL_EX_01_存在する機能名の一覧を重複なくソートして抽出できること`() {
        val result = AuditLogLogic.extractAvailableFeatures(testLogs)
        assertEquals(2, result.size)
        assertEquals("PersonList", result[0])
        assertEquals("Settings", result[1])
    }

    @Test
    fun `AL_EX_02_存在する結果種別の一覧を重複なくソートして抽出できること`() {
        val result = AuditLogLogic.extractAvailableResults(testLogs)
        assertEquals(2, result.size)
        assertEquals("DB_ERROR", result[0])
        assertEquals("SUCCESS", result[1])
    }
}
