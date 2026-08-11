package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Category
import org.junit.Assert.*
import org.junit.Test

/**
 * Logic層テスト：HealthProcessorRegistry
 */
class HealthProcessorRegistryTest {

    @Test
    fun REG_01_getAll_success() {
        val all = HealthProcessorRegistry.getAll()
        assertEquals(3, all.size)
        assertTrue(all.contains(HeightWeightProcessor))
        assertTrue(all.contains(VitalProcessor))
        assertTrue(all.contains(GlucoseProcessor))
    }

    @Test
    fun REG_02_getByCategory_success() {
        val processor = HealthProcessorRegistry.getByCategory(BatchInputCategory.HEIGHT_WEIGHT)
        assertEquals(HeightWeightProcessor, processor)
    }

    @Test
    fun REG_03_getByGeneralCategory_success() {
        val processor = HealthProcessorRegistry.getByGeneralCategory(Category.BP_AND_PULSE)
        assertEquals(VitalProcessor, processor)
    }

    @Test(expected = IllegalArgumentException::class)
    fun REG_04_getByCategory_unsupported() {
        // 現在は全ての Enum 値がサポートされている想定だが、将来の拡張に備えたテスト
        // モック Enum などが使えないため、存在しない値を想定したロジック検証
        HealthProcessorRegistry.getByCategory(null as @Suppress("UNCHECKED_CAST") BatchInputCategory)
    }

    @Test
    fun REG_05_getByGeneralCategory_notFound() {
        val processor = HealthProcessorRegistry.getByGeneralCategory(Category.CONDITION_AT_VISIT)
        assertNull(processor)
    }
}
