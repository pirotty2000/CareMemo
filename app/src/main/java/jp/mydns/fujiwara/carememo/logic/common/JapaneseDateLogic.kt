package jp.mydns.fujiwara.carememo.logic.common

import java.time.LocalDate
import java.time.YearMonth

/**
 * 西暦と和暦の相互変換、および日付の妥当性判定を行うロジッククラス。
 */
object JapaneseDateLogic {

    private val SHOWA_START = LocalDate.of(1926, 12, 25)
    private val HEISEI_START = LocalDate.of(1989, 1, 8)
    private val REIWA_START = LocalDate.of(2019, 5, 1)

    /**
     * 西暦から和暦（元号・年）へ変換します。
     */
    fun toJapaneseDate(date: LocalDate): Pair<BirthEra, Int> {
        return when {
            !date.isBefore(REIWA_START) -> {
                BirthEra.REIWA to (date.year - 2018)
            }
            !date.isBefore(HEISEI_START) -> {
                BirthEra.HEISEI to (date.year - 1988)
            }
            !date.isBefore(SHOWA_START) -> {
                BirthEra.SHOWA to (date.year - 1925)
            }
            else -> {
                BirthEra.AD to date.year
            }
        }
    }

    /**
     * 和暦（元号・年・月・日）から西暦（LocalDate）へ変換します。
     * 存在しない日付や、元号の範囲外の場合は null を返します。
     */
    fun toLocalDate(era: BirthEra, year: Int, month: Int, day: Int): LocalDate? {
        if (month !in 1..12) return null

        val westernYear = when (era) {
            BirthEra.SHOWA -> year + 1925
            BirthEra.HEISEI -> year + 1988
            BirthEra.REIWA -> year + 2018
            BirthEra.AD -> year
        }

        val date = try {
            if (day !in 1..YearMonth.of(westernYear, month).lengthOfMonth()) return null
            LocalDate.of(westernYear, month, day)
        } catch (_: Exception) {
            return null
        }

        // 変換した日付が、指定された元号の範囲内にあるかチェック
        // ただし、入力が AD（西暦）の場合は、日付自体が有効であれば全期間許容する
        if (era != BirthEra.AD) {
            val (actualEra, actualYear) = toJapaneseDate(date)
            if (actualEra != era || actualYear != year) {
                return null
            }
        }

        return date
    }

    /**
     * 日付の妥当性を判定します。
     * 1900年以前の日付はアプリの制限として false とします。
     */
    fun isValid(era: BirthEra, year: Int, month: Int, day: Int): Boolean {
        val date = toLocalDate(era, year, month, day) ?: return false
        
        // 1900年以前は無効とする（アプリ仕様）
        if (date.isBefore(LocalDate.of(1900, 1, 1))) return false
        
        return true
    }

    /**
     * 年の数値を表示用にフォーマットします（1年を「元年」とする）。
     */
    fun formatYear(year: Int): String {
        return if (year == 1) "元年" else year.toString()
    }
}
