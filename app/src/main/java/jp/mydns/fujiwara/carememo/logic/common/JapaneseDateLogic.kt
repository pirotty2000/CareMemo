package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import java.time.DateTimeException
import java.time.LocalDate

/**
 * 日付バリデーションの結果定義。
 * 単なる成否だけでなく、エラーの原因を詳細に区別します。
 */
enum class DateValidationResult {
    /** 妥当な日付 */
    SUCCESS,
    /** 年が 0 以下 */
    INVALID_YEAR,
    /** 月が 1〜12 の範囲外 */
    INVALID_MONTH,
    /** 日がその月（または閏年）に存在しない（例：2月30日） */
    INVALID_DAY,
    /** 指定された元号の期間外（例：令和1年4月30日以前など） */
    INVALID_ERA_RANGE,
    /** アプリのサポート範囲外（1900年未満など） */
    OUT_OF_APP_RANGE
}

/**
 * Logic：JapaneseDateLogic
 *
 * 【役割】
 * 西暦と和暦（昭和・平成・令和）の相互変換、および日付の妥当性判定を行います。
 *
 * 【設計指針：Pure Kotlin / Android 非依存】
 * 1. 改元日を考慮した厳密な日付バリデーションを行い、歴史的な元号の範囲とカレンダー上の妥当性の両方を保証します。
 * 2. `AppSpecifications` で定義された改元日とオフセットに基づき、マジックナンバーを排除した変換ロジックを維持します。
 * 3. アプリのサポート範囲（1900年〜）をドメインルールとして適用し、不正な過去データ入力を水際で阻止します。
 */
object JapaneseDateLogic {

    private val calendarSpec = AppSpecifications.JapaneseCalendar
    private val showaSpec = AppSpecifications.JapaneseCalendar.Era.Showa
    private val heiseiSpec = AppSpecifications.JapaneseCalendar.Era.Heisei
    private val reiwaSpec = AppSpecifications.JapaneseCalendar.Era.Reiwa

    /**
     * 西暦から和暦と年に変換します。
     * 境界日（改元日）を基準に判定します。
     *
     * @param date 変換対象の西暦日付
     * @return 元号 (BirthEra) とその年数のペア
     */
    fun toJapaneseDate(date: LocalDate): Pair<BirthEra, Int> {
        return when {
            // 令和：2019/05/01〜
            date >= reiwaSpec.START_DATE ->
                BirthEra.REIWA to (date.year - reiwaSpec.OFFSET_YEAR)

            // 平成：1989/01/08〜2019/04/30
            date >= heiseiSpec.START_DATE ->
                BirthEra.HEISEI to (date.year - heiseiSpec.OFFSET_YEAR)

            // 昭和：1926/12/25〜1989/01/07
            date >= showaSpec.START_DATE ->
                BirthEra.SHOWA to (date.year - showaSpec.OFFSET_YEAR)

            // その他（1900年〜1926年）は西暦として扱う
            else ->
                BirthEra.AD to date.year
        }
    }

    /**
     * 和暦の日付を西暦（LocalDate）に変換します。
     *
     * @param era 元号
     * @param year 和暦の年
     * @param month 月
     * @param day 日
     * @return 変換後の LocalDate。不正な日付（バリデーション失敗時）の場合は null。
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
     * 1. 数値範囲、2. 物理的な日付存在（閏年等）、3. 元号期間の整合性、4. アプリサポート範囲
     * の順で厳密にチェックします。
     *
     * @param era 元号
     * @param year 年
     * @param month 月
     * @param day 日
     * @return 判定結果 (DateValidationResult)
     */
    fun validate(era: BirthEra, year: Int, month: Int, day: Int): DateValidationResult {
        if (year <= 0) return DateValidationResult.INVALID_YEAR
        if (month !in 1..12) return DateValidationResult.INVALID_MONTH

        // 1. 物理的な日付の存在チェック（カレンダー的に正しいか）
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

        // 2. 元号の期間妥当性チェック（改元日との不整合がないか）
        // 入力された元号と、西暦から逆算した本来の元号を比較する
        val (actualEra, _) = toJapaneseDate(date)
        if (era != BirthEra.AD && era != actualEra) {
            return DateValidationResult.INVALID_ERA_RANGE
        }

        // 3. アプリの制限（MIN_DATE 以降であるか）
        if (date.isBefore(calendarSpec.MIN_DATE)) {
            return DateValidationResult.OUT_OF_APP_RANGE
        }

        return DateValidationResult.SUCCESS
    }

    /**
     * 和暦日付が妥当かどうかを判定します（簡易判定用）。
     *
     * @return 妥当な場合は true
     */
    fun isValid(era: BirthEra, year: Int, month: Int, day: Int): Boolean {
        return validate(era, year, month, day) == DateValidationResult.SUCCESS
    }
}
