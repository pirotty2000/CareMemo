package jp.mydns.fujiwara.carememo.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.JapaneseDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeUtils {
    /**
     * 日付を和暦・曜日付きでフォーマットする (例: 2023(令和5)年10月27日(金))
     */
    fun formatDateHeader(date: LocalDate): String {
        val eraDate = JapaneseDate.from(date)
        val eraName = eraDate.format(DateTimeFormatter.ofPattern("G").withLocale(Locale.JAPAN))
        val eraYear = eraDate[java.time.temporal.ChronoField.YEAR_OF_ERA]
        val dayOfWeek = date.format(DateTimeFormatter.ofPattern("(E)", Locale.JAPANESE))
        return "%d(%s%d)年%d月%d日%s".format(date.year, eraName, eraYear, date.monthValue, date.dayOfMonth, dayOfWeek)
    }

    /**
     * 年月を和暦付きでフォーマットする (例: 2023(令和5)年10月)
     */
    fun formatYearMonthHeader(yearMonth: java.time.YearMonth): String {
        val localDate = yearMonth.atDay(1)
        val eraDate = JapaneseDate.from(localDate)
        val eraName = eraDate.format(DateTimeFormatter.ofPattern("G").withLocale(Locale.JAPAN))
        val eraYear = eraDate[java.time.temporal.ChronoField.YEAR_OF_ERA]
        return "%d(%s%d)年%02d月".format(yearMonth.year, eraName, eraYear, yearMonth.monthValue)
    }

    /**
     * 短い曜日名のリストを取得する (日, 月, ..., 土)
     */
    fun getShortDayOfWeekNames(): List<String> =
        listOf("日", "月", "火", "水", "木", "金", "土")

    /**
     * 日付から短い曜日名を取得する (例: 金)
     */
    fun formatShortDayOfWeek(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("E", Locale.JAPANESE))

    /**
     * グラフのX軸表示用 (例: 23/10/27)
     */
    fun formatDateShort(instant: Instant): String =
        DateTimeFormatter.ofPattern("yy/MM/dd")
            .withZone(ZoneId.systemDefault())
            .format(instant)

    /**
     * 時刻のみをフォーマットする (例: 14:30)
     */
    fun formatTime(instant: Instant): String =
        DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)

    /**
     * 記録日時を表示用（和暦・曜日付き）にフォーマットする
     * 例: 2023(令和5)年10月27日(金) 14:30
     */
    fun formatRecordTime(instant: Instant): String {
        val zoneId = ZoneId.systemDefault()
        val localDateTime = instant.atZone(zoneId).toLocalDateTime()
        val localDate = localDateTime.toLocalDate()
        val eraDate = JapaneseDate.from(localDate)
        val eraYearFormatter = DateTimeFormatter.ofPattern("G").withLocale(Locale.JAPAN)
        val eraYear = eraDate[java.time.temporal.ChronoField.YEAR_OF_ERA]
        val eraName = eraDate.format(eraYearFormatter)
        val dayOfWeek = localDate.format(DateTimeFormatter.ofPattern("(E)", Locale.JAPANESE))
        return "%d(%s%d)年%d月%d日%s %02d:%02d".format(
            localDate.year,
            eraName,
            eraYear,
            localDate.monthValue,
            localDate.dayOfMonth,
            dayOfWeek,
            localDateTime.hour,
            localDateTime.minute,
        )
    }

    /**
     * 服薬ダイアログ用のタイトルをフォーマットする (例: 10月27日(金))
     */
    fun formatMedicationDialogTitle(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPANESE))
    }

    /**
     * 誕生日から年齢を計算する
     */
    fun calculateAge(birthday: Instant): Int {
        val birthDate = birthday.atZone(ZoneId.systemDefault()).toLocalDate()
        val now = LocalDate.now()
        return java.time.Period.between(birthDate, now).years
    }

    /**
     * 写真のキャプション用のデフォルト日時フォーマット (yyyy/MM/dd HH:mm)
     */
    fun formatPhotoCaption(instant: Instant): String {
        return DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }

    /**
     * 現在時刻を写真のキャプション用にフォーマットして返す
     */
    fun getCurrentPhotoCaption(): String = formatPhotoCaption(Instant.now())
}
