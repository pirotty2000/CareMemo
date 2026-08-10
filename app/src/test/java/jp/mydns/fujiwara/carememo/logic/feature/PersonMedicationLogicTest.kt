package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

/**
 * Logic層テスト：PersonMedicationLogic
 */
class PersonMedicationLogicTest {

    private val now = Instant.now()

    // region 2. UI状態変換テスト (groupRecordsByDate)

    @Test
    fun UI_01_groupRecordsByDate_groupsCorrectly() {
        val records = listOf(
            MedicationRecord(id = "1", personId = "u1", dosageDate = "2023-11-01", timeSlot = 0, status = 1, recordTime = now),
            MedicationRecord(id = "2", personId = "u1", dosageDate = "2023-11-01", timeSlot = 1, status = 2, recordTime = now),
            MedicationRecord(id = "3", personId = "u1", dosageDate = "2023-11-02", timeSlot = 0, status = 1, recordTime = now)
        )

        val result = PersonMedicationLogic.groupRecordsByDate(records)

        assertEquals(2, result.size)
        assertEquals(2, result["2023-11-01"]?.size)
        assertEquals(1, result["2023-11-02"]?.size)
        assertEquals("1", result["2023-11-01"]?.get(0)?.id)
    }

    @Test
    fun UI_02_groupRecordsByDate_emptyList() {
        val result = PersonMedicationLogic.groupRecordsByDate(emptyList())
        assertTrue(result.isEmpty())
    }

    // endregion

    // region 3. UI状態テスト (UiState)

    @Test
    fun STA_01_initialState() {
        val state = PersonMedicationUiState()
        assertEquals(Category.MEDICATION, state.currentCategory)
        assertEquals(YearMonth.now(), state.selectedMonth)
        assertTrue(state.monthlyRecords.isEmpty())
        assertTrue(state.recordsByDate.isEmpty())
        assertTrue(state.allRecords.isEmpty())
        assertFalse(state.isLoading)
    }

    // endregion
}
