package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import java.time.DateTimeException
import java.time.LocalDate

/**
 * 日付バリデーションの結果（事実）
 */
enum class DateValidationResult {
    SUCCESS,
    INVALID_YEAR,
    INVALID_MONTH,
    INVALID_DAY,
    INVALID_ERA_RANGE, // 元号の期間外（例：令和1年4月）
    OUT_OF_APP_RANGE   // アプリのサポート範囲外（1900年未満など）
}

/**
 * 西暦と和暦の相互変換、および日付妥当性の判定ロジック。
 */
object JapaneseDateLogic {

    private val calendarSpec = AppSpecifications.JapaneseCalendar
    private val showaSpec = AppSpecifications.JapaneseCalendar.Era.Showa
    private val heiseiSpec = AppSpecifications.JapaneseCalendar.Era.Heisei
    private val reiwaSpec = AppSpecifications.JapaneseCalendar.Era.Reiwa

    /**
     * 西暦から和暦と年に変換します。
     */
    fun toJapaneseDate(date: LocalDate): Pair<BirthEra, Int> {
        return when {
            date >= reiwaSpec.START_DATE ->
                BirthEra.REIWA to (date.year - reiwaSpec.OFFSET_YEAR)

            date >= heiseiSpec.START_DATE ->
                BirthEra.HEISEI to (date.year - heiseiSpec.OFFSET_YEAR)

            date >= showaSpec.START_DATE ->
                BirthEra.SHOWA to (date.year - showaSpec.OFFSET_YEAR)

            else ->
                BirthEra.AD to date.year
        }
    }

    /**
     * 和暦の日付を西暦（LocalDate）に変換します。
     * 不正な日付の場合は null を返します。
     */
    fun toLocalDate(era: BirthEra, year: Int, month: Int, day: Int): LocalDate? {
        if (validate(era, year, month, day) != DateValidationResult.SUCCESS) return null

        val adYear = when (era) {
            BirthEra.SHOWA -> year + showaSpec.OFFSET_YEAR
            BirthEra.HEISEI -> year + heiseiSpec.OFFSET_YEAR
            BirthEra.REIWA -> year + reiwaSpec.OFFSET_YEAR
            BirthEra.AD -> year
        }

        return try {
            LocalDate.of(adYear, month, day)
        } catch (_: DateTimeException) {
            null
        }
    }

    /**
     * 和暦日付の妥当性を詳細に判定します。
     */
    fun validate(era: BirthEra, year: Int, month: Int, day: Int): DateValidationResult {
        if (year <= 0) return DateValidationResult.INVALID_YEAR
        if (month !in 1..12) return DateValidationResult.INVALID_MONTH

        // 1. 物理的な日付の存在チェック
        val adYear = when (era) {
            BirthEra.SHOWA -> year + showaSpec.OFFSET_YEAR
            BirthEra.HEISEI -> year + heiseiSpec.OFFSET_YEAR
            BirthEra.REIWA -> year + reiwaSpec.OFFSET_YEAR
            BirthEra.AD -> year
        }

        val date = try {
            LocalDate.of(adYear, month, day)
        } catch (_: DateTimeException) {
            return DateValidationResult.INVALID_DAY
        }

        // 2. 元号の期間妥当性チェック
        val (actualEra, _) = toJapaneseDate(date)
        if (era != BirthEra.AD && era != actualEra) {
            return DateValidationResult.INVALID_ERA_RANGE
        }

        // 3. アプリの制限（MIN_DATE 以降）
        if (date.isBefore(calendarSpec.MIN_DATE)) {
            return DateValidationResult.OUT_OF_APP_RANGE
        }

        return DateValidationResult.SUCCESS
    }

    /**
     * 和暦日付が妥当かどうかを判定します（旧来の互換用）。
     */
    fun isValid(era: BirthEra, year: Int, month: Int, day: Int): Boolean {
        return validate(era, year, month, day) == DateValidationResult.SUCCESS
    }
}
