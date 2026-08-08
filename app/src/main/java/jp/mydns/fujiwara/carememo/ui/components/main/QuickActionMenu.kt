package jp.mydns.fujiwara.carememo.ui.components.main

/**
 * Component：QuickActionMenu
 *
 * 【役割】
 * 利用者カードのバッジ部分をタップした際に表示される、頻度の高い操作に素早くアクセスするためのコンテキストメニューを提供します。
 *
 * 【主な機能】
 * ・対象の利用者名の表示（マスク対応）。
 * ・緊急連絡先画面へのクイックアクセス。
 * ・将来的な拡張を考慮したメニュー構造。
 *
 * 【想定する利用場所】
 * ・MainScreenContent の UserListItem 内（バッジタップ時）。
 *
 * 【このコンポーネントでは行わないこと】
 * ・利用者情報の編集などの管理操作（UserListItem の右側ドロップダウンメニューが担当）。
 */

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person

/**
 * バッジタップ時に表示されるクイックアクションメニュー
 *
 * @param expanded メニューを表示するかどうか
 * @param person 対象の利用者情報
 * @param isNameMaskingEnabled 氏名を伏せ字にするかどうか
 * @param onDismissRequest メニューを閉じる際のコールバック
 * @param onEmergencyContactClick 緊急連絡先へのアクセスが選択された際のコールバック
 */
@Composable
fun QuickActionMenu(
    expanded: Boolean,
    person: Person,
    isNameMaskingEnabled: Boolean,
    onDismissRequest: () -> Unit,
    onEmergencyContactClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag("QuickActionMenu_${person.id}")
    ) {
        // メニューヘッダー (名前表示用：クリック不可)
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.common_honorific_san_suffix, person.getMaskedName(isNameMaskingEnabled)),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            onClick = { },
            enabled = false
        )
        HorizontalDivider()

        // アクション：緊急連絡先 (医師・看護師・家族) に連絡
        DropdownMenuItem(
            text = { Text(stringResource(R.string.main_quick_action_emergency_contact)) },
            leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
            onClick = {
                onDismissRequest()
                onEmergencyContactClick()
            },
            modifier = Modifier.testTag("QuickActionMenu_EmergencyContact")
        )

        // 将来的な拡張領域（例：ここから一括入力を開始するなど）
    }
}
