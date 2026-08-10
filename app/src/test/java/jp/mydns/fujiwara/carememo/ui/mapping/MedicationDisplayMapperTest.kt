package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.common.MedicationStatus
import jp.mydns.fujiwara.carememo.logic.common.MedicationTimeSlot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mapping層テスト：MedicationDisplayMapper
 */
class MedicationDisplayMapperTest {

    // region 2. 記号マッピングテスト (getStatusSymbol)

    @Test
    fun SYM_01_taken_symbol() {
        assertEquals("○", MedicationDisplayMapper.getStatusSymbol(MedicationStatus.TAKEN))
    }

    @Test
    fun SYM_02_assist_symbol() {
        assertEquals("△", MedicationDisplayMapper.getStatusSymbol(MedicationStatus.ASSIST))
    }

    @Test
    fun SYM_03_none_symbol() {
        assertEquals("×", MedicationDisplayMapper.getStatusSymbol(MedicationStatus.NONE))
    }

    @Test
    fun SYM_04_null_symbol() {
        assertEquals("－", MedicationDisplayMapper.getStatusSymbol(null))
    }

    // endregion

    // region 3. 時間枠ラベルテスト (getTimeSlotLabelRes)

    @Test
    fun LBL_01_morning_label() {
        assertEquals(R.string.slot_morning, MedicationDisplayMapper.getTimeSlotLabelRes(MedicationTimeSlot.MORNING))
    }

    @Test
    fun LBL_02_lunch_label() {
        assertEquals(R.string.slot_lunch, MedicationDisplayMapper.getTimeSlotLabelRes(MedicationTimeSlot.LUNCH))
    }

    @Test
    fun LBL_03_dinner_label() {
        assertEquals(R.string.slot_dinner, MedicationDisplayMapper.getTimeSlotLabelRes(MedicationTimeSlot.DINNER))
    }

    @Test
    fun LBL_04_bedtime_label() {
        assertEquals(R.string.slot_bedtime, MedicationDisplayMapper.getTimeSlotLabelRes(MedicationTimeSlot.BEDTIME, false))
    }

    @Test
    fun LBL_05_bedtimeShort_label() {
        assertEquals(R.string.slot_bedtime_short, MedicationDisplayMapper.getTimeSlotLabelRes(MedicationTimeSlot.BEDTIME, true))
    }

    // endregion
}
