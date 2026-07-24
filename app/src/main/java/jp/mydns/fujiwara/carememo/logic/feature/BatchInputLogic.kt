package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import jp.mydns.fujiwara.carememo.logic.common.HealthLogic
import jp.mydns.fujiwara.carememo.viewmodel.PersonAwareState
import java.time.Instant

/**
 * 一括入力画面用の UI 状態
 */
data class BatchInputUiState(
    val personId: String? = null,
    val person: Person? = null, // 追加
    val currentPersonName: String = "",
    val personSummary: PersonCategorySummary? = null,

    val height: String = "",
    val weight: String = "",
    val bpSystolic: String = "",
    val bpDiastolic: String = "",
    val sat: String = "",
    val pulse: String = "",
    val bodyTemperature: String = "",
    val glucose: String = "",
    val hba1c: String = "",

    val recordTime: Instant = Instant.now(),
    val initialRecordTime: Instant = recordTime, // 変更検知の基準点
    
    override val isLoading: Boolean = false,
    val isValid: Boolean = false,
    val isChanged: Boolean = false,
    val isNameMaskingEnabled: Boolean = true
) : PersonAwareState

/**
 * 一括入力画面固有のイベント
 */
sealed interface BatchInputViewEvent {
    /** 保存成功時の演出（フラッシュ、スクロールトップ等）を要求する */
    object SaveSuccessEffects : BatchInputViewEvent
}

/**
 * 一括入力のバリデーション結果
 */
enum class BatchInputValidationResult {
    SUCCESS,      // 保存可能なデータが1つ以上あり、かつ不正な入力がない
    EMPTY_ALL,    // 全ての項目が未入力
    INVALID_VALUE // いずれかの項目に形式不正または範囲外の値がある
}

/**
 * 健康記録のカテゴリ定義
 */
enum class BatchInputCategory {
    HEIGHT_WEIGHT,
    VITAL,
    GLUCOSE
}

/**
 * 健康記録の一括入力に関するドメインロジック
 */
object BatchInputLogic {

    /**
     * 入力内容の妥当性を判定し、詳細な「事実」を返します。
     */
    fun validate(state: BatchInputUiState): BatchInputValidationResult {
        // 全項目が空かチェック
        val isAllBlank = state.height.isBlank() && state.weight.isBlank() &&
                state.bpSystolic.isBlank() && state.bpDiastolic.isBlank() &&
                state.sat.isBlank() && state.pulse.isBlank() &&
                state.bodyTemperature.isBlank() && state.glucose.isBlank() &&
                state.hba1c.isBlank()

        if (isAllBlank) return BatchInputValidationResult.EMPTY_ALL

        // 各項目の形式チェック (いずれかの入力がある場合のみ判定)
        
        // 身長・体重
        if (state.height.isNotBlank() || state.weight.isNotBlank()) {
            if (HealthLogic.validateHeightAndWeight(state.height, state.weight) != HealthInputValidationResult.SUCCESS) {
                return BatchInputValidationResult.INVALID_VALUE
            }
        }

        // バイタル
        if (state.bpSystolic.isNotBlank() || state.bpDiastolic.isNotBlank() || 
            state.sat.isNotBlank() || state.pulse.isNotBlank() || state.bodyTemperature.isNotBlank()) {
            if (HealthLogic.validateBpAndPulse(state.bpSystolic, state.bpDiastolic, state.sat, state.pulse, state.bodyTemperature) != HealthInputValidationResult.SUCCESS) {
                return BatchInputValidationResult.INVALID_VALUE
            }
        }

        // 血糖値
        if (state.glucose.isNotBlank() || state.hba1c.isNotBlank()) {
            if (HealthLogic.validateGlucoseAndHbA1c(state.glucose, state.hba1c) != HealthInputValidationResult.SUCCESS) {
                return BatchInputValidationResult.INVALID_VALUE
            }
        }

        return BatchInputValidationResult.SUCCESS
    }

    /**
     * 保存可能かどうかを判定します（UI用）。
     */
    fun isValid(state: BatchInputUiState): Boolean {
        return validate(state) == BatchInputValidationResult.SUCCESS
    }

    /**
     * 入力がある（保存対象となる）カテゴリのリストを取得します。
     */
    fun getEffectiveCategories(state: BatchInputUiState): List<BatchInputCategory> {
        val categories = mutableListOf<BatchInputCategory>()
        if (HealthLogic.isValidHeightAndWeight(state.height, state.weight)) {
            categories.add(BatchInputCategory.HEIGHT_WEIGHT)
        }
        if (HealthLogic.isValidBpAndPulse(state.bpSystolic, state.bpDiastolic, state.sat, state.pulse, state.bodyTemperature)) {
            categories.add(BatchInputCategory.VITAL)
        }
        if (HealthLogic.isValidGlucoseAndHbA1c(state.glucose, state.hba1c)) {
            categories.add(BatchInputCategory.GLUCOSE)
        }
        return categories
    }

    /**
     * 初期状態から変更されているかどうかを判定します。
     */
    fun isChanged(state: BatchInputUiState): Boolean {
        val hasInput = state.height.isNotBlank() ||
                state.weight.isNotBlank() ||
                state.bpSystolic.isNotBlank() ||
                state.bpDiastolic.isNotBlank() ||
                state.sat.isNotBlank() ||
                state.pulse.isNotBlank() ||
                state.bodyTemperature.isNotBlank() ||
                state.glucose.isNotBlank() ||
                state.hba1c.isNotBlank()

        val isTimeChanged = state.recordTime != state.initialRecordTime

        return hasInput || isTimeChanged
    }

    /**
     * UI状態から保存対象となる Entity のリストを生成します。
     */
    fun createEntities(personId: String, time: Instant, state: BatchInputUiState): List<Any> {
        if (!isValid(state)) throw IllegalArgumentException("Invalid input state")

        val entities = mutableListOf<Any>()

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
