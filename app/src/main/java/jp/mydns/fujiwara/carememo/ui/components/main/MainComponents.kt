package jp.mydns.fujiwara.carememo.ui.components.main

/**
 * Component：MainComponents
 *
 * 【役割】：
 * 利用者一覧画面（MainScreen）で使用される、利用者情報の表示、編集、および機能選択（ランチャー）
 * に関する共通UIパーツ群を提供する。
 *
 * 【主な機能】：
 * ・利用者リストアイテム（UserListItem）：一覧における1名分の表示（バッジ、氏名、年齢、誕生日、メニュー）。
 * ・利用者編集ダイアログ（UserEditDialog）：利用者の新規登録および情報編集用のフォーム。
 * ・カテゴリ選択シート（CategorySelectionSheet）：利用者選択時に表示される、各機能（健康記録・所見・服薬等）への遷移メニュー。
 *
 * 【想定する利用場所】：
 * ・MainScreen（利用者一覧画面）
 *
 * 【このコンポーネントでは行わないこと】：
 * ・ViewModelの直接保持（すべて引数またはラムダ経由で操作）
 * ・永続化処理（保存・削除の最終実行は呼び出し元に委譲）
 *
 * 【公開composable】：
 * ・UserListItem
 * ・UserEditDialog
 * ・CategorySelectionSheet
 *
 * ---
 * 最終更新日: 2026/07/04
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ModeEdit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.components.base.BirthdayInputFields
import jp.mydns.fujiwara.carememo.ui.components.base.rememberBirthdayInputState
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.viewmodel.PersonUiState

/**
 * 利用者一覧の各行（リストアイテム）を表示するコンポーネント。
 */
@Composable
fun UserListItem(
    userUiState: PersonUiState,
    onUserClick: (Person) -> Unit,
    onEditUser: (Person) -> Unit,
    onEndUser: (Person) -> Unit,
    modifier: Modifier = Modifier
) {
    var showItemMenu by remember { mutableStateOf(false) }
    val isBirthdayToday = remember(userUiState.person.birthday) {
        DateTimeUtils.isBirthdayToday(userUiState.person.birthday)
    }
    val isBirthdaySoon = remember(userUiState.person.birthday) {
        DateTimeUtils.isBirthdaySoon(userUiState.person.birthday)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // カテゴリバッジ
                    CategoryBadges(summary = userUiState.summary)

                    // 誕生日アイコン
                    Box(
                        modifier = Modifier.width(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBirthdaySoon || isBirthdayToday) {
                            Icon(
                                imageVector = Icons.Rounded.Cake,
                                contentDescription = "もうすぐ誕生日",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFE91E63)
                            )
                        }
                    }
                }
            },
            headlineContent = {
                Column {
                    // 氏名（ふりがな）
                    Text(
                        text = userUiState.maskedFurigana,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    // 氏名（漢字）
                    Text(
                        text = buildString {
                            append(userUiState.maskedName)
                            if (userUiState.person.note.isNotBlank()) append(" (${userUiState.person.note})")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 生年月日と年齢
                    Text(
                        text = stringResource(
                            R.string.birthday_summary_format,
                            userUiState.formattedBirthday,
                            stringResource(R.string.age_suffix, userUiState.age)
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
                            onClick = { showItemMenu = false; onEditUser(userUiState.person) }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.end_user_service),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { showItemMenu = false; onEndUser(userUiState.person) }
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = when {
                    isBirthdayToday -> {
                        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        if (isDark) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        else Color(0xFFFFC0CB) // Pink
                    }
                    isBirthdaySoon -> {
                        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        if (isDark) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                        else Color(0xFFFFF0F5) // LavenderBlush
                    }
                    else -> MaterialTheme.colorScheme.surface
                }
            ),
            modifier = Modifier.clickable { onUserClick(userUiState.person) }
        )
    }
}

/**
 * 利用者を選択した際に表示されるカテゴリ選択用のボトムシート。
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
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
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

        // カテゴリ選択メニュー
        Category.entries.forEach { category ->
            Button(
                onClick = { onCategorySelect(category) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(stringResource(category.displayNameRes), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * 利用者情報の登録および編集を行うためのダイアログ。
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        },
        confirmButton = {
            Button(
                onClick = {
                    birthdayState.toInstant()?.let { birthday ->
                        keyboardController?.hide()
                        val newPerson = person?.copy(
                            lastName = lastName,
                            firstName = firstName,
                            lastNameFurigana = lastNameFurigana,
                            firstNameFurigana = firstNameFurigana,
                            birthday = birthday,
                            note = note
                        ) ?: Person(
                            lastName = lastName,
                            firstName = firstName,
                            lastNameFurigana = lastNameFurigana,
                            firstNameFurigana = firstNameFurigana,
                            birthday = birthday,
                            note = note
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
