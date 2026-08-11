package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Logic層テスト：ConditionLogic
 */
class ConditionLogicTest {

    private val now = Instant.now()
    private val mockRecords = listOf(
        ConditionAtVisit(id = "1", personId = "1", title = "朝の様子", condition = "元気です", author = "担当A", recordTime = now),
        ConditionAtVisit(id = "2", personId = "1", title = "昼の訪問", condition = "test message", author = "担当B", recordTime = now.plusSeconds(3600))
    )

    // region 2. 検索フィルタリングテスト (filterRecords)

    @Test
    fun FLT_01_filterRecords_noFilter() {
        val result = ConditionLogic.filterRecords(mockRecords, "")
        assertEquals(2, result.size)
    }

    @Test
    fun FLT_02_filterRecords_titleMatch() {
        val result = ConditionLogic.filterRecords(mockRecords, "朝の様子")
        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    @Test
    fun FLT_03_filterRecords_conditionMatch() {
        val result = ConditionLogic.filterRecords(mockRecords, "元気です")
        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    @Test
    fun FLT_04_filterRecords_ignoreCase() {
        val result = ConditionLogic.filterRecords(mockRecords, "TEST")
        assertEquals(1, result.size)
        assertEquals("2", result[0].id)
    }

    @Test
    fun FLT_05_filterRecords_noMatch() {
        val result = ConditionLogic.filterRecords(mockRecords, "存在しない")
        assertTrue(result.isEmpty())
    }

    // endregion

    // region 3. 重複判定テスト (validateDuplicate)

    @Test
    fun DUP_01_validateDuplicate_newNoExisting() {
        val current = ConditionAtVisit(id = "", personId = "1", title = "", condition = "", author = "", recordTime = now)
        assertEquals(ConditionValidationResult.SUCCESS, ConditionLogic.validateDuplicate(current, null))
    }

    @Test
    fun DUP_02_validateDuplicate_newWithExisting() {
        val current = ConditionAtVisit(id = "", personId = "1", title = "", condition = "", author = "", recordTime = now)
        val existing = ConditionAtVisit(id = "10", personId = "1", title = "", condition = "", author = "", recordTime = now)
        assertEquals(ConditionValidationResult.DUPLICATE_TIME, ConditionLogic.validateDuplicate(current, existing))
    }

    @Test
    fun DUP_03_validateDuplicate_updateSame() {
        val current = ConditionAtVisit(id = "10", personId = "1", title = "", condition = "", author = "", recordTime = now)
        val existing = ConditionAtVisit(id = "10", personId = "1", title = "", condition = "", author = "" , recordTime = now)
        assertEquals(ConditionValidationResult.SUCCESS, ConditionLogic.validateDuplicate(current, existing))
    }

    @Test
    fun DUP_04_validateDuplicate_updateDifferent() {
        val current = ConditionAtVisit(id = "10", personId = "1", title = "", condition = "", author = "", recordTime = now)
        val existing = ConditionAtVisit(id = "20", personId = "1", title = "", condition = "", author = "", recordTime = now)
        assertEquals(ConditionValidationResult.DUPLICATE_TIME, ConditionLogic.validateDuplicate(current, existing))
    }

    // endregion
}
