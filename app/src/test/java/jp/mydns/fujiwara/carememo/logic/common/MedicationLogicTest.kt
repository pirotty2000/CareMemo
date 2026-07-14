@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.MedicationRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * MedicationLogic のロジックを検証する単体テスト
 */
class MedicationLogicTest {

    // --- カレンダー生成のテスト (ML_CL) ---
    @Test
    fun getCalendarDays_水曜開始の月_2023年11月() {
        val ym = YearMonth.of(2023, 11)
        val days = MedicationLogic.getCalendarDays(ym)

        // 11/1は水曜日(3)なので、前に3つのnullが入る。11月は30日まで。
        // 合計: 3 + 30 = 33
        assertEquals(33, days.size)
        assertNull(days[0])
        assertNull(days[2])
        assertEquals(LocalDate.of(2023, 11, 1), days[3])
        assertEquals(LocalDate.of(2023, 11, 30), days[32])
    }

    @Test
    fun getCalendarDays_うるう年_2024年2月() {
        val ym = YearMonth.of(2024, 2)
        val days = MedicationLogic.getCalendarDays(ym)

        // 2024/2/1は木曜日(4)なので、前に4つのnull。29日まで。
        // 合計: 4 + 29 = 33
        assertEquals(33, days.size)
        assertNull(days[3])
        assertEquals(LocalDate.of(2024, 2, 1), days[4])
        assertEquals(LocalDate.of(2024, 2, 29), days[32])
    }

    @Test
    fun getCalendarDays_日曜開始の月_2023年10月() {
        val ym = YearMonth.of(2023, 10)
        val days = MedicationLogic.getCalendarDays(ym)

        // 10/1は日曜日(0)なので、空セルなし。31日まで。
        assertEquals(31, days.size)
        assertEquals(LocalDate.of(2023, 10, 1), days[0])
    }

    // --- Enum 変換のテスト (ML_EN) ---
    @Test
    fun enumConversion_TimeSlot() {
        assertEquals(MedicationTimeSlot.MORNING, MedicationTimeSlot.fromIndex(0))
        assertEquals(MedicationTimeSlot.LUNCH, MedicationTimeSlot.fromIndex(1))
        assertEquals(MedicationTimeSlot.DINNER, MedicationTimeSlot.fromIndex(2))
        assertEquals(MedicationTimeSlot.BEDTIME, MedicationTimeSlot.fromIndex(3))
        assertNull(MedicationTimeSlot.fromIndex(4))
    }

    @Test
    fun enumConversion_Status() {
        assertEquals(MedicationStatus.NONE, MedicationStatus.fromCode(0))
        assertEquals(MedicationStatus.ASSIST, MedicationStatus.fromCode(1))
        assertEquals(MedicationStatus.TAKEN, MedicationStatus.fromCode(2))
        assertNull(MedicationStatus.fromCode(9))
        assertNull(MedicationStatus.fromCode(null))
    }

    // --- 同期アクション判定のテスト (ML_SY) ---
    @Test
    fun determineSyncActions_追加_削除_更新の混在() {
        val now = Instant.now()
        val todayStr = LocalDate.now().toString()
        val existing = listOf(
            MedicationRecord(id = 1, personId = 1, dosageDate = todayStr, timeSlot = 0, status = 2, recordTime = now) // 朝:服用済
        )

        // 入力：朝を未服用(0)に変更、昼に介助(1)を追加
        val input = listOf(
            MedicationRecord(id = 1, personId = 1, dosageDate = todayStr, timeSlot = 0, status = 0, recordTime = now),
            MedicationRecord(id = 0, personId = 1, dosageDate = todayStr, timeSlot = 1, status = 1, recordTime = now),
            null,
            null
        )

        val actions = MedicationLogic.determineSyncActions(existing, input)

        // 朝の更新(Insert)と昼の新規(Insert)の2件
        assertEquals(2, actions.size)
        assertTrue(actions.any { it is SyncAction.Insert && it.record.timeSlot == 0 && it.record.status == 0 })
        assertTrue(actions.any { it is SyncAction.Insert && it.record.timeSlot == 1 && it.record.status == 1 })
    }

    @Test
    fun determineSyncActions_既存レコードの削除() {
        val now = Instant.now()
        val todayStr = LocalDate.now().toString()
        val existing = listOf(
            MedicationRecord(id = 1, personId = 1, dosageDate = todayStr, timeSlot = 0, status = 2, recordTime = now)
        )

        // 入力：全てnull
        val input = listOf<MedicationRecord?>(null, null, null, null)

        val actions = MedicationLogic.determineSyncActions(existing, input)

        // 朝の削除(Delete)が1件
        assertEquals(1, actions.size)
        assertTrue(actions[0] is SyncAction.Delete)
        assertEquals(0, (actions[0] as SyncAction.Delete).record.timeSlot)
    }
}
