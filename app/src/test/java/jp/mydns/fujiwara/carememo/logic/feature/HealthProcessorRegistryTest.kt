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
        // 非 null 型の Enum に対して強引に null を渡してセーフティネット（?: throw）を検証する。
        // コンパイラの警告を避けるため、リフレクションを使用して呼び出す。
        val method = HealthProcessorRegistry::class.java.getMethod("getByCategory", BatchInputCategory::class.java)
        try {
            method.invoke(HealthProcessorRegistry, null as BatchInputCategory?)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }

    @Test
    fun REG_05_getByGeneralCategory_notFound() {
        val processor = HealthProcessorRegistry.getByGeneralCategory(Category.CONDITION_AT_VISIT)
        assertNull(processor)
    }
}
