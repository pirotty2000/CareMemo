package jp.mydns.fujiwara.carememo.utils

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.chrono.JapaneseDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * アプリ全体で使用される日時操作・フォーマット用のユーティリティ。
 * 和暦（令和など）の表示に標準で対応している。
 */
object DateTimeUtils {
    private val DEFAULT_ZONE = ZoneId.systemDefault()
    private val ERA_NAME_FORMATTER = DateTimeFormatter.ofPattern("G").withLocale(Locale.JAPAN)
    private val DOW_FORMATTER = DateTimeFormatter.ofPattern("(E)", Locale.JAPANESE)
    private val SHORT_DOW_FORMATTER = DateTimeFormatter.ofPattern("E", Locale.JAPANESE)
    private val SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yy/MM/dd").withZone(DEFAULT_ZONE)
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withZone(DEFAULT_ZONE)
    private val JAPANESE_ERA_FULL_FORMATTER = DateTimeFormatter.ofPattern("Gy年M月d日").withLocale(Locale.JAPAN)
    private val PHOTO_CAPTION_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(DEFAULT_ZONE)

    /**
     * 日付を和暦・曜日付きでフォーマットする (例: 2023(令和5)年10月27日(金))
     */
    fun formatDateHeader(date: LocalDate): String {
        val yearWithEra = getYearWithEra(date)
        val dayOfWeek = date.format(DOW_FORMATTER)
        return "${yearWithEra}${date.monthValue}月${date.dayOfMonth}日${dayOfWeek}"
    }

    /**
     * 年月を和暦付きでフォーマットする (例: 2023(令和5)年10月)
     */
    fun formatYearMonthHeader(yearMonth: YearMonth): String {
        val date = yearMonth.atDay(1)
        val yearWithEra = getYearWithEra(date)
        return "${yearWithEra}%02d月".format(yearMonth.monthValue)
    }

    /**
     * 短い曜日名のリストを取得する (日, 月, ..., 土)
     */
    fun getShortDayOfWeekNames(): List<String> {
        return listOf(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        ).map { it.getDisplayName(java.time.format.TextStyle.SHORT, Locale.JAPANESE) }
    }

    /**
     * 日付から短い曜日名を取得する (例: 金)
     */
    fun formatShortDayOfWeek(date: LocalDate): String = date.format(SHORT_DOW_FORMATTER)

    /**
     * グラフのX軸表示用 (例: 23/10/27)
     */
    fun formatDateShort(instant: Instant): String = SHORT_DATE_FORMATTER.format(instant)

    /**
     * 時刻のみをフォーマットする (例: 14:30)
     */
    fun formatTime(instant: Instant): String = TIME_FORMATTER.format(instant)

    /**
     * 記録日時を表示用（和暦・曜日付き）にフォーマットする
     * 例: 2023(令和5)年10月27日(金) 14:30:05
     */
    fun formatRecordTime(instant: Instant): String {
        val localDateTime = instant.atZone(DEFAULT_ZONE).toLocalDateTime()
        val dateHeader = formatDateHeader(localDateTime.toLocalDate())
        val time = "%02d:%02d:%02d".format(localDateTime.hour, localDateTime.minute, localDateTime.second)
        return "$dateHeader $time"
    }

    /**
     * 服薬ダイアログ用のタイトルをフォーマットする (例: 10月27日(金))
     */
    fun formatMedicationDialogTitle(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPANESE))
    }

    /**
     * 誕生日から年齢を計算する。
     * 時差の影響を避けるため、誕生日は常に UTC として扱う。
     */
    fun calculateAge(birthday: Instant): Int {
        val birthDate = birthday.atZone(ZoneOffset.UTC).toLocalDate()
        val now = LocalDate.now(DEFAULT_ZONE)
        return java.time.Period.between(birthDate, now).years
    }

    /**
     * 誕生日（Instant）を和暦形式でフォーマットする (例: 昭和25年1月1日)
     * 時差の影響を避けるため UTC として扱う。
     */
    fun formatDateJapaneseEra(instant: Instant): String {
        val localDate = instant.atZone(ZoneOffset.UTC).toLocalDate()
        val japaneseDate = JapaneseDate.from(localDate)
        return japaneseDate.format(JAPANESE_ERA_FULL_FORMATTER)
    }

    /**
     * 写真のキャプション用のデフォルト日時フォーマット (yyyy/MM/dd HH:mm)
     */
    fun formatPhotoCaption(instant: Instant): String = PHOTO_CAPTION_FORMATTER.format(instant)

    /**
     * 現在時刻を写真のキャプション用にフォーマットして返す
     */
    fun getCurrentPhotoCaption(): String = formatPhotoCaption(Instant.now())

    /**
     * 誕生日が現在から指定された日数以内（誕生日を含む）かどうかを判定する。
     * 時差の影響を避けるため誕生日は UTC。
     */
    fun isBirthdaySoon(birthday: Instant, daysIn: Int = 30): Boolean {
        val today = LocalDate.now(DEFAULT_ZONE)
        val birthDate = birthday.atZone(ZoneOffset.UTC).toLocalDate()

        var nextBirthday = birthDate.withYear(today.year)
        if (nextBirthday.isBefore(today)) {
            nextBirthday = nextBirthday.plusYears(1)
        }

        val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, nextBirthday)
        return daysUntil in 0..daysIn.toLong()
    }

    /**
     * 今日が誕生日かどうかを判定する
     */
    fun isBirthdayToday(birthday: Instant): Boolean {
        val today = LocalDate.now(DEFAULT_ZONE)
        val birthDate = birthday.atZone(ZoneOffset.UTC).toLocalDate()
        return today.monthValue == birthDate.monthValue && today.dayOfMonth == birthDate.dayOfMonth
    }

    /**
     * 誕生日を表示用にフォーマットする (例: 1950年1月1日 (昭和25年))
     * 時差の影響を避けるため UTC。
     */
    fun formatBirthday(birthday: Instant): String {
        val date = birthday.atZone(ZoneOffset.UTC).toLocalDate()
        val japaneseDate = JapaneseDate.from(date)
        val eraName = japaneseDate.format(ERA_NAME_FORMATTER)
        val eraYear = japaneseDate[ChronoField.YEAR_OF_ERA]
        return "${date.year}年${date.monthValue}月${date.dayOfMonth}日 (${eraName}${eraYear}年)"
    }

    /**
     * 生年月日を正規化する（時刻情報を切り捨てて UTC 00:00:00 に固定する）。
     * 入力元がいかなるタイムゾーンであっても、日付部分のみを UTC に固定して保存する。
     */
    fun normalizeBirthday(instant: Instant): Instant {
        // 入力時のタイムゾーンにおける「日付」を取得
        val localDate = instant.atZone(DEFAULT_ZONE).toLocalDate()
        // その日付の 00:00:00 UTC を作成して返す
        return localDate.atStartOfDay(ZoneOffset.UTC).toInstant()
    }

    /**
     * 西暦に和暦を添えた文字列を取得する (例: 2023(令和5)年)
     */
    private fun getYearWithEra(date: LocalDate): String {
        val eraDate = JapaneseDate.from(date)
        val eraName = eraDate.format(ERA_NAME_FORMATTER)
        val eraYear = eraDate[ChronoField.YEAR_OF_ERA]
        return "${date.year}(${eraName}${eraYear})年"
    }
}
