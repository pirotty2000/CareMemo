package jp.mydns.fujiwara.carememo.ui.mapping

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.common.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Mapping層テスト：HealthDisplayMapper
 */
class HealthDisplayMapperTest {

    // region 2. ラベルマッピングテスト (get...Label)

    @Test
    fun LBL_01_bmiLabels() {
        assertEquals(R.string.bmi_label_underweight, HealthDisplayMapper.getBmiLabel(BmiStatus.UNDERWEIGHT))
        assertEquals(R.string.bmi_label_normal, HealthDisplayMapper.getBmiLabel(BmiStatus.NORMAL))
        assertEquals(R.string.bmi_label_obesity_4, HealthDisplayMapper.getBmiLabel(BmiStatus.OBESITY_4))
    }

    @Test
    fun LBL_02_vitalLabels() {
        assertEquals(R.string.vital_label_normal, HealthDisplayMapper.getVitalLabel(VitalStatus.NORMAL))
        assertEquals(R.string.vital_label_high_bp, HealthDisplayMapper.getVitalLabel(VitalStatus.HIGH_BP))
        assertEquals(R.string.vital_label_fever, HealthDisplayMapper.getVitalLabel(VitalStatus.FEVER))
    }

    @Test
    fun LBL_03_glucoseLabels() {
        assertEquals(R.string.glucose_label_low, HealthDisplayMapper.getGlucoseLabel(GlucoseStatus.LOW))
        assertEquals(R.string.glucose_label_diabetes, HealthDisplayMapper.getGlucoseLabel(GlucoseStatus.DIABETES))
    }

    @Test
    fun LBL_04_hba1cLabels() {
        assertEquals(R.string.hba1c_label_normal, HealthDisplayMapper.getHbA1cLabel(HbA1cStatus.NORMAL))
        assertEquals(R.string.hba1c_label_diabetes, HealthDisplayMapper.getHbA1cLabel(HbA1cStatus.DIABETES))
    }

    @Test
    fun LBL_05_nullInput() {
        assertNull(HealthDisplayMapper.getBmiLabel(null))
        assertNull(HealthDisplayMapper.getGlucoseLabel(null))
    }

    // endregion

    // region 3. インジケーター・配色テスト (getVitalIndicatorLevel / getPdfBgColor)

    @Test
    fun IND_01_vitalIndicator_active() {
        assertEquals(HealthAlertLevel.ALERT, HealthDisplayMapper.getVitalIndicatorLevel(true))
    }

    @Test
    fun IND_02_vitalIndicator_inactive() {
        assertEquals(HealthAlertLevel.NONE, HealthDisplayMapper.getVitalIndicatorLevel(false))
    }

    @Test
    fun PDF_01_pdfBgColor_warning() {
        assertNotNull(HealthDisplayMapper.getPdfBgColor(HealthAlertLevel.WARNING))
    }

    @Test
    fun PDF_02_pdfBgColor_alert() {
        assertNotNull(HealthDisplayMapper.getPdfBgColor(HealthAlertLevel.ALERT))
    }

    // endregion

    // region 4. グラフ境界線テスト (get...GraphLimits)

    @Test
    fun LMT_01_bmiGraphLimits() {
        val context = mockk<Context>()
        every { context.getString(any()) } returns "label"
        
        val limits = HealthDisplayMapper.getBmiGraphLimits(context)
        assertEquals(2, limits.size)
    }

    @Test
    fun LMT_03_satGraphLimits() {
        val context = mockk<Context>()
        every { context.getString(any()) } returns "label"
        
        val limits = HealthDisplayMapper.getSatGraphLimits(context)
        assertEquals(1, limits.size)
    }

    // endregion
}
