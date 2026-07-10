package jp.mydns.fujiwara.carememo.ui.components.main

/**
 * Component：MainComponents
 *
 * 【役割】：
 * 利用者一覧画面（MainScreen）で使用される、利用者情報の表示、編集、および機能選択（ランチャー）
 * に関する共通UIパーツ群を提供する。
 *
 * 【主な機能】：
 * ・利用者リストアイテム（UserListItem）：一覧における1名分の表示。
 * ・利用者編集ダイアログ（UserEditDialog）：利用者の新規登録および情報編集用のフォーム。
 * ・カテゴリ選択シート（CategorySelectionSheet）：機能遷移メニュー。
 */

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * 利用者カードを表示するコンポーネント（一覧用）。
 * v2.1.1のデザインを完全に復元。
 */
@Composable
fun UserListItem(
    person: Person,
    summary: PersonCategorySummary? = null,
    isNameMaskingEnabled: Boolean = true,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val isBirthdayToday = remember(person.birthday) {
        DateTimeUtils.isBirthdayToday(person.birthday)
    }
    val isBirthdaySoon = remember(person.birthday) {
        DateTimeUtils.isBirthdaySoon(person.birthday)
    }

    var showItemMenu by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CategoryBadges(summary = summary ?: PersonCategorySummary())
                
                // ケーキアイコン領域
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
        headlineContent = { 
            Column { 
                // フリガナ (上段)
                Text(
                    text = person.getMaskedFurigana(isNameMaskingEnabled), 
                    style = MaterialTheme.typography.labelSmall, 
                    color = if (isBirthdayToday) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.secondary
                )
                // 氏名 + 備考 (中段)
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
                    color = if (isBirthdayToday) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } 
        },
        trailingContent = {
            Box {
                IconButton(
                    onClick = { showItemMenu = true },
                    modifier = Modifier.testTag("UserListItem_MenuButton")
                ) { 
                    Icon(Icons.Rounded.ModeEdit, contentDescription = "操作メニュー") 
                }
                DropdownMenu(expanded = showItemMenu, onDismissRequest = { showItemMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.main_edit_user_info)) }, 
                        leadingIcon = { Icon(Icons.Rounded.ModeEdit, contentDescription = null) }, 
                        onClick = { showItemMenu = false; onEditClick() },
                        modifier = Modifier.testTag("UserListItem_MenuItem_Edit")
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.main_end_user_service), color = MaterialTheme.colorScheme.error) }, 
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }, 
                        onClick = { showItemMenu = false; onDeleteClick() },
                        modifier = Modifier.testTag("UserListItem_MenuItem_Delete")
                    )
                }
            }
        },
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
 * 利用者を選択した際に表示されるカテゴリ選択用のボトムシート。
 * v2.1.1のデザインを復元。
 */
@Composable
fun CategorySelectionSheet(
    personName: String,
    onCategorySelect: (Category) -> Unit,
    onBatchInputSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
            .testTag("CategorySelectionSheet"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.main_category_selection_title, personName),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .align(Alignment.Start)
                .testTag("CategorySelectionSheet_Title")
        )

        // 一括入力ボタン
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

        // 各カテゴリボタン
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
