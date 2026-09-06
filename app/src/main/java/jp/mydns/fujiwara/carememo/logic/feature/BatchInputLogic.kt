package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.runtime.Immutable
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import jp.mydns.fujiwara.carememo.logic.common.JapaneseDateLogic
import jp.mydns.fujiwara.carememo.viewmodel.PersonAwareState
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * UI State：BatchInputUiState
 *
 * 【役割】
 * 健康記録の一括入力画面における、全カテゴリの入力値と画面状態を管理します。
 *
 * @param screenState 構造的状態 (Loading / Active / Error)
 * @param operation 実行中の操作状態 (Idle / Saving)
 * @param personId 対象者のID
 * @param person 対象者の基本情報 (Domain Content)
 * @param personSummary 既存の記録状況サマリー (Domain Content)
 * @param input 現在の入力セッション (UI Content Details)
 * @param isNameMaskingEnabled 氏名のマスキング設定
 */
@Immutable
data class BatchInputUiState(
    val screenState: BatchInputScreenState = BatchInputScreenState.Loading,
    val operation: BatchInputOperation = BatchInputOperation.Idle,

    override val personId: String? = null,
    val person: Person? = null,
    val personSummary: PersonCategorySummary? = null,

    val input: BatchInputSession = BatchInputSession(),

    val isNameMaskingEnabled: Boolean = true,

    @Deprecated("Use screenState and operation")
    override val isLoading: Boolean = false,
    @Deprecated("Moved to input session")
    val isValid: Boolean = false,
    @Deprecated("Moved to input session")
    val isChanged: Boolean = false,
    @Deprecated("Use person name masking helper")
    val currentPersonName: String = "",
    @Deprecated("Always Category.HEALTH in this context")
    override val currentCategory: Category? = null
) : PersonAwareState

/**
 * 構造的状態 (Structural State)
 */
sealed interface BatchInputScreenState {
    data object Loading : BatchInputScreenState
    data object Active : BatchInputScreenState
    data class Error(val throwable: Throwable) : BatchInputScreenState
}

/**
 * 操作状態 (Operation State)
 */
sealed interface BatchInputOperation {
    data object Idle : BatchInputOperation
    data object Saving : BatchInputOperation
}

/**
 * 入力セッション状態 (UI Content Details)
 */
@Immutable
data class BatchInputSession(
    // 健康指標
    val height: String = "",
    val weight: String = "",
    val bpSystolic: String = "",
    val bpDiastolic: String = "",
    val sat: String = "",
    val pulse: String = "",
    val bodyTemperature: String = "",
    val glucose: String = "",
    val hba1c: String = "",

    // 記録日時
    val year: String = "",
    val month: String = "",
    val day: String = "",
    val hour: String = "",
    val minute: String = "",

    // 変更検知用基準点 (Baselines)
    val initialYear: String = "",
    val initialMonth: String = "",
    val initialDay: String = "",
    val initialHour: String = "",
    val initialMinute: String = "",

    // 派生状態
    val isValid: Boolean = false,
    val isChanged: Boolean = false,
    val recordTime: Instant? = null,
    val fieldErrors: Map<String, Int?> = emptyMap(),
    val fieldErrorArgs: Map<String, List<String>> = emptyMap(),
    val touchedFields: Set<String> = emptySet()
)

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
 * Logic：BatchInputLogic
 *
 * 【役割】
 * 健康記録の一括入力画面における、複数カテゴリにわたる入力内容の評価と Entity 生成を行います。
 */
object BatchInputLogic {

