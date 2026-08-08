package jp.mydns.fujiwara.carememo.ui.components.main

/**
 * Component：MainComponents
 *
 * 【役割】
 * 利用者一覧画面（MainScreen）において、利用者情報の概要表示と、
 * 各機能（健康記録、所見メモ等）への遷移メニュー（ランチャー）を提供します。
 *
 * 【主な機能】
 * ・利用者カード（UserListItem）：氏名、年齢、誕生日通知、および各カテゴリの入力済みバッジの表示。
 * ・クイックメニュー（CategorySelectionSheet）：特定の利用者に対する機能選択（一括入力含む）ボトムシート。
 * ・操作メニュー（DropdownMenu）：情報の編集、緊急連絡先管理、利用終了の操作分岐。
 *
 * 【想定する利用場所】
 * ・MainScreenContent（利用者一覧画面のメインリスト部分）
 *
 * 【このコンポーネントでは行わないこと】
 * ・利用者情報自体の保存・削除処理（ViewModel 経由でラムダとして操作を受け取る）。
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils

/**
 * 全体像：利用者一覧（Main）
 *
 * ■ ui/screens/main/MainScreenContent.kt の MainScreenContent (画面全体の器)
 * │
 * ├─ [Scaffold]
 * │    ├─ TopAppBar (アプリタイトル、設定・バージョンメニュー)
 * │    ├─ FloatingActionButton (利用者の新規追加ボタン ➔ [5] 画面へ遷移)
 * │    └─ SnackbarHost (メッセージ通知領域)
 * │
 * ├─【コンテンツエリア：Column】
 * │    ├─ [1] SearchBox (氏名・所見メモのリアルタイム検索：ui/components/base/SearchBox.kt)
 * │    ├─ [2] KanaIndexBar (五十音インデックスバー：ui/components/main/KanaIndexBar.kt)
 * │    ├─ <区切り線> HorizontalDivider
 * │    └─ [3] LazyColumn (メインリスト)
 * │         └─ [3-1] UserListItem (利用者カード：ui/components/main/MainComponents.kt)
 * │              ├─ [3-1-1] CategoryBadges (入力済み情報のバッジ：ui/components/main/CategoryBadges.kt)
 * │              ├─ CakeIcon (本日/近日誕生日の通知アイコン)
 * │              ├─ <表示情報> フリガナ、氏名(マスク対応)、識別メモ、生年月日、年齢
 * │              └─ <操作メニュー> DropdownMenu (情報編集 ➔ [5] 画面へ、利用終了)
 * │
 * └─【遷移・シート・ダイアログ群】
 *      ├─ [4] CategorySelectionSheet (機能選択シート：ui/components/main/MainComponents.kt)
 *      ├─ [5] PersonEditScreen (利用者の登録・編集画面：ui/screens/main/PersonEditScreen.kt)
 *      └─ [6] VersionDialog (アプリ情報・バージョン表示：MainScreenContent内に定義)
 */

/**
 * [3-1] UserListItem (利用者カード)
 * 利用者一覧における、1名分の情報を表示するカード型コンポーネント。
 *
 * @param person 利用者情報
 * @param summary カテゴリごとのデータ存在有無サマリー
 * @param isNameMaskingEnabled 氏名・ふりがなを伏せ字にするかどうか
 * @param onClick カード全体（名前エリア）がタップされた際のコールバック（機能選択シートの表示を想定）
 * @param onQuickMenuClick バッジ部分がタップされた際のコールバック
 * @param onEmergencyContactManageClick 緊急連絡先管理が選択された際のコールバック
 * @param onEditClick 利用者情報の編集が選択された際のコールバック
 * @param onDeleteClick 利用終了（論理削除）が選択された際のコールバック
 * @param modifier 修飾子
 */
