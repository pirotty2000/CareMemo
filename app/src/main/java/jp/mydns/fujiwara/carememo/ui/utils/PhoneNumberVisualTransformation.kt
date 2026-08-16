package jp.mydns.fujiwara.carememo.ui.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import jp.mydns.fujiwara.carememo.logic.common.PhoneLogic

/**
 * Logic：PhoneNumberVisualTransformation
 *
 * 【役割】
 * 入力された電話番号の数字列に対して、日本の主要な電話番号パターンに基づいたハイフンを動的に挿入し、
 * ユーザーが読みやすい形式で表示するための VisualTransformation を提供します。
 */
class PhoneNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        val transformedText = StringBuilder()
        
        val hyphenPositions = PhoneLogic.getHyphenPositions(originalText)
        val firstHyphen = hyphenPositions.getOrNull(0) ?: -1
        val secondHyphen = hyphenPositions.getOrNull(1) ?: -1

        for (i in originalText.indices) {
            transformedText.append(originalText[i])
            if (i == firstHyphen || i == secondHyphen) {
                // 3-3-4形式などの場合、7文字目以降にのみ2つ目のハイフンを入れるなどの追加制約
                if (i == secondHyphen && originalText.length <= secondHyphen + 2) {
                     // 短すぎる場合は入れない（既存ロジック踏襲）
                } else {
                    transformedText.append("-")
                }
            }
        }

        if (transformedText.endsWith("-")) {
            transformedText.deleteCharAt(transformedText.length - 1)
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var hyphenCount = 0
                if (offset > firstHyphen + 1) hyphenCount++
                if (offset > secondHyphen + 1) {
                    if (originalText.length > secondHyphen + 2) hyphenCount++
                }
                return offset + hyphenCount
            }

            override fun transformedToOriginal(offset: Int): Int {
                var hyphenCount = 0
                if (offset > firstHyphen + 2) hyphenCount++
                if (offset > secondHyphen + 3) {
                    if (originalText.length > secondHyphen + 2) hyphenCount++
                }
                val originalOffset = offset - hyphenCount
                return originalOffset.coerceIn(0, originalText.length)
            }
        }

        return TransformedText(AnnotatedString(transformedText.toString()), offsetMapping)
    }
}
