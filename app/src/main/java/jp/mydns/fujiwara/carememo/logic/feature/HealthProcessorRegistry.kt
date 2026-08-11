package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Category

/**
 * 健康記録の各カテゴリプロセッサを管理するレジストリ。
 * 
 * カテゴリの追加・削除時には本レジストリを修正することで、
 * 呼び出し側のロジックを変更することなく機能拡張が可能です。
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
