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

    @Test(expected = NullPointerException::class)
    fun REG_04_getByCategory_unsupported() {
        // 非 null 型の Enum に対して強引に null を渡して、Kotlin の実行時チェック（内部ガード）
        // が正しく機能し、安全にクラッシュ（保護）されることを検証する。
        // ※ 内部の ?: throw IllegalArgumentException に到達する前に Kotlin が NPE を投げる。
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
