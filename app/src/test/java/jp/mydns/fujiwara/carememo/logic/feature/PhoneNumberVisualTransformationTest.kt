package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Logic層テスト：PhoneNumberVisualTransformation
 */
class PhoneNumberVisualTransformationTest {

    private val transformation = PhoneNumberVisualTransformation()

    // region 2. 電話番号整形テスト (filter)

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
    fun TR_03_filter_osakaFixed() {
        val result = transformation.filter(AnnotatedString("0612345678"))
        assertEquals("06-1234-5678", result.text.text)
    }

    @Test
    fun TR_04_filter_generalFixed() {
        val result = transformation.filter(AnnotatedString("0481234567"))
        assertEquals("048-123-4567", result.text.text)
    }

    @Test
    fun TR_05_filter_noTrailingHyphenAtBoundary() {
        val result = transformation.filter(AnnotatedString("090"))
        assertEquals("090", result.text.text)
    }

    @Test
    fun TR_06_filter_hyphenAppearsAtNextChar() {
        val result = transformation.filter(AnnotatedString("0901"))
        assertEquals("090-1", result.text.text)
    }

    @Test
    fun TR_07_filter_freeDial() {
        // 0120 should be formatted as 4-3-3
        val result = transformation.filter(AnnotatedString("0120123456"))
        assertEquals("0120-123-456", result.text.text)
    }

    @Test
    fun TR_08_filter_freeCall() {
        // 0800 is a 10-digit free dial (4-3-3)
        val result = transformation.filter(AnnotatedString("0800123456"))
        assertEquals("0800-123-456", result.text.text)
    }

    @Test
    fun TR_09_filter_mobile080() {
        // 080-1xxx-xxxx is a mobile phone (3-4-4)
        val result = transformation.filter(AnnotatedString("08012345678"))
        assertEquals("080-1234-5678", result.text.text)
    }

    // endregion

    // region 3. カーソル位置制御テスト (OffsetMapping)

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
    fun OS_03_offset_mobileThirdArea() {
        val result = transformation.filter(AnnotatedString("09012345678"))
        val mapping = result.offsetMapping
        assertEquals(10, mapping.originalToTransformed(8)) // 090-1234-56| (original 8 chars)
        assertEquals(8, mapping.transformedToOriginal(10))
    }

    @Test
    fun OS_04_offset_tokyoFixedSecondArea() {
        val result = transformation.filter(AnnotatedString("0312345678"))
        val mapping = result.offsetMapping
        // original index 2 ("1") is at transformed index 3 because of "03-"
        assertEquals(3, mapping.originalToTransformed(2))
        assertEquals(2, mapping.transformedToOriginal(3))
    }

    @Test
    fun OS_05_offset_boundaryEnd() {
        val result = transformation.filter(AnnotatedString("09012345678"))
        val mapping = result.offsetMapping
        assertEquals(13, mapping.originalToTransformed(11))
        assertEquals(11, mapping.transformedToOriginal(13))
    }

    // endregion
}
