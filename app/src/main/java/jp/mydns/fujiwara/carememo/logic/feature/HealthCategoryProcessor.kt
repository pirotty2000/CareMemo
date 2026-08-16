package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import java.time.Instant

/**
 * 健康記録の各カテゴリ（身長体重、バイタル、血糖値等）固有の処理を抽象化するインターフェース。
 * 
 * 【設計指針：レイヤー責務と課題】
 * 1. 条件分岐の排除：バリデーション、Entity生成、表示用名称の解決をカプセル化し、上位レイヤーでの if/else を抑制します。
 * 2. アーキテクチャ境界の違反（注意）: 現状、本インターフェースおよび実装クラスが `HealthRepository` 
 *    に直接依存し、保存・削除（副作用）を実行しています。これは Dependency Matrix 違反（Logic -> Repository）
 *    であり、将来的に副作用を ViewModel へ押し出すリファクタリングが推奨されます。
 */
interface HealthCategoryProcessor {
    /** 処理対象の一括入力用カテゴリ */
    val category: BatchInputCategory

    /** 処理対象の汎用カテゴリ */
    val generalCategory: Category

    /** カテゴリの日本語名称リソースID */
    val categoryNameResId: Int

    /** 範囲外エラー時の詳細メッセージリソースID */
    val outOfRangeErrorResId: Int

    /** 一括入力画面の入力内容が空（保存対象外）かどうかを判定します。 */
    fun isEmpty(state: BatchInputUiState): Boolean

    /** 一括入力画面の入力内容のバリデーションを実行します。 */
    fun validate(state: BatchInputUiState): HealthInputValidationResult

    /** 一括入力画面の入力内容から DB 保存用の Entity オブジェクトを生成します。 */
    fun createEntity(personId: String, time: Instant, state: BatchInputUiState): Any?

    /** 入力値マップからのバリデーションを実行します（個別編集画面用）。 */
    fun validateFromMap(values: Map<String, String>): HealthInputValidationResult

    /** 入力値マップから DB 保存用の Entity オブジェクトを生成します（個別編集画面用）。 */
    fun createEntityFromValues(personId: String, id: String, time: Instant, values: Map<String, Any?>): Any

    /** Entity オブジェクトの数値範囲バリデーションを実行します。 */
    fun validateEntity(entity: Any): Boolean
}
