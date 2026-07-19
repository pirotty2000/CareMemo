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
    fun lg_01_filterAndSortLogs_noFilter() {
        val result = AuditLogLogic.filterAndSortLogs(testLogs, null, null, false)
        assertEquals(3, result.size)
    }

    @Test
    fun lg_02_filterAndSortLogs_byFeature() {
        val result = AuditLogLogic.filterAndSortLogs(testLogs, "Settings", null, false)
        assertEquals(2, result.size)
        assertTrue(result.all { it.featureName == "Settings" })
    }

    @Test
    fun lg_03_filterAndSortLogs_byResult() {
        val result = AuditLogLogic.filterAndSortLogs(testLogs, null, "DB_ERROR", false)
        assertEquals(1, result.size)
        assertEquals("DB_ERROR", result[0].resultType)
    }

    @Test
    fun lg_04_filterAndSortLogs_combined() {
        val result = AuditLogLogic.filterAndSortLogs(testLogs, "Settings", "SUCCESS", false)
        assertEquals(2, result.size)
    }

    @Test
    fun lg_05_filterAndSortLogs_ascending() {
        // デフォルト(false)は降順（新しい順）、trueは昇順（古い順）
        val resultAsc = AuditLogLogic.filterAndSortLogs(testLogs, null, null, true)
        assertEquals(1, resultAsc[0].id)
        assertEquals(3, resultAsc[2].id)

        val resultDesc = AuditLogLogic.filterAndSortLogs(testLogs, null, null, false)
        assertEquals(3, resultDesc[0].id)
        assertEquals(1, resultDesc[2].id)
    }

    @Test
    fun lg_06_filterAndSortLogs_noMatch() {
        val result = AuditLogLogic.filterAndSortLogs(testLogs, "NonExistent", null, false)
        assertTrue(result.isEmpty())
    }

    @Test
    fun ex_01_extractAvailableFeatures() {
        val result = AuditLogLogic.extractAvailableFeatures(testLogs)
        assertEquals(2, result.size)
        assertEquals("PersonList", result[0])
        assertEquals("Settings", result[1])
    }

    @Test
    fun ex_02_extractAvailableResults() {
        val result = AuditLogLogic.extractAvailableResults(testLogs)
        assertEquals(2, result.size)
        assertEquals("DB_ERROR", result[0])
        assertEquals("SUCCESS", result[1])
    }
}