    /**
     * 入力セッションから記録日時（分精度まで）を算出します。
     *
     * @param input 現在の入力セッション
     * @return 算出された [Instant]、変換不能な場合は null
     */
    fun calculateRecordTime(input: BatchInputSession): Instant? {
        val y = input.year.toIntOrNull() ?: return null
        val m = input.month.toIntOrNull() ?: return null
        val d = input.day.toIntOrNull() ?: return null
        val h = input.hour.toIntOrNull() ?: 0
        val min = input.minute.toIntOrNull() ?: 0

        return try {
            ZonedDateTime.of(y, m, d, h, min, 0, 0, ZoneId.systemDefault()).toInstant()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 入力内容の妥当性を一括判定し、詳細なバリデーション結果を返します。
     *
     * @param input 現在の入力セッション
     * @return [BatchInputValidationResult]
     */
    fun validate(input: BatchInputSession): BatchInputValidationResult {
        // --- 1. 数値変換と範囲の基本チェック ---
        val y = input.year.toIntOrNull()
        val m = input.month.toIntOrNull()
        val d = input.day.toIntOrNull()
        val h = input.hour.toIntOrNull()
        val min = input.minute.toIntOrNull()

        // いずれかのフィールドが未入力、または数値以外なら、日付不備として扱う
        if (y == null || m == null || d == null || h == null || min == null) {
            return BatchInputValidationResult.INVALID_VALUE
        }

        // --- 2. 日付・時刻の論理的妥当性チェック ---
        val isDateValid = JapaneseDateLogic.isValid(BirthEra.AD, y, m, d)
        val isTimeValid = h in 0..23 && min in 0..59

        if (!isDateValid || !isTimeValid) {
            return BatchInputValidationResult.INVALID_VALUE
        }

        // --- 3. 各カテゴリ（身長・バイタル等）の入力内容チェック ---
        val processors = HealthProcessorRegistry.getAll()
        var hasAtLeastOneValidInput = false

        for (processor in processors) {
            if (!processor.isEmpty(input)) {
                if (processor.validate(input) != HealthInputValidationResult.SUCCESS) {
                    return BatchInputValidationResult.INVALID_VALUE
                }
                hasAtLeastOneValidInput = true
            }
        }

        // --- 4. 最終判定 ---
        return when {
            !hasAtLeastOneValidInput -> BatchInputValidationResult.EMPTY_ALL
            else -> BatchInputValidationResult.SUCCESS
        }
    }

    /**
     * 保存可能かどうかを判定します（UIのボタン有効化用）。
     */
    fun isValid(input: BatchInputSession): Boolean {
        return validate(input) == BatchInputValidationResult.SUCCESS
    }

    /**
     * 有効な入力がある（正常なデータとして保存対象となる）カテゴリのリストを取得します。
     */
    fun getEffectiveCategories(input: BatchInputSession): List<BatchInputCategory> {
        if (validate(input) != BatchInputValidationResult.SUCCESS) return emptyList()

        return HealthProcessorRegistry.getAll()
            .filter { !it.isEmpty(input) }
            .map { it.category }
    }

    /**
     * 初期状態から入力内容、または記録時刻が変更されているかどうかを判定します。
     */
    fun isChanged(input: BatchInputSession): Boolean {
        val hasInput = HealthProcessorRegistry.getAll().any { !it.isEmpty(input) }
        val isTimeChanged = input.year != input.initialYear ||
                input.month != input.initialMonth ||
                input.day != input.initialDay ||
                input.hour != input.initialHour ||
                input.minute != input.initialMinute

        return hasInput || isTimeChanged
    }

    /**
     * UI状態から、DB保存対象となる Entity のリストを生成します。
     */
    fun createEntities(personId: String, time: Instant, input: BatchInputSession): List<Any> {
        if (validate(input) == BatchInputValidationResult.INVALID_VALUE) {
            throw IllegalArgumentException("Invalid input state")
        }

        return HealthProcessorRegistry.getAll()
            .mapNotNull { processor ->
                when (val entity = processor.createEntity(personId, time, input)) {
                    is jp.mydns.fujiwara.carememo.data.HeightAndWeight -> entity.copy(id = UUID.randomUUID().toString())
                    is jp.mydns.fujiwara.carememo.data.BpAndPulse -> entity.copy(id = UUID.randomUUID().toString())
                    is jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c -> entity.copy(id = UUID.randomUUID().toString())
                    else -> entity
                }
            }
    }
}
