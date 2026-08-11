@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.utils

import android.content.Context
import android.net.Uri
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Unit Test: DateTimeUtils
 */
class DateTimeUtilsTest {

    private val birthdayZone = ZoneOffset.UTC

    @Before
    fun setup() {
        mockkObject(ImageUtils)
    }

    @After
    fun tearDown() {
        unmockkObject(ImageUtils)
    }

    // region 2. フォーマットテスト (Formatting)

    @Test
    fun FMT_01_formatDateHeader_includesEraAndDOW() {
        val date = LocalDate.of(2023, 10, 27) // Friday
        val result = DateTimeUtils.formatDateHeader(date)
        assertTrue(result.contains("令和5"))
        assertTrue(result.contains("(金)"))
    }

    @Test
    fun FMT_02_formatYearMonthHeader_includesEra() {
        val ym = YearMonth.of(2023, 10)
        val result = DateTimeUtils.formatYearMonthHeader(ym)
        assertTrue(result.contains("令和5"))
        assertTrue(result.contains("10月"))
    }

    @Test
    fun FMT_03_formatRecordTime_includesFullDetails() {
        val instant = Instant.parse("2023-10-27T14:30:05Z")
        // Note: formatRecordTime uses system default zone. 
        // We just check if it contains expected parts
        val result = DateTimeUtils.formatRecordTime(instant)
        assertTrue(result.contains("月"))
        assertTrue(result.contains("日"))
        assertTrue(result.contains(":"))
    }

    @Test
    fun FMT_04_formatDateShort_returnsCompactFormat() {
        val instant = Instant.parse("2023-10-27T10:00:00Z")
        val result = DateTimeUtils.formatDateShort(instant)
        assertTrue(result.matches(Regex("\\d{2}/\\d{2}/\\d{2}")))
    }

    @Test
    fun FMT_05_formatTime_returnsOnlyTime() {
        val instant = Instant.parse("2023-10-27T14:30:00Z")
        val result = DateTimeUtils.formatTime(instant)
        assertTrue(result.matches(Regex("\\d{2}:\\d{2}")))
    }

    @Test
    fun FMT_06_formatMedicationDialogTitle_isCorrect() {
        val date = LocalDate.of(2023, 10, 27)
        val result = DateTimeUtils.formatMedicationDialogTitle(date)
        assertEquals("10月27日(金)", result)
    }

    // endregion

    // region 3. 年齢・誕生日ロジックテスト (Birthday Logic)

    @Test
    fun BDT_01_calculateAge_isCorrect() {
        val now = LocalDate.now()
        val b1 = now.minusYears(20).atStartOfDay(birthdayZone).toInstant()
        assertEquals(20, DateTimeUtils.calculateAge(b1))
    }

    @Test
    fun BDT_04_isBirthdayToday_detectsCorrectDay() {
        val now = LocalDate.now()
        val bToday = now.minusYears(50).atStartOfDay(birthdayZone).toInstant()
        assertTrue(DateTimeUtils.isBirthdayToday(bToday))
    }

    @Test
    fun BDT_05_formatBirthday_includesEraInParenthesis() {
        val date = LocalDate.of(1950, 1, 1)
        val instant = date.atStartOfDay(birthdayZone).toInstant()
        assertEquals("1950年1月1日 (昭和25年)", DateTimeUtils.formatBirthday(instant))
    }

    @Test
    fun BDT_06_formatDateJapaneseEra_returnsEraOnly() {
        val date = LocalDate.of(1950, 1, 1)
        val instant = date.atStartOfDay(birthdayZone).toInstant()
        assertEquals("昭和25年1月1日", DateTimeUtils.formatDateJapaneseEra(instant))
    }

    // endregion

    // region 4. 正規化・UTC 処理テスト (Normalization)

    @Test
    fun NRM_01_normalizeBirthday_fixesToUtcMidnight() {
        // Use times that stay on the same day in most timezones (around noon UTC)
        val morning = Instant.parse("2023-10-27T08:00:00Z")
        val afternoon = Instant.parse("2023-10-27T14:00:00Z")
        
        val norm1 = DateTimeUtils.normalizeBirthday(morning)
        val norm2 = DateTimeUtils.normalizeBirthday(afternoon)
        
        assertEquals("Both times should normalize to the same date", norm1, norm2)
        assertTrue("Normalized time should be UTC midnight", norm1.toString().endsWith("T00:00:00Z"))
    }

    // endregion

    // region 5. 写真キャプションテスト (Photo Caption)

    @Test
    fun CPT_01_formatPhotoCaption_isCorrect() {
        val instant = Instant.parse("2023-10-27T10:00:00Z")
        val result = DateTimeUtils.formatPhotoCaption(instant)
        assertTrue(result.matches(Regex("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}")))
    }

    @Test
    fun CPT_02_getPhotoCaption_usesImageMetadata() {
        val context = mockk<Context>()
        val uri = mockk<Uri>()
        // Use a time that stays on the same date in most timezones (around noon UTC)
        val mockTime = Instant.parse("2023-10-27T05:00:00Z").toEpochMilli()
        every { ImageUtils.getCaptureTime(context, uri) } returns mockTime
        val caption = DateTimeUtils.getPhotoCaption(context, uri)
        assertTrue("Caption should contain the correct date string: $caption", caption.contains("2023/10/27"))
    }

    // endregion
}
