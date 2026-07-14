@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * ConditionLogic のロジックを検証する単体テスト
 */
class ConditionLogicTest {

    private val mockRecords = listOf(
        ConditionAtVisit(id = 1, personId = 1, title = "定期巡回", condition = "発熱あり", author = "A", recordTime = Instant.now()),
        ConditionAtVisit(id = 2, personId = 1, title = "臨時訪問", condition = "良好です", author = "B", recordTime = Instant.now()),
        ConditionAtVisit(id = 3, personId = 1, title = "Test Title", condition = "Something test here", author = "C", recordTime = Instant.now())
    )

    // --- 検索フィルタリングのテスト (CL_FL) ---
    @Test
    fun filterRecords_空文字なら全件返す() {
        val result = ConditionLogic.filterRecords(mockRecords, "")
        assertEquals(3, result.size)
    }

    @Test
    fun filterRecords_タイトルまたは本文でフィルタされる() {
        // "発熱" は ID=1 の本文に含まれる
        val result1 = ConditionLogic.filterRecords(mockRecords, "発熱")
        assertEquals(1, result1.size)
        assertEquals(1, result1[0].id)

        // "訪問" は ID=2 のタイトルに含まれる
        val result2 = ConditionLogic.filterRecords(mockRecords, "訪問")
        assertEquals(1, result2.size)
        assertEquals(2, result2[0].id)
    }

    @Test
    fun filterRecords_ヒットしない場合() {
        val result = ConditionLogic.filterRecords(mockRecords, "ABCD")
        assertEquals(0, result.size)
    }

    @Test
    fun filterRecords_大文字小文字を区別しない() {
        val result = ConditionLogic.filterRecords(mockRecords, "TEST")
        assertEquals(1, result.size)
        assertEquals(3, result[0].id)
    }

    // --- 重複判定のテスト (CL_DP) ---
    @Test
    fun isDuplicate_新規レコードの判定() {
        val current = ConditionAtVisit(id = 0, personId = 1, title = "", condition = "", author = "", recordTime = Instant.now())
        
        // 既存なし
        assertFalse(ConditionLogic.isDuplicate(current, null))

        // 同じ時間の既存あり
        val existing = ConditionAtVisit(id = 10, personId = 1, title = "", condition = "", author = "", recordTime = Instant.now())
        assertTrue(ConditionLogic.isDuplicate(current, existing))
    }

    @Test
    fun isDuplicate_更新レコードの判定() {
        val current = ConditionAtVisit(id = 10, personId = 1, title = "", condition = "", author = "", recordTime = Instant.now())

        // 自分自身との一致
        val existingSelf = ConditionAtVisit(id = 10, personId = 1, title = "", condition = "", author = "", recordTime = Instant.now())
        assertFalse(ConditionLogic.isDuplicate(current, existingSelf))

        // 別のIDとの一致
        val existingOther = ConditionAtVisit(id = 20, personId = 1, title = "", condition = "", author = "", recordTime = Instant.now())
        assertTrue(ConditionLogic.isDuplicate(current, existingOther))
    }
}
