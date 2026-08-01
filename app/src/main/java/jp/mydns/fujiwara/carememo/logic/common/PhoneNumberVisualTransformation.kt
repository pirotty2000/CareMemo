package jp.mydns.fujiwara.carememo.logic.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * 電話番号を動的にハイフン形式 (090-xxxx-xxxx / 03-xxxx-xxxx 等) に整形する VisualTransformation。
 * 基本的に日本の主要なパターン (10桁/11桁) に対応する。
 */
class PhoneNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        val transformedText = StringBuilder()

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
                    if (i == 2 || i == 6) transformedText.append("-")
                }
                isTwoDigitAreaCode -> {
                    if (i == 1 || i == 5) transformedText.append("-")
                }
                else -> {
                    if (i == 2 || i == 5) transformedText.append("-")
                }
            }
        }

        // 末尾がハイフンの場合は削除 (入力中)
        if (transformedText.endsWith("-")) {
            transformedText.deleteCharAt(transformedText.length - 1)
        }

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
