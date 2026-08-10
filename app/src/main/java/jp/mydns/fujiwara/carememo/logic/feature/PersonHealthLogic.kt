package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.runtime.Immutable
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import jp.mydns.fujiwara.carememo.logic.common.HealthLogic
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
    val initialSnapshot: HealthEditInput? = null,
    val isChanged: Boolean = false,
    val isSaveEnabled: Boolean = false
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
 * 【主な機能】
 * ・各記録型（身長体重、バイタル、血糖）に応じた数値範囲の妥当性判定。
 * ・同一日時における重複レコードの検出。
 * ・UI側での保存ボタン制御用のリアルタイムバリデーション。
 * ・入力値からの適切な Entity（HeightAndWeight等）の生成と ID 管理。
 *
 * 【設計指針】
 * 1. 数値の妥当性判定は、AppSpecifications に定義された各項目の MIN/MAX 値を基準とする。
 * 2. 重複チェックでは、編集中の自分自身（ID一致）を除外することで、日時の変わらない更新を許容する。
 * 3. createEntity メソッドにおいて、新規レコードに対する UUID 発行の責任を一元管理する。
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

        val isValid = when (record) {
            is HeightAndWeight -> {
                val hSpec = AppSpecifications.Health.Height
                val wSpec = AppSpecifications.Health.Weight
                (record.height == null || record.height in hSpec.MIN_VALUE..hSpec.MAX_VALUE) &&
                (record.weight == null || record.weight in wSpec.MIN_VALUE..wSpec.MAX_VALUE)
            }
            is BpAndPulse -> {
                val bpSpec = AppSpecifications.Health.BloodPressure
                val pulseSpec = AppSpecifications.Health.Pulse
                val satSpec = AppSpecifications.Health.OxygenSaturation
                val tempSpec = AppSpecifications.Health.BodyTemperature
                (record.bpSystolic == null || record.bpSystolic.toDouble() in bpSpec.MIN_VALUE..bpSpec.MAX_VALUE) &&
                (record.bpDiastolic == null || record.bpDiastolic.toDouble() in bpSpec.MIN_VALUE..bpSpec.MAX_VALUE) &&
                (record.pulse == null || record.pulse.toDouble() in pulseSpec.MIN_VALUE..pulseSpec.MAX_VALUE) &&
                (record.sat == null || record.sat.toDouble() in satSpec.MIN_VALUE..satSpec.MAX_VALUE) &&
                (record.bodyTemperature == null || record.bodyTemperature in tempSpec.MIN_VALUE..tempSpec.MAX_VALUE)
            }
            is GlucoseAndHbA1c -> {
                val gSpec = AppSpecifications.Health.BloodGlucose
                val hSpec = AppSpecifications.Health.HbA1c
                (record.glucose == null || record.glucose.toDouble() in gSpec.MIN_VALUE..gSpec.MAX_VALUE) &&
                (record.hba1c == null || record.hba1c in hSpec.MIN_VALUE..hSpec.MAX_VALUE)
            }
            else -> true
        }

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
        return when (category) {
            Category.HEIGHT_AND_WEIGHT -> HealthLogic.validateHeightAndWeight(
                values["height"] ?: "",
                values["weight"] ?: ""
            )
            Category.BP_AND_PULSE -> HealthLogic.validateBpAndPulse(
                values["bpSystolic"] ?: "",
                values["bpDiastolic"] ?: "",
                values["sat"] ?: "",
                values["pulse"] ?: "",
                values["bodyTemperature"] ?: ""
            )
            Category.GLUCOSE_AND_HBA1C -> HealthLogic.validateGlucoseAndHbA1c(
                values["glucose"] ?: "",
                values["hba1c"] ?: ""
            )
            else -> HealthInputValidationResult.SUCCESS
        }
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
        
        return when (category) {
            Category.HEIGHT_AND_WEIGHT -> HeightAndWeight(id = finalId, personId = personId, height = values["height"] as? Double, weight = values["weight"] as? Double, recordTime = recordTime)
            Category.BP_AND_PULSE -> BpAndPulse(id = finalId, personId = personId, bpSystolic = values["bpSystolic"] as? Int, bpDiastolic = values["bpDiastolic"] as? Int, sat = values["sat"] as? Int, pulse = values["pulse"] as? Int, bodyTemperature = values["bodyTemperature"] as? Double, recordTime = recordTime)
            Category.GLUCOSE_AND_HBA1C -> GlucoseAndHbA1c(id = finalId, personId = personId, glucose = values["glucose"] as? Int, hba1c = values["hba1c"] as? Double, recordTime = recordTime)
            else -> throw IllegalArgumentException("Unsupported category")
        }
    }
}
