package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.layout.Column
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
) {
    person?.let { p ->
        val age = DateTimeUtils.calculateAge(p.birthday)
        Column {
            Text(
                text = p.getMaskedFurigana(isNameMaskingEnabled),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
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
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    } ?: Text(
        text = defaultTitle,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer
    )
}
