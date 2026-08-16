package jp.mydns.fujiwara.carememo.logic.common

/**
 * Logic：PhoneLogic
 *
 * 【役割】
 * 日本の電話番号体系に基づいた、表示用のハイフン挿入ロジックを提供します。
 * 本クラスは純粋な Kotlin で記述され、Android フレームワークへの依存を持ちません。
 */
object PhoneLogic {

    /**
     * 電話番号に適切なハイフンを挿入して整形します。
     *
     * @param number 整形前の電話番号（数字のみであることを想定）
     * @return ハイフン付きの電話番号
     */
    fun formatPhoneNumber(number: String?): String? {
        if (number.isNullOrBlank()) return null

        val digits = number.filter { it.isDigit() }

        return when (digits.length) {
            11 ->  // 携帯・IP電話等 (3-4-4)
                "${digits.take(3)}-${digits.substring(3, 7)}-${digits.takeLast(4)}"

            10 -> {
                when {
                    digits.startsWith("03") || digits.startsWith("06") -> // 東京・大阪 (2-4-4)
                        "${digits.take(2)}-${digits.substring(2, 6)}-${digits.takeLast(4)}"

                    digits.startsWith("0120") || digits.startsWith("0800") || digits.startsWith("0570") -> // フリーダイヤル・ナビダイヤル等 (4-3-3)
                        "${digits.take(4)}-${digits.substring(4, 7)}-${digits.takeLast(3)}"

                    else -> // その他固定電話 (3-3-4)
                        "${digits.take(3)}-${digits.substring(3, 6)}-${digits.takeLast(4)}"
                }
            }

            else -> number // それ以外は整形せずそのまま返す
        }
    }

    /**
     * 入力中の文字列に対してハイフンを挿入すべき位置を判定する内部ロジック。
     * VisualTransformation と共有するために使用します。
     */
    fun getHyphenPositions(originalText: String): List<Int> {
        val digits = originalText.filter { it.isDigit() }
        val isElevenDigits = digits.length >= 11
        val isFreeDial = !isElevenDigits && (digits.startsWith("0120") || digits.startsWith("0800"))
        val isTwoDigitAreaCode = !isElevenDigits && !isFreeDial && 
                (digits.startsWith("03") || digits.startsWith("06"))

        return when {
            isElevenDigits -> listOf(2, 6)
            isFreeDial -> listOf(3, 6)
            isTwoDigitAreaCode -> listOf(1, 5)
            else -> listOf(2, 5)
        }
    }
}
