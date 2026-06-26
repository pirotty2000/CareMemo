package jp.mydns.fujiwara.carememo.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.JapaneseDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeUtils {
    /**
     * 日付を和暦付きでフォーマットする (例: 2023(令和5)年10月27日)
     */
    fun formatDateHeader(date: LocalDate): String {
        val eraDate = JapaneseDate.from(date)
        val eraName = eraDate.format(DateTimeFormatter.ofPattern("G").withLocale(Locale.JAPAN))
        val eraYear = eraDate[java.time.temporal.ChronoField.YEAR_OF_ERA]
        return "%d(%s%d)年%d月%d日".format(date.year, eraName, eraYear, date.monthValue, date.dayOfMonth)
    }

    /**
     * 時刻のみをフォーマットする (例: 14:30)
     */
    fun formatTime(instant: Instant): String =
        DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)

    /**
     * 記録日時を表示用（和暦付き）にフォーマットする
     * 例: 2023(令和5)年10月27日 14:30
     */
    fun formatRecordTime(instant: Instant): String {
        val zoneId = ZoneId.systemDefault()
        val localDateTime = instant.atZone(zoneId).toLocalDateTime()
        val localDate = localDateTime.toLocalDate()
        val eraDate = JapaneseDate.from(localDate)
        val eraYearFormatter = DateTimeFormatter.ofPattern("G").withLocale(Locale.JAPAN)
        val eraYear = eraDate[java.time.temporal.ChronoField.YEAR_OF_ERA]
        val eraName = eraDate.format(eraYearFormatter)
        return "%d(%s%d)年%d月%d日 %02d:%02d".format(
            localDate.year,
            eraName,
            eraYear,
            localDate.monthValue,
            localDate.dayOfMonth,
            localDateTime.hour,
            localDateTime.minute
        )
    }
}
