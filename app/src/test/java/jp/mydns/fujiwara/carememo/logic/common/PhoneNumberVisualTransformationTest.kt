package jp.mydns.fujiwara.carememo.logic.common

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PhoneNumberVisualTransformation のユニットテスト
 */
class PhoneNumberVisualTransformationTest {

    private val transformation = PhoneNumberVisualTransformation()

    @Test
    fun `携帯電話11桁が正しく整形されること`() {
        val result = transformation.filter(AnnotatedString("09012345678"))
        assertEquals("090-1234-5678", result.text.text)
    }

    @Test
    fun `東京03の10桁が正しく整形されること`() {
        val result = transformation.filter(AnnotatedString("0312345678"))
        assertEquals("03-1234-5678", result.text.text)
    }

    @Test
    fun `一般市外局番3桁の10桁が正しく整形されること`() {
        val result = transformation.filter(AnnotatedString("0481234567"))
        assertEquals("048-123-4567", result.text.text)
    }

    @Test
    fun `入力途中の末尾にハイフンが表示されないこと`() {
        val result = transformation.filter(AnnotatedString("090"))
        assertEquals("090", result.text.text)
    }

    @Test
    fun `offsetMapping - 11桁の変換が正しいこと`() {
        val result = transformation.filter(AnnotatedString("09012345678"))
        val mapping = result.offsetMapping
        
        // originalToTransformed
        assertEquals(2, mapping.originalToTransformed(2)) // 09|
        assertEquals(5, mapping.originalToTransformed(4)) // 090-1|
        assertEquals(10, mapping.originalToTransformed(8)) // 090-1234-56|
        
        // transformedToOriginal
        assertEquals(2, mapping.transformedToOriginal(2))
        assertEquals(4, mapping.transformedToOriginal(5))
        assertEquals(8, mapping.transformedToOriginal(10))
    }
}
