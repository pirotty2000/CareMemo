package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import jp.mydns.fujiwara.carememo.logic.common.HealthLogic
import jp.mydns.fujiwara.carememo.viewmodel.PersonAwareState
import java.time.Instant

/**
 * 一括入力画面用の UI 状態
 */
data class BatchInputUiState(
    override val personId: String? = null,
    override val currentCategory: Category? = null,
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
 * 内部評価用のカテゴリ状態
 */
private sealed interface CategoryResult {
    object Empty : CategoryResult
    data class Valid(val entity: Any) : CategoryResult
    data class Invalid(val result: HealthInputValidationResult) : CategoryResult
}

/**
 * 健康記録の一括入力に関するドメインロジック
 */
object BatchInputLogic {

    /**
     * 入力内容の妥当性を一括判定し、詳細なバリデーション結果を返します。
     *
     * @param state 現在のUI状態
     * @return [BatchInputValidationResult] (SUCCESS: 保存可能, EMPTY_ALL: 未入力, INVALID_VALUE: 不正あり)
     */
    fun validate(state: BatchInputUiState): BatchInputValidationResult {
        // バリデーション目的なので仮のIDと時間を使用
        val results = evaluateCategories("dummy", Instant.EPOCH, state).values

        return when {
            results.any { it is CategoryResult.Invalid } -> BatchInputValidationResult.INVALID_VALUE
            results.all { it is CategoryResult.Empty } -> BatchInputValidationResult.EMPTY_ALL
            else -> BatchInputValidationResult.SUCCESS
        }
    }

    /**
     * 入力内容が保存可能な状態かどうかを判定します。
     *
     * @param state 現在のUI状態
     * @return 保存可能な場合は true、それ以外は false
     */
    fun isValid(state: BatchInputUiState): Boolean {
        return validate(state) == BatchInputValidationResult.SUCCESS
    }

    /**
     * 入力がある（正常なデータとして保存対象となる）カテゴリのリストを取得します。
     *
     * @param state 現在のUI状態
     * @return 有効な入力がある [BatchInputCategory] のリスト
     */
    fun getEffectiveCategories(state: BatchInputUiState): List<BatchInputCategory> {
        val results = evaluateCategories("dummy", Instant.EPOCH, state)
        return results.filter { it.value is CategoryResult.Valid }.keys.toList()
    }

    /**
     * 初期状態から入力内容、または記録時刻が変更されているかどうかを判定します。
     *
     * @param state 現在のUI状態
     * @return 変更がある場合は true、それ以外は false
     */
    fun isChanged(state: BatchInputUiState): Boolean {
        // 入力があるかどうかを評価結果から判断
        val results = evaluateCategories("dummy", Instant.EPOCH, state).values
        val hasInput = results.any { it !is CategoryResult.Empty }
        val isTimeChanged = state.recordTime != state.initialRecordTime

        return hasInput || isTimeChanged
    }

    /**
     * UI状態から、DB保存対象となる Entity（HeightAndWeight等）のリストを生成します。
     * 不正な入力（Invalid）が一つでもある場合は、例外をスローします。
     *
     * @param personId 対象者のID
     * @param time 記録時刻
     * @param state 現在のUI状態
     * @return 生成された Entity オブジェクトのリスト
     * @throws IllegalArgumentException 不正な入力状態で呼び出された場合
     */
    fun createEntities(personId: String, time: Instant, state: BatchInputUiState): List<Any> {
        val results = evaluateCategories(personId, time, state).values

        // 不正な入力が一つでもある場合は例外（呼び出し側でvalidate済みであることを期待）
        if (results.any { it is CategoryResult.Invalid }) {
            throw IllegalArgumentException("Invalid input state")
        }

        return results.filterIsInstance<CategoryResult.Valid>().map { it.entity }
    }

    /**
     * 全カテゴリの入力を評価し、その結果をカテゴリごとのマップで返します。
     *
     * @param personId 対象者のID
     * @param time 記録時刻
     * @param state 現在のUI状態
     * @return カテゴリをキー、評価結果を値とする Map
     */
    private fun evaluateCategories(
        personId: String,
        time: Instant,
        state: BatchInputUiState
    ): Map<BatchInputCategory, CategoryResult> {
        return mapOf(
            BatchInputCategory.HEIGHT_WEIGHT to evaluateHeightWeight(personId, time, state),
            BatchInputCategory.VITAL to evaluateVital(personId, time, state),
            BatchInputCategory.GLUCOSE to evaluateGlucose(personId, time, state)
        )
    }

    /**
     * 身長・体重の入力を評価します。
     */
    private fun evaluateHeightWeight(personId: String, time: Instant, state: BatchInputUiState): CategoryResult {
        if (state.height.isBlank() && state.weight.isBlank()) return CategoryResult.Empty

        val validation = HealthLogic.validateHeightAndWeight(state.height, state.weight)
        return if (validation == HealthInputValidationResult.SUCCESS) {
            CategoryResult.Valid(
                HeightAndWeight(
                    personId = personId,
                    height = state.height.toDoubleOrNull(),
                    weight = state.weight.toDoubleOrNull(),
                    recordTime = time
                )
            )
        } else {
            CategoryResult.Invalid(validation)
        }
    }

    /**
     * バイタル（血圧、脈拍、酸素飽和度、体温）の入力を評価します。
     */
    private fun evaluateVital(personId: String, time: Instant, state: BatchInputUiState): CategoryResult {
        val isAllBlank = state.bpSystolic.isBlank() && state.bpDiastolic.isBlank() &&
                state.sat.isBlank() && state.pulse.isBlank() && state.bodyTemperature.isBlank()
        if (isAllBlank) return CategoryResult.Empty

        val validation = HealthLogic.validateBpAndPulse(
            state.bpSystolic, state.bpDiastolic, state.sat, state.pulse, state.bodyTemperature
        )
        return if (validation == HealthInputValidationResult.SUCCESS) {
            CategoryResult.Valid(
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
        } else {
            CategoryResult.Invalid(validation)
        }
    }

    /**
     * 血糖値・HbA1cの入力を評価します。
     */
    private fun evaluateGlucose(personId: String, time: Instant, state: BatchInputUiState): CategoryResult {
        if (state.glucose.isBlank() && state.hba1c.isBlank()) return CategoryResult.Empty

        val validation = HealthLogic.validateGlucoseAndHbA1c(state.glucose, state.hba1c)
        return if (validation == HealthInputValidationResult.SUCCESS) {
            CategoryResult.Valid(
                GlucoseAndHbA1c(
                    personId = personId,
                    glucose = state.glucose.toIntOrNull(),
                    hba1c = state.hba1c.toDoubleOrNull(),
                    recordTime = time
                )
            )
        } else {
            CategoryResult.Invalid(validation)
        }
    }
}
