package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Logic：PhoneNumberVisualTransformation
 *
 * 【役割】
 * 入力された電話番号の数字列に対して、日本の主要な電話番号パターンに基づいたハイフンを動的に挿入し、
 * ユーザーが読みやすい形式で表示するための VisualTransformation を提供します。
 *
 * 【設計指針：レイヤー責務と課題】
 * 1. 表示ロジック：保存されるデータには影響を与えず、表示のみを加工します。
 * 2. 配置の不整合（注意）: 本クラスは Compose UI (`VisualTransformation`) に直接依存しており、
 *    本来は `ui/utils` または `ui/mapping` に配置されるべき表示ロジックです。
 *    将来的なパッケージ整理の対象です。
 */
class PhoneNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        val transformedText = StringBuilder()

        // 日本の主要なパターンを判別
        // 11桁 (携帯等) なら 3-4-4
        // 10桁かつ市外局番2桁 (東京03, 大阪06) なら 2-4-4
        // 0120/0800 (フリーダイヤル等) なら 4-3-3
        // それ以外 (基本10桁) なら 3-3-4 を基本とする
        val isElevenDigits = originalText.length >= 11
        val isFreeDial = !isElevenDigits && (originalText.startsWith("0120") || originalText.startsWith("0800"))
        val isTwoDigitAreaCode = !isElevenDigits && !isFreeDial && 
                (originalText.startsWith("03") || originalText.startsWith("06"))

        for (i in originalText.indices) {
            transformedText.append(originalText[i])
            when {
                isElevenDigits -> {
                    if (i == 2 || i == 6) transformedText.append("-")
                }
                isFreeDial -> {
                    // 4桁目(index 3)と7桁目(index 6)の後にハイフン
                    if (i == 3 || i == 6) transformedText.append("-")
                }
                isTwoDigitAreaCode -> {
                    if (i == 1 || i == 5) transformedText.append("-")
                }
                else -> {
                    if (i == 2) transformedText.append("-")
                    if (i == 5 && originalText.length > 7) transformedText.append("-")
                }
            }
        }

        // 入力中の最後がハイフンの場合は削除して、数値だけで終わるように見せる
        if (transformedText.endsWith("-")) {
            transformedText.deleteCharAt(transformedText.length - 1)
        }

        /**
         * オリジナルのインデックスとハイフン挿入後のインデックスを相互に変換するマッピング定義
         */
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var hyphenCount = 0
                when {
                    isElevenDigits -> {
                        if (offset > 2) hyphenCount++
                        if (offset > 6) hyphenCount++
                    }
                    isFreeDial -> {
                        if (offset > 3) hyphenCount++
                        if (offset > 6) hyphenCount++
                    }
                    isTwoDigitAreaCode -> {
                        if (offset > 1) hyphenCount++
                        if (offset > 5) hyphenCount++
                    }
                    else -> {
                        if (offset > 2) hyphenCount++
                        if (offset > 5 && originalText.length > 7) hyphenCount++
                    }
                }
                return offset + hyphenCount
            }

            override fun transformedToOriginal(offset: Int): Int {
                var hyphenCount = 0
                when {
                    isElevenDigits -> {
                        if (offset > 3) hyphenCount++
                        if (offset > 8) hyphenCount++
                    }
                    isFreeDial -> {
                        if (offset > 4) hyphenCount++
                        if (offset > 8) hyphenCount++
                    }
                    isTwoDigitAreaCode -> {
                        if (offset > 2) hyphenCount++
                        if (offset > 7) hyphenCount++
                    }
                    else -> {
                        if (offset > 3) hyphenCount++
                        if (offset > 7 && originalText.length > 7) hyphenCount++
                    }
                }
                val originalOffset = offset - hyphenCount
                return originalOffset.coerceIn(0, originalText.length)
            }
        }

        return TransformedText(AnnotatedString(transformedText.toString()), offsetMapping)
    }
}
