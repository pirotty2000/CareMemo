package jp.mydns.fujiwara.carememo.ui.utils

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * UI層テスト：PhoneNumberVisualTransformation
 *
 * 入力中のリアルタイムハイフン挿入と、それに伴うカーソル位置（OffsetMapping）の変換を検証します。
 */
class PhoneNumberVisualTransformationTest {

    private val transformation = PhoneNumberVisualTransformation()

    // region 1. 電話番号整形テスト (filter)

    @Test
    fun TR_01_filter_mobile11Digits() {
        val result = transformation.filter(AnnotatedString("09012345678"))
        assertEquals("090-1234-5678", result.text.text)
    }

    @Test
    fun TR_02_filter_tokyoFixed() {
        val result = transformation.filter(AnnotatedString("0312345678"))
        assertEquals("03-1234-5678", result.text.text)
    }

    @Test
    fun TR_03_filter_freeDial() {
        // 0120 は 4-3-3 形式でリアルタイム整形されること
        val result = transformation.filter(AnnotatedString("0120123456"))
        assertEquals("0120-123-456", result.text.text)
    }

    @Test
    fun TR_04_filter_noTrailingHyphenAtBoundary() {
        val result = transformation.filter(AnnotatedString("090"))
        assertEquals("090", result.text.text)
    }

    @Test
    fun TR_05_filter_hyphenAppearsAtNextChar() {
        val result = transformation.filter(AnnotatedString("0901"))
        assertEquals("090-1", result.text.text)
    }

    // endregion

    // region 2. カーソル位置制御テスト (OffsetMapping)

    @Test
    fun OS_01_offset_mobileFirstArea() {
        val result = transformation.filter(AnnotatedString("09012345678"))
        val mapping = result.offsetMapping
        assertEquals(2, mapping.originalToTransformed(2)) // 09|
        assertEquals(2, mapping.transformedToOriginal(2))
    }

    @Test
    fun OS_02_offset_mobileSecondArea() {
        val result = transformation.filter(AnnotatedString("09012345678"))
        val mapping = result.offsetMapping
        assertEquals(5, mapping.originalToTransformed(4)) // 090-1| (original 4 chars)
        assertEquals(4, mapping.transformedToOriginal(5))
    }

    @Test
    fun OS_03_offset_freeDialSecondArea() {
        val result = transformation.filter(AnnotatedString("0120123"))
        val mapping = result.offsetMapping
        // original index 4 ("1") is at transformed index 5 because of "0120-"
        assertEquals(5, mapping.originalToTransformed(4))
        assertEquals(4, mapping.transformedToOriginal(5))
    }

    @Test
    fun OS_04_offset_boundaryEnd() {
        val result = transformation.filter(AnnotatedString("09012345678"))
        val mapping = result.offsetMapping
        assertEquals(13, mapping.originalToTransformed(11))
        assertEquals(11, mapping.transformedToOriginal(13))
    }

    // endregion
}
