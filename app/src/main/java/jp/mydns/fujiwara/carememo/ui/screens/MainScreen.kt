package jp.mydns.fujiwara.carememo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.components.CategoryBadges
import jp.mydns.fujiwara.carememo.ui.components.CompactTextField
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import java.time.ZoneId
import java.time.chrono.JapaneseDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PersonListViewModel,
    onNavigateToDetail: (Int, Category) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val userList by viewModel.userList.collectAsState()
    val categorySummaries by viewModel.categorySummaries.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()
    val selectedSection by viewModel.selectedSection.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }
    
    var showEditDialog by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<Person?>(null) }

    // ダイアログ表示用の状態
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    // ViewModelからのイベントを監視
    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is PersonListViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is PersonListViewModel.UiEvent.ShowInfoDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is PersonListViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is PersonListViewModel.UiEvent.SaveSuccess -> {
                    showEditDialog = false
                }
            }
        }
    }

    MainScreenContent(
        userList = userList,
        categorySummaries = categorySummaries,
        isNameMaskingEnabled = isNameMaskingEnabled,
        searchQuery = searchQuery,
        selectedSection = selectedSection,
        onSearchQueryChange = { viewModel.setSearchQuery(it) },
        onSectionSelect = { viewModel.setSelectedSection(it) },
        snackbarHostState = snackbarHostState,
        lazyListState = lazyListState,
        onUserClick = { person -> selectedPerson = person; showSheet = true },
        onEditUser = { person -> editingPerson = person; showEditDialog = true },
        onAddClick = { editingPerson = null; showEditDialog = true },
        onEndUser = { person ->
            viewModel.logicalDeletePerson(person)
            scope.launch {
                val fullName = person.getMaskedName(isNameMaskingEnabled)
                val result = snackbarHostState.showSnackbar(
                    message = "$fullName さんの利用を終了しました", 
                    actionLabel = "元に戻す", 
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) { 
                    viewModel.restorePerson(person)
                    lazyListState.animateScrollToItem(0) 
                }
            }
        },
        onNavigateToSettings = onNavigateToSettings
    )

    // 通知ダイアログの表示
    if (dialogMessage != null) {
        AlertDialog(
            onDismissRequest = { dialogMessage = null; dialogTitle = null },
            title = { dialogTitle?.let { Text(it) } },
            text = { Text(dialogMessage!!) },
            confirmButton = {
                TextButton(onClick = { dialogMessage = null; dialogTitle = null }) {
                    Text("閉じる")
                }
            }
        )
    }

    if (showSheet && selectedPerson != null) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            CategorySelectionSheet(
                personName = selectedPerson!!.getMaskedName(isNameMaskingEnabled), 
                onCategorySelect = { category -> 
                    showSheet = false
                    onNavigateToDetail(selectedPerson!!.id, category) 
                }
            )
        }
    }

    if (showEditDialog) {
        UserEditDialog(
            person = editingPerson, 
            onDismiss = { showEditDialog = false }, 
            onSave = { person -> 
                if (editingPerson == null) viewModel.addPerson(person) else viewModel.updatePerson(person)
                // ここで showEditDialog = false にしない（ViewModelからの成功通知を待つ）
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    userList: List<Person>,
    categorySummaries: Map<Int, PersonCategorySummary>,
    isNameMaskingEnabled: Boolean,
    searchQuery: String,
    selectedSection: String,
    onSearchQueryChange: (String) -> Unit,
    onSectionSelect: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    onUserClick: (Person) -> Unit,
    onEditUser: (Person) -> Unit,
    onAddClick: () -> Unit,
    onEndUser: (Person) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    if (showVersionDialog) {
        AlertDialog(
            onDismissRequest = { showVersionDialog = false },
            title = { Text("バージョン情報") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CareMemo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("バージョン: 1.3.0")
                    HorizontalDivider()
                    Text("ターゲット環境:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Android 15 (API 35)")
                    Text("KYOCERA TORQUE G06 最適化済")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("(C) 2025-2026 pirotty.galaxy", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showVersionDialog = false }) { Text("閉じる") } }
        )
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("バージョン履歴") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text("Ver. 1.0.2 (2025/06/21)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("・指紋・顔認証による起動ロック機能を搭載（設定から有効化可能）\n" +
                             "・同姓同名・同生年月日の利用者の重複登録チェックを強化\n" +
                             "・日時入力欄を2段化し、文字の見切れを解消\n" +
                             "・PDF出力時、データがない指標のグラフを出力から除外するよう改善\n" +
                             "・キーボードの「次へ」ボタンで各入力欄をスムーズに移動可能に改善")
                    }
                    
                    HorizontalDivider()

                    Column {
                        Text("Ver. 1.0.1 (2025/02/11)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("【詳細データ画面】", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
                        Text("・利用者名の表示形式を改善（ふりがな・メモの表示追加）\n" +
                             "・数値入力欄をコンパクト化し、画面の有効活用を促進\n" +
                             "・入力時、キーボードの「次へ」で項目間移動を可能に改善\n" +
                             "・記録件数の表示位置を履歴リスト最上部に移動\n" +
                             "・グラフを「全件表示」に変更し、推移を確認しやすく改善\n" +
                             "・グラフの余白を詰め、描画エリアを拡大\n" +
                             "・体重・BMI・血糖値グラフのY軸レンジをデータに合わせて自動調整")
                        
                        Text("【所見メモ】", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
                        Text("・音声入力時に句点の自動補完と改行の自動挿入を実装\n" +
                             "・追記がよりスムーズに行えるよう改善")
                        
                        Text("【設定画面】", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
                        Text("・「記録者の名前」入力時のカーソル飛びバグを修正")
                        
                        Text("【PDF出力】", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
                        Text("・グラフタイトルの直下に「目安」と「ヒント」を表示するようレイアウトを刷新")
                    }
                    
                    HorizontalDivider()
                    
                    Column {
                        Text("Ver. 1.0.0 (2025/02/10)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("・初回リリース")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistoryDialog = false }) { Text("閉じる") } }
        )
    }

    if (showHelpDialog) {
        AlertDialog(onDismissRequest = { showHelpDialog = false }, title = { Text("ヘルプ") }, text = { Text("・利用者一覧から利用者を選択して記録を行います。\n・利用を終了した方は「設定」内の「利用者管理」から復帰できます。") }, confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text("閉じる") } })
    }

    val kanaGroups = listOf("全", "あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ", "他")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CareMemo", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "メニュー") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("設定") }, onClick = { showMenu = false; onNavigateToSettings() })
                        DropdownMenuItem(text = { Text("ヘルプ") }, onClick = { showMenu = false; showHelpDialog = true })
                        DropdownMenuItem(text = { Text("バージョン情報") }, onClick = { showMenu = false; showVersionDialog = true })
                        DropdownMenuItem(text = { Text("バージョン履歴") }, onClick = { showMenu = false; showHistoryDialog = true })
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { FloatingActionButton(onClick = onAddClick) { Icon(Icons.Default.Add, contentDescription = "利用者登録") } }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchBar(query = searchQuery, onQueryChange = onSearchQueryChange)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Start
            ) {
                kanaGroups.forEach { title ->
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(48.dp)
                            .clickable { onSectionSelect(title) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selectedSection == title) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selectedSection == title) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .width(24.dp)
                                        .height(2.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize(), state = lazyListState, contentPadding = PaddingValues(bottom = 80.dp)) {
                items(userList, key = { it.id }) { user ->
                    val age = calculateAge(user.birthday)
                    val birthdayStr = formatToJapaneseEra(user.birthday)
                    var showItemMenu by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.animateItem()) {
                        ListItem(
                            leadingContent = {
                                CategoryBadges(summary = categorySummaries[user.id] ?: PersonCategorySummary())
                            },
                            headlineContent = { 
                                Column { 
                                    Text(
                                        text = user.getMaskedFurigana(isNameMaskingEnabled), 
                                        style = MaterialTheme.typography.labelSmall, 
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = buildString { 
                                            append(user.getMaskedName(isNameMaskingEnabled))
                                            if (user.note.isNotBlank()) append(" (${user.note})") 
                                        }, 
                                        style = MaterialTheme.typography.titleMedium, 
                                        fontWeight = FontWeight.Bold, 
                                        maxLines = 1, 
                                        overflow = TextOverflow.Ellipsis
                                    ) 
                                } 
                            },
                            supportingContent = { Text(text = "${birthdayStr}生　${age}歳", style = MaterialTheme.typography.bodySmall) },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { showItemMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "操作メニュー") }
                                    DropdownMenu(expanded = showItemMenu, onDismissRequest = { showItemMenu = false }) {
                                        DropdownMenuItem(text = { Text("利用者情報を編集") }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }, onClick = { showItemMenu = false; onEditUser(user) })
                                        DropdownMenuItem(text = { Text("利用を終了する", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }, onClick = { showItemMenu = false; onEndUser(user) })
                                    }
                                }
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface).clickable { onUserClick(user) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun CategorySelectionSheet(personName: String, onCategorySelect: (Category) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "$personName さんの記録を選択", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp).align(Alignment.Start))
        Category.entries.forEach { category ->
            Button(onClick = { onCategorySelect(category) }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                Text(category.displayName, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

enum class BirthEra(val displayName: String) { AD("西暦"), SHOWA("昭和"), HEISEI("平成"), REIWA("令和") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserEditDialog(person: Person?, onDismiss: () -> Unit, onSave: (Person) -> Unit) {
    var lastName by remember { mutableStateOf(person?.lastName ?: "") }
    var firstName by remember { mutableStateOf(person?.firstName ?: "") }
    var lastNameFurigana by remember { mutableStateOf(person?.lastNameFurigana ?: "") }
    var firstNameFurigana by remember { mutableStateOf(person?.firstNameFurigana ?: "") }
    var note by remember { mutableStateOf(person?.note ?: "") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val yearFocusRequester = remember { FocusRequester() }
    val monthFocusRequester = remember { FocusRequester() }
    val dayFocusRequester = remember { FocusRequester() }
    val initialDate = person?.birthday?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: LocalDate.now()
    var selectedEra by remember { mutableStateOf(if (person == null) BirthEra.SHOWA else when { initialDate.year in 1926..1989 -> BirthEra.SHOWA; initialDate.year in 1990..2019 -> BirthEra.HEISEI; initialDate.year >= 2020 -> BirthEra.REIWA; else -> BirthEra.AD }) }
    var yearText by remember { mutableStateOf(if (person == null) "" else { val y = initialDate.year; when (selectedEra) { BirthEra.SHOWA -> (y - 1925).toString(); BirthEra.HEISEI -> (y - 1988).toString(); BirthEra.REIWA -> (y - 2018).toString(); BirthEra.AD -> y.toString() } }) }
    var monthText by remember { mutableStateOf(if (person == null) "" else initialDate.monthValue.toString()) }
    var dayText by remember { mutableStateOf(if (person == null) "" else initialDate.dayOfMonth.toString()) }
    var eraExpanded by remember { mutableStateOf(false) }
    val yInput = yearText.toIntOrNull()
    val m = monthText.toIntOrNull()
    val d = dayText.toIntOrNull()
    val westernYear = yInput?.let { when (selectedEra) { BirthEra.SHOWA -> it + 1925; BirthEra.HEISEI -> it + 1988; BirthEra.REIWA -> it + 2018; BirthEra.AD -> it } }
    val isYearError = yInput == null || when (selectedEra) { BirthEra.SHOWA -> yInput !in 1..64; BirthEra.HEISEI -> yInput !in 1..31; BirthEra.REIWA -> yInput !in 1..99; BirthEra.AD -> yInput !in 1900..2100 }
    val isMonthError = m == null || m !in 1..12
    val isDayError = try { if (westernYear != null && m != null && d != null) d < 1 || d > YearMonth.of(westernYear, m).lengthOfMonth() else true } catch (_: Exception) { true }
    val isInputValid = lastName.isNotBlank() && firstName.isNotBlank() && !isYearError && !isMonthError && !isDayError
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (person == null) "利用者登録" else "登録情報の編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("姓") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("名") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lastNameFurigana,
                        onValueChange = { lastNameFurigana = it },
                        label = { Text("せい") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = firstNameFurigana,
                        onValueChange = { firstNameFurigana = it },
                        label = { Text("めい") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("同姓同名識別用メモ") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("生年月日", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExposedDropdownMenuBox(expanded = eraExpanded, onExpandedChange = { eraExpanded = !eraExpanded }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(value = selectedEra.displayName, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = eraExpanded) }, colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(), modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium)
                        ExposedDropdownMenu(expanded = eraExpanded, onDismissRequest = { eraExpanded = false }) { BirthEra.entries.forEach { era -> DropdownMenuItem(text = { Text(era.displayName, style = MaterialTheme.typography.bodyMedium) }, onClick = { selectedEra = era; eraExpanded = false }) } }
                    }
                    CompactTextField(value = yearText, onValueChange = { val filtered = it.filter { char -> char.isDigit() }; val maxLength = if (selectedEra == BirthEra.AD) 4 else 2; if (filtered.length <= maxLength) { yearText = filtered; if (filtered.length == maxLength) monthFocusRequester.requestFocus() } }, modifier = Modifier.weight(1f).focusRequester(yearFocusRequester), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), isError = isYearError, suffix = { Text("年", style = MaterialTheme.typography.labelSmall) })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CompactTextField(value = monthText, onValueChange = { val filtered = it.filter { char -> char.isDigit() }; if (filtered.length <= 2) { monthText = filtered; if (filtered.length == 2) dayFocusRequester.requestFocus() } }, modifier = Modifier.weight(1f).focusRequester(monthFocusRequester), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), isError = isMonthError, suffix = { Text("月", style = MaterialTheme.typography.labelSmall) })
                    CompactTextField(value = dayText, onValueChange = { val filtered = it.filter { char -> char.isDigit() }; if (filtered.length <= 2) dayText = filtered }, modifier = Modifier.weight(1f).focusRequester(dayFocusRequester), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), isError = isDayError, suffix = { Text("日", style = MaterialTheme.typography.labelSmall) })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isInputValid && westernYear != null && m != null && d != null) {
                        keyboardController?.hide()
                        val birthday = LocalDate.of(westernYear, m, d)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
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
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}


fun calculateAge(birthday: Instant): Int {
    val birthDate = birthday.atZone(ZoneId.systemDefault()).toLocalDate()
    val currentDate = LocalDate.now()
    return Period.between(birthDate, currentDate).years
}

fun formatToJapaneseEra(instant: Instant): String {
    val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val japaneseDate = JapaneseDate.from(localDate)
    val formatter = DateTimeFormatter.ofPattern("Gy年M月d日").withLocale(Locale.JAPAN)
    return japaneseDate.format(formatter)
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = { onQueryChange(it) },
        label = { Text("所見メモ検索") },
        placeholder = { Text("特定の症状や出来事を入力...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "クリア")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val mockUserList = listOf(Person(id = 1, lastName = "山田", firstName = "太郎", lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ", birthday = LocalDate.of(1950, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant()))
    CareMemoTheme { 
        MainScreenContent(
            userList = mockUserList, 
            categorySummaries = emptyMap(), 
            isNameMaskingEnabled = false, 
            searchQuery = "",
            selectedSection = "全",
            onSearchQueryChange = {},
            onSectionSelect = {},
            snackbarHostState = remember { SnackbarHostState() }, 
            lazyListState = rememberLazyListState(), 
            onUserClick = { }, 
            onEditUser = { }, 
            onAddClick = { }, 
            onEndUser = { }, 
            onNavigateToSettings = { }
        ) 
    }
}
