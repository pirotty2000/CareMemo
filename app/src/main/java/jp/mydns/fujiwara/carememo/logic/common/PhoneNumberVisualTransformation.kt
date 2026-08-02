package jp.mydns.fujiwara.carememo.logic.common

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
 * 【主な機能】
 * ・11桁（携帯電話・IP電話：090-xxxx-xxxx 等）の自動フォーマット。
 * ・10桁かつ市外局番2桁（東京03, 大阪06等：03-xxxx-xxxx）の自動フォーマット。
 * ・その他の10桁（地方の市外局番：0xx-xxx-xxxx 等）の自動フォーマット。
 * ・入力カーソル位置と表示文字列の整合性を保つためのオフセットマッピング。
 *
 * 【設計指針】
 * 1. 保存されるデータ（Raw文字列）には影響を与えず、表示のみを加工する。
 * 2. 入力中の末尾ハイフンは視覚的な違和感を避けるために削除する。
 * 3. offsetMapping を厳密に定義し、ハイフン挿入後もカーソル移動が不自然にならないように制御する。
 */
class PhoneNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        val transformedText = StringBuilder()

        // 日本の主要なパターンを判別
        // 11桁 (携帯等) なら 3-4-4
        // 10桁かつ市外局番2桁 (東京03, 大阪06) なら 2-4-4
        // それ以外 (基本10桁) なら 3-3-4 を基本とする
        val isElevenDigits = originalText.length >= 11
        val isTwoDigitAreaCode = !isElevenDigits && 
                (originalText.startsWith("03") || originalText.startsWith("06"))

        for (i in originalText.indices) {
            transformedText.append(originalText[i])
            when {
                isElevenDigits -> {
                    // 3桁目(index 2)と7桁目(index 6)の後にハイフン
                    if (i == 2 || i == 6) transformedText.append("-")
                }
                isTwoDigitAreaCode -> {
                    // 2桁目(index 1)と6桁目(index 5)の後にハイフン
                    if (i == 1 || i == 5) transformedText.append("-")
                }
                else -> {
                    // 3桁目(index 2)と6桁目(index 5)の後にハイフン (地方市外局番想定)
                    if (i == 2 || i == 5) transformedText.append("-")
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
                        if (offset > 3) hyphenCount++
                        if (offset > 7) hyphenCount++
                    }
                    isTwoDigitAreaCode -> {
                        if (offset > 2) hyphenCount++
                        if (offset > 6) hyphenCount++
                    }
                    else -> {
                        if (offset > 3) hyphenCount++
                        if (offset > 6) hyphenCount++
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
                    isTwoDigitAreaCode -> {
                        if (offset > 2) hyphenCount++
                        if (offset > 7) hyphenCount++
                    }
                    else -> {
                        if (offset > 3) hyphenCount++
                        if (offset > 7) hyphenCount++
                    }
                }
                val originalOffset = offset - hyphenCount
                return originalOffset.coerceIn(0, originalText.length)
            }
        }

        return TransformedText(AnnotatedString(transformedText.toString()), offsetMapping)
    }
}
