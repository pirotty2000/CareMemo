@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.utils

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * DateTimeUtils のロジックを検証する単体テスト
 */
class DateTimeUtilsTest {

    // 誕生日はアプリの仕様（DateTimeUtils）に合わせて UTC 基準でテストデータを作成する
    private val birthdayZone = ZoneOffset.UTC

    @Test
    fun formatDateHeader_和暦と曜日が正しく付与される() {
        val date = LocalDate.of(2023, 10, 27) // 金曜日
        val result = DateTimeUtils.formatDateHeader(date)
        // 期待値: 2023(令和5)年10月27日(金)
        assertTrue(result.contains("2023"))
        assertTrue(result.contains("令和5"))
        assertTrue(result.contains("10月27日"))
        assertTrue(result.contains("(金)"))
    }

    @Test
    fun formatYearMonthHeader_和暦が正しく付与される() {
        val ym = YearMonth.of(2023, 10)
        val result = DateTimeUtils.formatYearMonthHeader(ym)
        // 期待値: 2023(令和5)年10月
        assertTrue(result.contains("2023"))
        assertTrue(result.contains("令和5"))
        assertTrue(result.contains("10月"))
    }

    @Test
    fun calculateAge_年齢が正しく計算される() {
        // テスト実行時の「今日」に依存しないように相対的な値でテストする
        // 実装側は LocalDate.now(systemDefault) を使用しているため、ここでの now はローカル基準
        val now = LocalDate.now()
        
        // 20年前の今日生まれ
        val b1 = now.minusYears(20).atStartOfDay(birthdayZone).toInstant()
        assertEquals(20, DateTimeUtils.calculateAge(b1))

        // 20年前の明日生まれ（まだ19歳）
        val b2 = now.minusYears(20).plusDays(1).atStartOfDay(birthdayZone).toInstant()
        assertEquals(19, DateTimeUtils.calculateAge(b2))
    }

    @Test
    fun isBirthdaySoon_誕生日が近い判定() {
        val now = LocalDate.now()
        
        // 今日
        val bToday = now.atStartOfDay(birthdayZone).toInstant()
        assertTrue(DateTimeUtils.isBirthdaySoon(bToday, 30))

        // 29日後
        val bSoon = now.plusDays(29).atStartOfDay(birthdayZone).toInstant()
        assertTrue(DateTimeUtils.isBirthdaySoon(bSoon, 30))

        // 31日後
        val bFar = now.plusDays(31).atStartOfDay(birthdayZone).toInstant()
        assertFalse(DateTimeUtils.isBirthdaySoon(bFar, 30))
    }

    @Test
    fun isBirthdayToday_当日の判定() {
        val now = LocalDate.now()
        
        // 今日の日付 (年は違っても良い)
        val bToday = now.minusYears(50).atStartOfDay(birthdayZone).toInstant()
        assertTrue(DateTimeUtils.isBirthdayToday(bToday))

        // 明日の日付
        val bTomorrow = now.plusDays(1).atStartOfDay(birthdayZone).toInstant()
        assertFalse(DateTimeUtils.isBirthdayToday(bTomorrow))
    }

    @Test
    fun formatBirthday_和暦併記のフォーマット() {
        val date = LocalDate.of(1950, 1, 1)
        val instant = date.atStartOfDay(birthdayZone).toInstant()
        val result = DateTimeUtils.formatBirthday(instant)
        
        // 期待値: 1950年1月1日 (昭和25年)
        assertEquals("1950年1月1日 (昭和25年)", result)
    }

    @Test
    fun normalizeBirthday_同じ日の異なる時刻が全て同じInstantに変換されること() {
        // 同じ日 (2023-10-27) の異なる時刻
        // タイムゾーンの影響を受けにくいよう、お昼前後の時間を使用
        val morning = Instant.parse("2023-10-27T10:00:00Z")
        val afternoon = Instant.parse("2023-10-27T14:00:00Z")
        
        val norm1 = DateTimeUtils.normalizeBirthday(morning)
        val norm2 = DateTimeUtils.normalizeBirthday(afternoon)
        
        assertEquals("午前と午後でも同じ正規化結果になること", norm1, norm2)
        assertTrue("時刻部分が00:00:00Zであること", norm1.toString().endsWith("T00:00:00Z"))
    }

    @Test
    fun normalizeBirthday_時刻が切り捨てられUTC固定になる() {
        val dateStr = "2023-10-27T15:30:00Z"
        val instant = Instant.parse(dateStr)
        
        val normalized = DateTimeUtils.normalizeBirthday(instant)
        
        // 時刻部分が 00:00:00Z になっていることを確認する
        assertTrue(normalized.toString().endsWith("T00:00:00Z"))
    }
}
