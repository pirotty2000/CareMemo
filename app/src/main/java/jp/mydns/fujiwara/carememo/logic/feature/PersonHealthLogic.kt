package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.runtime.Immutable
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import jp.mydns.fujiwara.carememo.viewmodel.PersonAwareState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant
import java.util.UUID

/**
 * UI State：PersonHealthUiState
 *
 * 【役割】
 * 健康記録画面における、表示データ、選択状態、および表示優先設定を保持します。
 *
 * @param personId 対象の利用者ID
 * @param currentCategory 現在表示しているカテゴリ（身長体重、バイタル、血糖）
 * @param records 履歴レコードのリスト
 * @param preferredShowHistory グラフよりも履歴リストを優先して表示するかどうかの設定
 * @param selectedRecordId 現在詳細表示または編集対象として選択されているレコードのID
 * @param isLoading データの読み込み中フラグ
 * @param editInput 現在の入力値
 * @param initialSnapshot 編集開始時のスナップショット（変更検知用）
 * @param isChanged 初期状態から変更があるかどうか
 * @param isSaveEnabled 保存ボタンを活性化できる状態（バリデーション成功かつ変更あり）かどうか
 */
@Immutable
data class PersonHealthUiState(
    override val personId: String? = null,
    override val currentCategory: Category = Category.HEIGHT_AND_WEIGHT,
    val records: ImmutableList<HistoryRecord> = persistentListOf(),
    val preferredShowHistory: Boolean = true,
    val selectedRecordId: String? = null,
    override val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val editInput: HealthEditInput = HealthEditInput(),
    val initialRecordTime: Instant? = null,
    val initialSnapshot: HealthEditInput? = null,
    val isChanged: Boolean = false,
    val isSaveEnabled: Boolean = false,
    val fieldErrors: Map<String, Int?> = emptyMap(),
    val fieldErrorArgs: Map<String, List<String>> = emptyMap(),
    val touchedFields: Set<String> = emptySet()
) : PersonAwareState

/**
 * 健康記録の入力フォーム状態。
 */
@Immutable
data class HealthEditInput(
    val heightText: String = "",
    val weightText: String = "",
    val bpSystolicText: String = "",
    val bpDiastolicText: String = "",
    val satText: String = "",
    val pulseText: String = "",
    val bodyTemperatureText: String = "",
    val glucoseText: String = "",
    val hba1cText: String = "",
    val recordTime: Instant? = null
)

/**
 * View Event：PersonHealthViewEvent
 *
 * 【役割】
 * 健康記録画面固有の、一過性のアクション（複雑なアニメーションの開始や、特定の外部画面への遷移等）を定義します。
 */
sealed interface PersonHealthViewEvent {
    /** グラフ拡大表示画面へ遷移 */
    data class NavigateToGraphExpansion(
        val personId: String,
        val category: Category,
        val initialIndex: Int
    ) : PersonHealthViewEvent
}

/**
 * 健康記録のバリデーション結果（事実）。
 */
enum class HealthValidationResult {
    /** バリデーション成功 */
    SUCCESS,
    /** 数値が形式不正、または規定範囲外 */
    INVALID_VALUE,
    /*
     * 日時の妥当性チェック（未来日の禁止等）を厳格化する際に使用する可能性があるため保持。
    INVALID_TIME,
    */
    /** 同一利用者の同一日時に既に別のレコードが存在する */
    DUPLICATE_TIME
}

/**
 * Logic：PersonHealthLogic
 *
 * 【役割】
 * 健康記録画面（カテゴリA, C, D）における、バリデーション判定および Entity 構築のドメインロジックを提供します。
 *
 * 【設計指針：UI 境界の責務】
 * Logic レイヤーの純粋性を保つため、戻り値には特定の UI ライブラリに依存しない標準の型（Any, Map, Enum 等）を使用します。
 * UI で必要な ImmutableList への変換等は ViewModel の責務とします。
 */
object PersonHealthLogic {

    /**
     * レコード全体の内容（数値範囲）の妥当性を判定します。
     *
     * @param record 検証対象のレコード
     * @return バリデーション結果
     */
    fun validate(record: HistoryRecord?): HealthValidationResult {
        if (record == null) return HealthValidationResult.INVALID_VALUE

        val processor = when (record) {
            is HeightAndWeight -> HeightWeightProcessor
            is BpAndPulse -> VitalProcessor
            is GlucoseAndHbA1c -> GlucoseProcessor
            else -> null
        }
        val isValid = processor?.validateEntity(record) ?: true

        return if (isValid) HealthValidationResult.SUCCESS else HealthValidationResult.INVALID_VALUE
    }

    /**
     * 重複チェックを行います（自分自身を除外）。
     *
     * @param current 現在編集中のレコード
     * @param existing DBから取得された同一日時の既存レコード
     * @return 同一日時に別のIDのレコードがあれば DUPLICATE_TIME、そうでなければ SUCCESS
     */
    fun validateDuplicate(current: HistoryRecord, existing: HistoryRecord?): HealthValidationResult {
        if (existing == null) return HealthValidationResult.SUCCESS
        return if (current.id != existing.id) HealthValidationResult.DUPLICATE_TIME else HealthValidationResult.SUCCESS
    }

    /**
     * UI側の入力フィールド群（文字列マップ）を一括評価します。
     * 「保存ボタンの活性制御」や「エラー表示」に使用します。
     *
     * @param category カテゴリ
     * @param values フィールド名をキーとした入力文字列のマップ
     * @return [HealthInputValidationResult]
     */
    fun validateInputs(
        category: Category,
        values: Map<String, String>
    ): HealthInputValidationResult {
        val processor = HealthProcessorRegistry.getByGeneralCategory(category)
        return processor?.validateFromMap(values) ?: HealthInputValidationResult.SUCCESS
    }

    /**
     * 入力内容から永続化用 Entity を構築します。
     * IDの新規採番（UUID）または既存IDの継承をこのメソッドが保証します。
     *
     * @param category カテゴリ
     * @param personId 利用者ID
     * @param recordId レコードID（新規なら新規用定数、既存ならそのIDを維持）
     * @param recordTime 記録日時
     * @param values 型変換済みの値のマップ
     * @return 生成された Entity オブジェクト
     * @throws IllegalArgumentException 未サポートのカテゴリの場合
     */
    fun createEntity(
        category: Category,
        personId: String,
        recordId: String,
        recordTime: Instant,
        values: Map<String, Any?>
    ): Any {
        val finalId = if (IdLogic.isNew(recordId)) UUID.randomUUID().toString() else recordId
        val processor = HealthProcessorRegistry.getByGeneralCategory(category)
        return processor?.createEntityFromValues(personId, finalId, recordTime, values)
            ?: throw IllegalArgumentException("Unsupported category: $category")
    }
}

/**
 * Map 内の値を Double として安全に取得します。
 * 入力値が Int 型（"100" などドットなし入力時）であっても正しく Double へ変換します。
 */
internal fun Map<String, Any?>.getDouble(key: String): Double? {
    return when (val value = this[key]) {
        is Double -> value
        is Number -> value.toDouble()
        else -> null
    }
}

/**
 * Map 内の値を Int として安全に取得します。
 */
internal fun Map<String, Any?>.getInt(key: String): Int? {
    return when (val value = this[key]) {
        is Int -> value
        is Number -> value.toInt()
        else -> null
    }
}
