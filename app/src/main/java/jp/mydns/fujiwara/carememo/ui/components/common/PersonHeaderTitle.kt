package jp.mydns.fujiwara.carememo.ui.components.common

/**
 * Component：PersonHeaderTitle
 *
 * 【役割】：
 * TopAppBar 等に表示される、利用者の基本情報（氏名、ふりがな、年齢、識別メモ）を含むタイトル部分を描画する。
 *
 * 【主な機能】：
 * ・利用者の名前とふりがなに対するマスキング（伏せ字）の適用。
 * ・生年月日から計算された現在の年齢の表示。
 * ・同一人物を識別するための備考（note）の併記。
 * ・利用者情報がない場合（初期表示等）のデフォルトタイトルの表示。
 *
 * 【想定する利用場所】：
 * 詳細画面（健康、所見、服薬）の TopAppBar、一括入力画面など。
 *
 * 【このコンポーネントでは行わないこと】：
 * 情報の編集機能の提供。
 *
 * 【公開composable】：
 * PersonHeaderTitle
 */

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils

/**
 * TopAppBar等で使用する利用者情報のタイトルコンポーネント
 */
@Composable
fun PersonHeaderTitle(
    person: Person?,
    isNameMaskingEnabled: Boolean,
    defaultTitle: String,
    modifier: Modifier = Modifier,
) {
    person?.let { p ->
        val age = DateTimeUtils.calculateAge(p.birthday)
        Column(modifier = modifier.testTag("PersonHeader_Title")) {
            Text(
                text = p.getMaskedFurigana(isNameMaskingEnabled),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.testTag("PersonHeader_Furigana")
            )
            Text(
                text = buildString {
                    append(p.getMaskedName(isNameMaskingEnabled))
                    append(" さん")
                    append(" (${age}歳)")
                    if (p.note.isNotBlank()) {
                        append(" [${p.note}]")
                    }
                },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("PersonHeader_NameAndAge")
            )
        }
    } ?: Text(
        text = defaultTitle,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.testTag("PersonHeader_DefaultTitle")
    )
}