@Composable
fun UserListItem(
    person: Person,
    modifier: Modifier = Modifier,
    summary: PersonCategorySummary? = null,
    isNameMaskingEnabled: Boolean = true,
    onClick: () -> Unit,
    onQuickMenuClick: () -> Unit,
    onEmergencyContactManageClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    // 誕生日の通知判定（本日または近日）
    val isBirthdayToday = remember(person.birthday) {
        DateTimeUtils.isBirthdayToday(person.birthday)
    }
    val isBirthdaySoon = remember(person.birthday) {
        DateTimeUtils.isBirthdaySoon(person.birthday)
    }

    var showItemMenu by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier,
        // 左側：バッジ（記録状況）と誕生日通知アイコン
        leadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 入力済み情報のバッジ部分 (タップでクイックメニューへ)
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onQuickMenuClick)
                        .padding(4.dp)
                ) {
                    CategoryBadges(summary = summary ?: PersonCategorySummary())
                }

                // 本日/近日誕生日の場合に表示されるアイコン
                Box(
                    modifier = Modifier.width(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBirthdaySoon || isBirthdayToday) {
                        Icon(
                            imageVector = Icons.Rounded.Cake,
                            contentDescription = "誕生日通知",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        },
        // 中央：氏名（マスク対応）、識別メモ、生年月日、年齢
        headlineContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 4.dp)
            ) {
                // フリガナ (上段)
                Text(
                    text = person.getMaskedFurigana(isNameMaskingEnabled), 
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isBirthdayToday) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.secondary
                )
                // 氏名 + 同姓同名識別メモ (中段)
                Text(
                    text = buildString { 
                        append(person.getMaskedName(isNameMaskingEnabled))
                        if (person.note.isNotBlank()) append(" (${person.note})") 
                    }, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                ) 
                // 生年月日 + 年齢 (下段)
                Text(
                    text = stringResource(
                        R.string.common_birthday_format, 
                        DateTimeUtils.formatDateJapaneseEra(person.birthday), 
                        stringResource(R.string.common_age_suffix, DateTimeUtils.calculateAge(person.birthday))
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBirthdayToday) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } 
        },
        // 右側：操作メニュー（鉛筆アイコン）
        trailingContent = {
            Box {
                IconButton(
                    onClick = { showItemMenu = true },
                    modifier = Modifier.testTag("UserListItem_MenuButton")
                ) { 
                    Icon(Icons.Rounded.ModeEdit, contentDescription = "操作メニュー") 
                }
                DropdownMenu(expanded = showItemMenu, onDismissRequest = { showItemMenu = false }) {
                    // 利用者情報を編集
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.main_edit_user_info)) }, 
                        leadingIcon = { Icon(Icons.Rounded.ModeEdit, contentDescription = null) }, 
                        onClick = { showItemMenu = false; onEditClick() },
                        modifier = Modifier.testTag("UserListItem_MenuItem_Edit")
                    )
                    // 緊急連絡先の管理
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.medical_contacts_manage_label)) },
                        leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                        onClick = { showItemMenu = false; onEmergencyContactManageClick() },
                        modifier = Modifier.testTag("UserListItem_MenuItem_EmergencyContact")
                    )
                    // 利用終了（論理削除）
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.main_end_user_service), color = MaterialTheme.colorScheme.error) }, 
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }, 
                        onClick = { showItemMenu = false; onDeleteClick() },
                        modifier = Modifier.testTag("UserListItem_MenuItem_Delete")
                    )
                }
            }
        },
        // 誕生日かどうかに応じた背景色の切り替え
        colors = ListItemDefaults.colors(
            containerColor = when {
                isBirthdayToday -> MaterialTheme.colorScheme.tertiaryContainer
                isBirthdaySoon -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                else -> MaterialTheme.colorScheme.surface
            },
            headlineColor = when {
                isBirthdayToday -> MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    )
}

/**
 * [4] CategorySelectionSheet (機能選択：健康/服薬/所見/一括入力)
 * 利用者を選択した際に表示される、各機能への遷移メニュー（ボトムシート）。
 *
 * @param personName 選択された利用者の氏名（表示用）
 * @param onCategorySelect 個別のカテゴリ（健康、服薬等）が選択された際のコールバック
 * @param onBatchInputSelect 健康記録の一括入力が選択された際のコールバック
 */
@Composable
fun CategorySelectionSheet(
    personName: String,
    onCategorySelect: (Category) -> Unit,
    onBatchInputSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
            .testTag("CategorySelectionSheet"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // タイトル：だれの情報を入力するかを明示
        Text(
            text = stringResource(R.string.main_category_selection_title, personName),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .align(Alignment.Start)
                .testTag("CategorySelectionSheet_Title")
        )

        // 最優先のアクション：健康記録の一括入力
        Button(
            onClick = onBatchInputSelect,
            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("CategorySelectionSheet_BatchInput"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Rounded.EditNote, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("健康記録の一括入力", style = MaterialTheme.typography.titleMedium)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 個別のカテゴリ詳細への遷移ボタン群
        Category.entries.forEach { category ->
            Button(
                onClick = { onCategorySelect(category) },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("CategorySelectionSheet_Button_${category.name}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(
                    text = stringResource(category.displayNameRes),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
