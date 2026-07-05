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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.components.base.BirthdayInputFields
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.base.rememberBirthdayInputState
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
                            tint = Color(0xFFE91E63)
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
                    color = MaterialTheme.colorScheme.secondary
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
                        R.string.birthday_summary_format, 
                        DateTimeUtils.formatDateJapaneseEra(person.birthday), 
                        stringResource(R.string.age_suffix, DateTimeUtils.calculateAge(person.birthday))
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } 
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showItemMenu = true }) { 
                    Icon(Icons.Rounded.ModeEdit, contentDescription = "操作メニュー") 
                }
                DropdownMenu(expanded = showItemMenu, onDismissRequest = { showItemMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_user_info)) }, 
                        leadingIcon = { Icon(Icons.Rounded.ModeEdit, contentDescription = null) }, 
                        onClick = { showItemMenu = false; onEditClick() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.end_user_service), color = MaterialTheme.colorScheme.error) }, 
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }, 
                        onClick = { showItemMenu = false; onDeleteClick() }
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = when {
                isBirthdayToday -> Color(0xFFFFC0CB) // Pink
                isBirthdaySoon -> Color(0xFFFFF0F5)  // LavenderBlush
                else -> MaterialTheme.colorScheme.surface
            }
        )
    )
}

/**
 * 利用者情報を登録・編集するためのダイアログ。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserEditDialog(
    person: Person?,
    onDismiss: () -> Unit,
    onSave: (Person) -> Unit
) {
    var lastName by remember { mutableStateOf(person?.lastName ?: "") }
    var firstName by remember { mutableStateOf(person?.firstName ?: "") }
    var lastNameFurigana by remember { mutableStateOf(person?.lastNameFurigana ?: "") }
    var firstNameFurigana by remember { mutableStateOf(person?.firstNameFurigana ?: "") }
    var note by remember { mutableStateOf(person?.note ?: "") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val birthdayState = rememberBirthdayInputState(initialInstant = person?.birthday)
    val isInputValid = lastName.isNotBlank() && firstName.isNotBlank() && birthdayState.isValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (person == null) stringResource(R.string.user_registration)
                else stringResource(R.string.user_edit)
            )
        },
        text = {
            val scrollState = rememberScrollState()
            Box {
                Column(
                    modifier = Modifier.verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            label = { Text(stringResource(R.string.last_name)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            label = { Text(stringResource(R.string.first_name)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lastNameFurigana,
                            onValueChange = { lastNameFurigana = it },
                            label = { Text(stringResource(R.string.last_name_furigana)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = firstNameFurigana,
                            onValueChange = { firstNameFurigana = it },
                            label = { Text(stringResource(R.string.first_name_furigana)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.note_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    BirthdayInputFields(state = birthdayState)
                }
                VerticalScrollIndicator(scrollState = scrollState, isCompact = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    birthdayState.toInstant()?.let { birthday ->
                        keyboardController?.hide()
                        val newPerson = person?.copy(
                            lastName = lastName.trim(),
                            firstName = firstName.trim(),
                            lastNameFurigana = lastNameFurigana.trim(),
                            firstNameFurigana = firstNameFurigana.trim(),
                            birthday = birthday,
                            note = note.trim()
                        ) ?: Person(
                            lastName = lastName.trim(),
                            firstName = firstName.trim(),
                            lastNameFurigana = lastNameFurigana.trim(),
                            firstNameFurigana = firstNameFurigana.trim(),
                            birthday = birthday,
                            note = note.trim()
                        )
                        onSave(newPerson)
                    }
                },
                enabled = isInputValid
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
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
            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.category_selection_title, personName),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .align(Alignment.Start)
        )

        // 一括入力ボタン
        Button(
            onClick = onBatchInputSelect,
            modifier = Modifier.fillMaxWidth().height(56.dp),
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
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
