package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.logic.common.HealthLogic
import java.time.Instant

/**
 * 一括入力画面用の UI 状態
 */
data class BatchInputUiState(
    val height: String = "",
    val weight: String = "",
    val bpSystolic: String = "",
    val bpDiastolic: String = "",
    val sat: String = "",
    val pulse: String = "",
    val bodyTemperature: String = "",
    val glucose: String = "",
    val hba1c: String = ""
)

/**
 * 健康記録の一括入力に関するドメインロジック
 */
object BatchInputLogic {

    /**
     * いずれかのカテゴリが保存可能な入力を持っているか判定します。
     */
    fun isValid(state: BatchInputUiState): Boolean {
        return HealthLogic.isValidHeightAndWeight(state.height, state.weight) ||
                HealthLogic.isValidBpAndPulse(state.bpSystolic, state.bpDiastolic, state.sat, state.pulse, state.bodyTemperature) ||
                HealthLogic.isValidGlucoseAndHbA1c(state.glucose, state.hba1c)
    }

    /**
     * 初期状態から変更されているかどうかを判定します。
     * 数値入力があるか、あるいは日時が初期値から変更されている場合に true を返します。
     */
    fun isChanged(current: BatchInputUiState, currentInstant: Instant?, initialInstant: Instant?): Boolean {
        val hasInput = current.height.isNotBlank() ||
                current.weight.isNotBlank() ||
                current.bpSystolic.isNotBlank() ||
                current.bpDiastolic.isNotBlank() ||
                current.sat.isNotBlank() ||
                current.pulse.isNotBlank() ||
                current.bodyTemperature.isNotBlank() ||
                current.glucose.isNotBlank() ||
                current.hba1c.isNotBlank()

        // 時刻が null の場合は「未完成」として変更ありとみなす（バリデーションで弾かれるが保護対象）
        val isTimeChanged = currentInstant == null || initialInstant == null || currentInstant != initialInstant

        return hasInput || isTimeChanged
    }

    /**
     * UI状態から保存対象となる Entity のリストを生成します。
     * 有効な入力があるカテゴリのみがリストに含まれます。
     */
    fun createEntities(personId: Int, time: Instant, state: BatchInputUiState): List<Any> {
        val entities = mutableListOf<Any>()

        // 身長・体重
        if (HealthLogic.isValidHeightAndWeight(state.height, state.weight)) {
            entities.add(
                HeightAndWeight(
                    personId = personId,
                    height = state.height.toDoubleOrNull(),
                    weight = state.weight.toDoubleOrNull(),
                    recordTime = time
                )
            )
        }

        // バイタル
        if (HealthLogic.isValidBpAndPulse(state.bpSystolic, state.bpDiastolic, state.sat, state.pulse, state.bodyTemperature)) {
            entities.add(
                BpAndPulse(
                    personId = personId,
                    bpSystolic = state.bpSystolic.toIntOrNull(),
                    bpDiastolic = state.bpDiastolic.toIntOrNull(),
                    sat = state.sat.toIntOrNull(),
                    pulse = state.pulse.toIntOrNull(),
                    bodyTemperature = state.bodyTemperature.toDoubleOrNull(),
                    recordTime = time
                )
            )
        }

        // 血糖値
        if (HealthLogic.isValidGlucoseAndHbA1c(state.glucose, state.hba1c)) {
            entities.add(
                GlucoseAndHbA1c(
                    personId = personId,
                    glucose = state.glucose.toIntOrNull(),
                    hba1c = state.hba1c.toDoubleOrNull(),
                    recordTime = time
                )
            )
        }

        return entities
    }
}
