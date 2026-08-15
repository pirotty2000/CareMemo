package jp.mydns.fujiwara.carememo.ui.mapping

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.common.MedicationStatus
import jp.mydns.fujiwara.carememo.logic.common.MedicationTimeSlot

/**
 * Component：MedicationDisplayMapper
 *
 * 【役割】
 * 服薬管理の判定結果（Enum）を表示用の資源（記号、色、ラベル）に変換するマッパーです。
 * カレンダー表示や履歴テーブルにおける一貫した視覚表現を保証します。
 */
object MedicationDisplayMapper {

    /**
     * ステータスに対応する記号を返します。
     */
    fun getStatusSymbol(status: MedicationStatus?): String = when (status) {
        MedicationStatus.NONE -> "×"
        MedicationStatus.ASSIST -> "△"
        MedicationStatus.TAKEN -> "○"
        null -> "－"
    }

    /**
     * ステータスに対応する Compose 用のカラーを返します。
     */
    @Composable
    fun getStatusColor(status: MedicationStatus?): Color {
        val warningColor = if (isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFE65100)
        return when (status) {
            MedicationStatus.NONE -> MaterialTheme.colorScheme.error
            MedicationStatus.ASSIST -> warningColor
            MedicationStatus.TAKEN -> MaterialTheme.colorScheme.primary
            null -> Color.Transparent
        }
    }

    /**
     * 時間枠に対応するラベルのリソースIDを返します。
     */
    fun getTimeSlotLabelRes(slot: MedicationTimeSlot, isShort: Boolean = false): Int = when (slot) {
        MedicationTimeSlot.MORNING -> R.string.slot_morning
        MedicationTimeSlot.LUNCH -> R.string.slot_lunch
        MedicationTimeSlot.DINNER -> R.string.slot_dinner
        MedicationTimeSlot.BEDTIME -> if (isShort) R.string.slot_bedtime_short else R.string.slot_bedtime
    }
}
