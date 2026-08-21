package jp.mydns.fujiwara.carememo.logic.feature

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.AuditLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Unit Test: AuditLogExportLogic
 */
class AuditLogExportLogicTest {

    private val context = mockk<Context>(relaxed = true)
    
    private val testLogs = listOf(
        AuditLog(
            id = 1,
            timestamp = Instant.parse("2026-08-21T10:00:00Z"),
            featureName = "PersonList",
            operation = "addPerson",
            tableName = "person_db",
            actionType = "INSERT",
            affectedId = "123",
            resultType = "SUCCESS",
            details = "Normal details"
        )
    )

    @Test
    fun CSV_01_toCsv_formatsCorrectlyWithTranslation() {
        every { context.getString(any()) } returns "翻訳済みラベル"
        
        val csv = AuditLogExportLogic.toCsv(context, testLogs)
        val lines = csv.lines().filter { it.isNotBlank() }

        // ヘッダー + データ1行
        assertEquals(2, lines.size)
        
        // ヘッダーに (Raw) と (Local) が含まれているか
        assertTrue(lines[0].contains("Feature (Raw),Feature (Local)"))
        
        // データ行に生の値と翻訳が両方含まれているか
        assertTrue(lines[1].contains("PersonList,翻訳済みラベル"))
        assertTrue(lines[1].contains("INSERT,翻訳済みラベル"))
        assertTrue(lines[1].contains("SUCCESS,翻訳済みラベル"))
    }

    @Test
    fun JSON_01_toJson_isParsable() {
        val json = AuditLogExportLogic.toJson(testLogs)
        assertTrue(json.contains("\"id\": 1"))
        assertTrue(json.contains("\"featureName\": \"SpecialFeature\""))
    }
}
