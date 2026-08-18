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
 * @param year 記録日時：年
 * @param month 記録日時：月
 * @param day 記録日時：日
 * @param hour 記録日時：時
 * @param minute 記録日時：分
 * @param initialYear 初期状態の記録日時：年（変更検知用）
 * @param initialMonth 初期状態の記録日時：月
 * @param initialDay 初期状態の記録日時：日
 * @param initialHour 初期状態の記録日時：時
 * @param initialMinute 初期状態の記録日時：分
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

    val year: String = "",
    val month: String = "",
    val day: String = "",
    val hour: String = "",
    val minute: String = "",

    val initialYear: String = "",
    val initialMonth: String = "",
    val initialDay: String = "",
    val initialHour: String = "",
    val initialMinute: String = "",
    
    override val isLoading: Boolean = false,
    val isValid: Boolean = false,
    val isChanged: Boolean = false,
    val isNameMaskingEnabled: Boolean = true,
    val recordTime: Instant? = null
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
 * 1. 各カテゴリの評価は [HealthCategoryProcessor] を介して実行する。
 * 2. 「保存可能」の定義は、「不正な入力が1つもなく、かつ保存すべきデータが1つ以上ある」状態とする。
 * 3. 変更検知は、入力値の有無だけでなく、記録時刻の変更も考慮する。
 */
object BatchInputLogic {

    /**
     * UI 状態から記録日時（分精度まで）を算出します。
     *
     * @param state 現在のUI状態
     * @return 算出された [Instant]、変換不能な場合は null
     */
    fun calculateRecordTime(state: BatchInputUiState): Instant? {
        val y = state.year.toIntOrNull() ?: return null
        val m = state.month.toIntOrNull() ?: return null
        val d = state.day.toIntOrNull() ?: return null
        val h = state.hour.toIntOrNull() ?: 0
        val min = state.minute.toIntOrNull() ?: 0

        return try {
            ZonedDateTime.of(y, m, d, h, min, 0, 0, ZoneId.systemDefault()).toInstant()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 入力内容の妥当性を一括判定し、詳細なバリデーション結果を返します。
     *
     * @param state 現在のUI状態
     * @return [BatchInputValidationResult]
     */
    fun validate(state: BatchInputUiState): BatchInputValidationResult {
        // --- 1. 数値変換と範囲の基本チェック ---
        val y = state.year.toIntOrNull()
        val m = state.month.toIntOrNull()
        val d = state.day.toIntOrNull()
        val h = state.hour.toIntOrNull()
        val min = state.minute.toIntOrNull()

        // いずれかのフィールドが未入力、または数値以外なら、日付不備として扱う
        if (y == null || m == null || d == null || h == null || min == null) {
            return BatchInputValidationResult.INVALID_VALUE
        }

        // --- 2. 日付・時刻の論理的妥当性チェック ---
        // 暦に存在しない日付（8/32等）や、時間の範囲外をチェック
        val isDateValid = JapaneseDateLogic.isValid(BirthEra.AD, y, m, d)
        val isTimeValid = h in 0..23 && min in 0..59

        if (!isDateValid || !isTimeValid) {
            return BatchInputValidationResult.INVALID_VALUE
        }

        // --- 3. 各カテゴリ（身長・バイタル等）の入力内容チェック ---
        val processors = HealthProcessorRegistry.getAll()
        var hasAtLeastOneValidInput = false

        for (processor in processors) {
            // そのカテゴリに何らかの入力があるか判定
            if (!processor.isEmpty(state)) {
                // 入力がある場合、そのカテゴリのバリデーション結果が SUCCESS 以外（EMPTY含む）ならエラー
                if (processor.validate(state) != HealthInputValidationResult.SUCCESS) {
                    return BatchInputValidationResult.INVALID_VALUE
                }
                hasAtLeastOneValidInput = true
            }
        }

        // --- 4. 最終判定 ---
        return when {
            // 全て未入力
            !hasAtLeastOneValidInput -> BatchInputValidationResult.EMPTY_ALL
            // 妥当な入力がある
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
     * 【設計意図】
     * UI 境界の外側である Logic クラスの戻り値には、標準の [List] を使用します。
     *
     * @param state 現在のUI状態
     * @return [BatchInputCategory] のリスト
     */
    fun getEffectiveCategories(state: BatchInputUiState): List<BatchInputCategory> {
        if (validate(state) != BatchInputValidationResult.SUCCESS) return emptyList()

        return HealthProcessorRegistry.getAll()
            .filter { !it.isEmpty(state) }
            .map { it.category }
    }

    /**
     * 初期状態から入力内容、または記録時刻が変更されているかどうかを判定します。
     *
     * @param state 現在のUI状態
     * @return 変更がある場合は true
     */
    fun isChanged(state: BatchInputUiState): Boolean {
        val hasInput = HealthProcessorRegistry.getAll().any { !it.isEmpty(state) }
        val isTimeChanged = state.year != state.initialYear ||
                state.month != state.initialMonth ||
                state.day != state.initialDay ||
                state.hour != state.initialHour ||
                state.minute != state.initialMinute

        return hasInput || isTimeChanged
    }

    /**
     * UI状態から、DB保存対象となる Entity（HeightAndWeight等）のリストを生成します。
     *
     * 【設計意図】
     * UI 境界の外側である Logic クラスの戻り値には、標準の [List] を使用します。
     * 新規作成ユースケースであるため、各 Entity には新規 UUID を明示的に割り当てます。
     *
     * @param personId 対象者のID
     * @param time 記録時刻
     * @param state 現在のUI状態
     * @return 生成された Entity オブジェクトのリスト
     * @throws IllegalArgumentException 不正な入力が一つでもある場合にスロー
     */
    fun createEntities(personId: String, time: Instant, state: BatchInputUiState): List<Any> {
        // 不正な入力がある状態で呼び出された場合は異常系として扱う（呼び出し側でvalidate済みであることを期待）
        if (validate(state) == BatchInputValidationResult.INVALID_VALUE) {
            throw IllegalArgumentException("Invalid input state")
        }

        return HealthProcessorRegistry.getAll()
            .mapNotNull { processor ->
                // 各カテゴリの保存データが存在する場合、ID（UUID）を確定させる (ADR #8)
                when (val entity = processor.createEntity(personId, time, state)) {
                    is jp.mydns.fujiwara.carememo.data.HeightAndWeight -> entity.copy(id = UUID.randomUUID().toString())
                    is jp.mydns.fujiwara.carememo.data.BpAndPulse -> entity.copy(id = UUID.randomUUID().toString())
                    is jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c -> entity.copy(id = UUID.randomUUID().toString())
                    else -> entity
                }
            }
    }
}
