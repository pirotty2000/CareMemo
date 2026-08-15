package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Category

/**
 * 健康記録の各カテゴリプロセッサを管理するレジストリ。
 * 
 * 【設計指針：Open-Closed Principle】
 * 1. 拡張性：健康記録のカテゴリ追加時には、本レジストリに新しいプロセッサを登録するだけで、
 *    ViewModel や一括入力ロジックなどの既存コードを修正することなく機能拡張が可能です。
 * 2. 責務の集中：各カテゴリ固有の知識（名称、バリデーション、Entity変換）をプロセッサに封じ込め、
 *    レジストリを介して一貫したインターフェースでアクセスすることを保証します。
 */
object HealthProcessorRegistry {
    private val processors = listOf(
        HeightWeightProcessor,
        VitalProcessor,
        GlucoseProcessor,
    )

    /**
     * すべてのプロセッサを取得します。
     */
    fun getAll(): List<HealthCategoryProcessor> = processors

    /**
     * 指定された一括入力用カテゴリに対応するプロセッサを取得します。
     * 
     * @param category 対象カテゴリ
     * @return 対応する [HealthCategoryProcessor]
     * @throws IllegalArgumentException 未サポートのカテゴリが指定された場合
     */
    fun getByCategory(category: BatchInputCategory): HealthCategoryProcessor {
        return processors.find { it.category == category }
            ?: throw IllegalArgumentException("Unsupported batch category: $category")
    }

    /**
     * 指定された汎用カテゴリに対応するプロセッサを取得します。
     * 
     * @param category 対象カテゴリ
     * @return 対応する [HealthCategoryProcessor]、存在しない場合は null
     */
    fun getByGeneralCategory(category: Category): HealthCategoryProcessor? {
        return processors.find { it.generalCategory == category }
    }
}
