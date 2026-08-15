package jp.mydns.fujiwara.carememo.ui.components.common

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
 * Component：PersonHeaderTitle
 *
 * 【役割】
 * TopAppBar 等に表示される、利用者の基本情報（氏名、ふりがな、年齢、識別メモ）を含むタイトル部分を描画します。
 *
 * 【主な機能】
 * ・利用者の名前とふりがなに対するマスキング（伏せ字）の適用。
 * ・生年月日から計算された現在の年齢の表示。
 * ・同一人物を識別するための備考（note）の併記。
 * ・利用者情報がない場合（初期表示等）のデフォルトタイトルの表示。
 *
 * 【想定する利用場所】
 * 各カテゴリの詳細画面（健康、所見、服薬）の TopAppBar、一括入力画面のツールバーなど。
 *
 * 【このコンポーネントでは行わないこと】
 * 利用者情報の編集機能の提供（表示専用）。
 */

/**
 * 全体像：利用者情報ヘッダー（Person Header）
 *
 * ■ TopAppBar 等の title スロット
 * │
 * └─ [1] PersonHeaderTitle (★本コンポーネント)
 *      └─ Column (縦並びレイアウト)
 *           ├─ [1-1] マスク済みふりがな (サブタイトル)
 *           └─ [1-2] マスク済み氏名 + 敬称 + 年齢 + 備考
 */

/**
 * TopAppBar 等に表示される、利用者の基本情報を含むタイトル部分を表示します。
 *
 * @param person 表示対象の利用者情報（null の場合はデフォルトタイトルを表示）
 * @param isNameMaskingEnabled 名前とふりがなを伏せ字にするかどうか
 * @param defaultTitle 利用者が選択されていない場合に表示するデフォルトの文字列
 * @param modifier 修飾子
 */
@Composable
fun PersonHeaderTitle(
    person: Person?,
    isNameMaskingEnabled: Boolean,
    defaultTitle: String,
    modifier: Modifier = Modifier,
) {
    person?.let { p ->
        // 生年月日から現在の年齢を算出
        val age = DateTimeUtils.calculateAge(p.birthday)
        Column(modifier = modifier.testTag("PersonHeader_Title")) {
            // ふりがな（サブタイトルとして小さめに表示）
            Text(
                text = p.getMaskedFurigana(isNameMaskingEnabled),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.testTag("PersonHeader_Furigana")
            )
            // 名前・敬称・年齢・備考
            Text(
                text = buildString {
                    append(p.getMaskedName(isNameMaskingEnabled))
                    append(" さん")
                    append(" (${age}歳)")
                    // 同姓同名の識別用メモがある場合は併記する
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
