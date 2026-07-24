package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.MedicationRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Logic層テスト：MedicationLogic
 */
class MedicationLogicTest {

    private val now = Instant.now()

    // region 1. カレンダー生成テスト (getCalendarDays)

    @Test
    fun CAL_01_getCalendarDays_wednesday_start() {
        val ym = YearMonth.of(2023, 11) // 11/1は水曜(3)
        val days = MedicationLogic.getCalendarDays(ym)
        
        assertEquals(30 + 3, days.size)
        assertNull(days[0])
        assertNull(days[1])
        assertNull(days[2])
        assertEquals(LocalDate.of(2023, 11, 1), days[3])
    }

    @Test
    fun CAL_02_getCalendarDays_leap_year() {
        val ym = YearMonth.of(2024, 2) // 2/1は木曜(4)
        val days = MedicationLogic.getCalendarDays(ym)
        
        assertEquals(29 + 4, days.size)
        assertEquals(LocalDate.of(2024, 2, 29), days.last())
    }

    @Test
    fun CAL_03_getCalendarDays_sunday_start() {
        val ym = YearMonth.of(2023, 10) // 10/1は日曜(0)
        val days = MedicationLogic.getCalendarDays(ym)
        
        assertEquals(31, days.size)
        assertEquals(LocalDate.of(2023, 10, 1), days[0])
    }

    // endregion

    // region 2. 同期アクション判定テスト (determineSyncActions)

    @Test
    fun SYN_01_determineSyncActions_insert() {
        val current = emptyList<MedicationRecord>()
        val input = listOf(
            MedicationRecord(id = "", personId = "1", dosageDate = "2023-11-01", timeSlot = 0, status = 2, recordTime = now),
            null, null, null
        )
        
        val actions = MedicationLogic.determineSyncActions(current, input)
        assertEquals(4, actions.size)
        assertTrue(actions[0] is SyncAction.Insert)
        assertEquals(SyncAction.None, actions[1])
    }

    @Test
    fun SYN_02_determineSyncActions_update() {
        val current = listOf(
            MedicationRecord(id = "10", personId = "1", dosageDate = "2023-11-01", timeSlot = 0, status = 1, recordTime = now)
        )
        val input = listOf(
            MedicationRecord(id = "10", personId = "1", dosageDate = "2023-11-01", timeSlot = 0, status = 2, recordTime = now),
            null, null, null
        )
        
        val actions = MedicationLogic.determineSyncActions(current, input)
        assertTrue(actions[0] is SyncAction.Insert) // ステータスが変わったので再Insert
    }

    @Test
    fun SYN_03_determineSyncActions_delete() {
        val current = listOf(
            MedicationRecord(id = "10", personId = "1", dosageDate = "2023-11-01", timeSlot = 0, status = 2, recordTime = now)
        )
        val input = listOf(null, null, null, null)
        
        val actions = MedicationLogic.determineSyncActions(current, input)
        assertTrue(actions[0] is SyncAction.Delete)
    }

    @Test
    fun SYN_04_determineSyncActions_none() {
        val current = listOf(
            MedicationRecord(id = "10", personId = "1", dosageDate = "2023-11-01", timeSlot = 0, status = 2, recordTime = now)
        )
        val input = listOf(
            MedicationRecord(id = "10", personId = "1", dosageDate = "2023-11-01", timeSlot = 0, status = 2, recordTime = now),
            null, null, null
        )
        
        val actions = MedicationLogic.determineSyncActions(current, input)
        assertEquals(SyncAction.None, actions[0])
    }

    // endregion

    // region 3. 入力バリデーションテスト (validateMedicationInput)

    @Test
    fun VAL_01_validateMedication_success() {
        val record = MedicationRecord(personId = "1", dosageDate = "2023-10-27", timeSlot = 0, status = 1, recordTime = now)
        assertEquals(MedicationValidationResult.SUCCESS, MedicationLogic.validateMedication(record, LocalDate.of(2023, 10, 27)))
    }

    @Test
    fun VAL_02_validateMedication_future() {
        val record = MedicationRecord(personId = "1", dosageDate = "2023-10-28", timeSlot = 0, status = 1, recordTime = now)
        assertEquals(MedicationValidationResult.FUTURE_DATE_NOT_ALLOWED, MedicationLogic.validateMedication(record, LocalDate.of(2023, 10, 27)))
    }

    @Test
    fun VAL_03_validateMedication_invalidStatus() {
        val record = MedicationRecord(personId = "1", dosageDate = "2023-10-27", timeSlot = 0, status = 9, recordTime = now)
        assertEquals(MedicationValidationResult.INVALID_STATUS, MedicationLogic.validateMedication(record))
    }

    // endregion
}
