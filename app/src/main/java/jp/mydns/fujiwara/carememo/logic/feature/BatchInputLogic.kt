package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.runtime.Immutable
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
 * 一括入力画面用の UI 状態。
 *
 * @param personId 対象者のID
 * @param currentCategory 現在選択されているカテゴリ
 * @param person 対象者の基本情報
 * @param currentPersonName 対象者の氏名（表示用）
 * @param personSummary 既存の記録状況サマリー
 * @param height 身長の入力文字列
 * @param weight 体重の入力文字列
 * @param bpSystolic 血圧(上)の入力文字列
 * @param bpDiastolic 血圧(下)の入力文字列
 * @param sat 酸素飽和度の入力文字列
 * @param pulse 脈拍の入力文字列
 * @param bodyTemperature 体温の入力文字列
 * @param glucose 血糖値の入力文字列
 * @param hba1c HbA1c の入力文字列
 * @param recordTime 記録時刻
 * @param initialRecordTime 初期状態の記録時刻（変更検知の基準点）
 * @param isLoading 読み込み中フラグ
 * @param isValid 保存可能な状態かどうか
 * @param isChanged 初期状態から変更があるかどうか
 * @param isNameMaskingEnabled 氏名を伏せ字にするかどうか
 */
@Immutable
data class BatchInputUiState(
    override val personId: String? = null,
    override val currentCategory: Category? = null,
    val person: Person? = null,
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
    val initialRecordTime: Instant = recordTime,
    
    override val isLoading: Boolean = false,
    val isValid: Boolean = false,
    val isChanged: Boolean = false,
    val isNameMaskingEnabled: Boolean = true
) : PersonAwareState

/**
 * 一括入力画面固有のイベント定義。
 */
sealed interface BatchInputViewEvent {
    /** 保存成功時の演出（画面リセット、スクロールトップ等）を要求する */
    data object SaveSuccessEffects : BatchInputViewEvent
    /** 前の画面に戻る */
    data object NavigateBack : BatchInputViewEvent
}

/**
 * 一括入力のバリデーション結果。
 */
enum class BatchInputValidationResult {
    /** 保存可能なデータが1つ以上あり、かつ不正な入力がない */
    SUCCESS,
    /** 全ての項目が未入力 */
    EMPTY_ALL,
    /** いずれかの項目に形式不正または範囲外の値がある */
    INVALID_VALUE
}

/**
 * 健康記録の一括入力対象カテゴリ。
 */
enum class BatchInputCategory {
    /** 身長・体重 */
    HEIGHT_WEIGHT,
    /** バイタル（血圧、脈拍、SAT、体温） */
    VITAL,
    /** 血糖値・HbA1c */
    GLUCOSE
}

/**
 * 内部評価用のカテゴリ別結果。
 */
private sealed interface CategoryResult {
    /** 項目が空 */
    data object Empty : CategoryResult
    /** 妥当な入力があり、Entity を生成済み */
    data class Valid(val entity: Any) : CategoryResult
    /** バリデーションエラー */
    data class Invalid(val result: HealthInputValidationResult) : CategoryResult
}

/**
 * Logic：BatchInputLogic
 *
 * 【役割】
 * 健康記録の一括入力画面における、複数カテゴリにわたる入力内容の評価と Entity 生成を行います。
 *
 * 【主な機能】
 * ・全カテゴリ一括でのバリデーション判定。
 * ・初期状態（未入力）からの変更検知。
 * ・入力内容に基づいた、DB保存対象となる複数 Entity（HeightAndWeight等）の生成。
 * ・有効な入力が含まれるカテゴリの抽出。
 *
 * 【設計指針】
 * 1. 各カテゴリの評価は HealthLogic の共通ルールを再利用する。
 * 2. 「保存可能」の定義は、「不正な入力が1つもなく、かつ保存すべきデータが1つ以上ある」状態とする。
 * 3. 変更検知は、入力値の有無だけでなく、記録時刻の変更も考慮する。
 */
object BatchInputLogic {

    /**
     * 入力内容の妥当性を一括判定し、詳細なバリデーション結果を返します。
     *
     * @param state 現在のUI状態
     * @return [BatchInputValidationResult]
     */
    fun validate(state: BatchInputUiState): BatchInputValidationResult {
        // バリデーション判定のみが目的のため、仮のIDと時間を使用
        val results = evaluateCategories("dummy", Instant.EPOCH, state).values

        return when {
            // いずれかのカテゴリが Invalid なら、全体として INVALID
            results.any { it is CategoryResult.Invalid } -> BatchInputValidationResult.INVALID_VALUE
            // 全てのカテゴリが Empty なら、全体として EMPTY
            results.all { it is CategoryResult.Empty } -> BatchInputValidationResult.EMPTY_ALL
            // 不正がなく、1つ以上の Valid があれば成功
            else -> BatchInputValidationResult.SUCCESS
        }
    }

    /**
     * 入力内容が保存可能な状態かどうかを判定します（UIのボタン有効化用）。
     *
     * @param state 現在のUI状態
     * @return 保存可能な場合は true、それ以外は false
     */
    fun isValid(state: BatchInputUiState): Boolean {
        return validate(state) == BatchInputValidationResult.SUCCESS
    }

    /**
     * 有効な入力がある（正常なデータとして保存対象となる）カテゴリのリストを取得します。
     *
     * @param state 現在のUI状態
     * @return [BatchInputCategory] のリスト
     */
    fun getEffectiveCategories(state: BatchInputUiState): List<BatchInputCategory> {
        val results = evaluateCategories("dummy", Instant.EPOCH, state)
        return results.filter { it.value is CategoryResult.Valid }.keys.toList()
    }

    /**
     * 初期状態から入力内容、または記録時刻が変更されているかどうかを判定します。
     *
     * @param state 現在のUI状態
     * @return 変更がある場合は true
     */
    fun isChanged(state: BatchInputUiState): Boolean {
        val results = evaluateCategories("dummy", Instant.EPOCH, state).values
        val hasInput = results.any { it !is CategoryResult.Empty }
        val isTimeChanged = state.recordTime != state.initialRecordTime

        return hasInput || isTimeChanged
    }

    /**
     * UI状態から、DB保存対象となる Entity（HeightAndWeight等）のリストを生成します。
     *
     * @param personId 対象者のID
     * @param time 記録時刻
     * @param state 現在のUI状態
     * @return 生成された Entity オブジェクトのリスト
     * @throws IllegalArgumentException 不正な入力（Invalid）が一つでもある場合にスロー
     */
    fun createEntities(personId: String, time: Instant, state: BatchInputUiState): List<Any> {
        val results = evaluateCategories(personId, time, state).values

        // 不正な入力がある状態で呼び出された場合は異常系として扱う（呼び出し側でvalidate済みであることを期待）
        if (results.any { it is CategoryResult.Invalid }) {
            throw IllegalArgumentException("Invalid input state")
        }

        return results.filterIsInstance<CategoryResult.Valid>().map { it.entity }
    }

    /**
     * 全カテゴリの入力を評価し、その結果をカテゴリごとのマップで返します。
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
