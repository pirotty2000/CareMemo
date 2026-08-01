package jp.mydns.fujiwara.carememo.ui.components.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import jp.mydns.fujiwara.carememo.data.Person

/**
 * 利用者カードのバッジ部分をタップした際に表示されるクイックアクションメニュー
 */
@Composable
fun QuickActionMenu(
    expanded: Boolean,
    person: Person,
    isNameMaskingEnabled: Boolean,
    onDismissRequest: () -> Unit,
    onEmergencyContactClick: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("QuickActionMenu_${person.id}")
    ) {
        // メニューヘッダー (名前)
        DropdownMenuItem(
            text = {
                Text(
                    text = "${person.getMaskedName(isNameMaskingEnabled)} さん",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            onClick = { },
            enabled = false
        )
        HorizontalDivider()

        // 緊急連絡先 (医師・看護師・家族) に連絡
        DropdownMenuItem(
            text = { Text("医師・看護師・家族に連絡") },
            leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
            onClick = {
                onDismissRequest()
                onEmergencyContactClick()
            },
            modifier = Modifier.testTag("QuickActionMenu_EmergencyContact")
        )

        // 将来的な拡張領域
    }
}
