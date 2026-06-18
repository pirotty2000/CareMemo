package jp.mydns.fujiwara.carememo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.rememberLazyListState
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel
import jp.mydns.fujiwara.carememo.ui.screens.CompactTextField
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
    onNavigateToRestore: () -> Unit
) {
    val userList by viewModel.userList.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }
    
    // 登録・編集ダイアログの状態
    var showEditDialog by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<Person?>(null) }

    val errorMsg by viewModel.errorFlow.collectAsState()

    if (errorMsg != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("登録エラー") },
            text = { Text(errorMsg!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }

    MainScreenContent(
        userList = userList,
        snackbarHostState = snackbarHostState,
        lazyListState = lazyListState,
        onUserClick = { person ->
            selectedPerson = person
            showSheet = true
        },
        onEditUser = { person ->
            editingPerson = person
            showEditDialog = true
        },
        onAddClick = {
            editingPerson = null
            showEditDialog = true
        },
        onInitClick = { context ->
            viewModel.loadInitialData(context)
        },
        onDeleteUser = { person ->
            viewModel.logicalDeletePerson(person)
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "${person.name} さんを削除しました",
                    actionLabel = "元に戻す",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.restorePerson(person)
                    // 復元した際に最上部へスクロールして、復活を確認しやすくする
                    scope.launch {
                        lazyListState.animateScrollToItem(0)
                    }
                    // 「元に戻す」が実行されたら、スナックバーを即座に閉じる
                    snackbarHostState.currentSnackbarData?.dismiss()
                }
            }
        },
        onNavigateToRestore = onNavigateToRestore
    )

    if (showSheet && selectedPerson != null) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            CategorySelectionSheet(
                personName = selectedPerson!!.name,
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
                if (editingPerson == null) {
                    viewModel.addPerson(person)
                } else {
                    viewModel.updatePerson(person)
                }
                showEditDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    userList: List<Person>,
    snackbarHostState: SnackbarHostState,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    onUserClick: (Person) -> Unit,
    onEditUser: (Person) -> Unit,
    onAddClick: () -> Unit,
    onInitClick: (android.content.Context) -> Unit,
    onDeleteUser: (Person) -> Unit,
    onNavigateToRestore: () -> Unit,
) {
    var searchText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    
    // インクリメンタルサーチのフィルタリング（前方一致）と、ふりがなでのソート
    val filteredList = remember(userList, searchText) {
        userList
            .filter { user ->
                searchText.isBlank() || (user.furigana?.startsWith(searchText) ?: false)
            }
            .sortedBy { it.furigana ?: "" }
    }
    
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("健康管理メモ帳", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { onInitClick(context) }) {
                        Text("データ読込", color = MaterialTheme.colorScheme.primary)
                    }
                    
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("削除データの復旧") },
                            onClick = {
                                showMenu = false
                                onNavigateToRestore()
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "利用者登録")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- 検索バー (SearchBar) ---
            SearchBar(
                query = searchText,
                onQueryChange = { searchText = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // --- 利用者一覧リスト ---
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState
            ) {
                items(filteredList, key = { it.id }) { user ->
                    val age = calculateAge(user.birthday)
                    val birthdayStr = formatToJapaneseEra(user.birthday)
                    
                    var showItemMenu by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.animateItem()) {
                        ListItem(
                            headlineContent = {
                                Column {
                                    Text(
                                        text = user.furigana ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = buildString {
                                            append(user.name)
                                            if (user.note.isNotBlank()) {
                                                append(" (${user.note})")
                                            }
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            supportingContent = {
                                Text(
                                    text = "${birthdayStr}生　${age}歳",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { showItemMenu = true }) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "操作メニュー"
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showItemMenu,
                                        onDismissRequest = { showItemMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("利用者情報を編集") },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                            onClick = {
                                                showItemMenu = false
                                                onEditUser(user)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("利用者を削除", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                showItemMenu = false
                                                onDeleteUser(user)
                                            }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onUserClick(user) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun CategorySelectionSheet(
    personName: String,
    onCategorySelect: (Category) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$personName さんの記録を選択",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp).align(Alignment.Start)
        )
        
        Category.entries.forEach { category ->
            Button(
                onClick = { onCategorySelect(category) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(category.displayName, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

enum class BirthEra(val displayName: String) {
    AD("西暦"), SHOWA("昭和"), HEISEI("平成"), REIWA("令和")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserEditDialog(
    person: Person?,
    onDismiss: () -> Unit,
    onSave: (Person) -> Unit,
) {
    var name by remember { mutableStateOf(person?.name ?: "") }
    var furigana by remember { mutableStateOf(person?.furigana ?: "") }
    var note by remember { mutableStateOf(person?.note ?: "") }
    
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val yearFocusRequester = remember { FocusRequester() }
    val monthFocusRequester = remember { FocusRequester() }
    val dayFocusRequester = remember { FocusRequester() }

    val initialDate = person?.birthday?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: LocalDate.now()
    
    // 元号の初期判定
    var selectedEra by remember { 
        mutableStateOf(
            if (person == null) {
                BirthEra.SHOWA 
            } else {
                when {
                    initialDate.year in 1926..1989 -> BirthEra.SHOWA
                    initialDate.year in 1990..2019 -> BirthEra.HEISEI
                    initialDate.year >= 2020 -> BirthEra.REIWA
                    else -> BirthEra.AD
                }
            }
        )
    }

    var yearText by remember { 
        mutableStateOf(
            if (person == null) {
                "" 
            } else {
                val y = initialDate.year
                when (selectedEra) {
                    BirthEra.SHOWA -> (y - 1925).toString()
                    BirthEra.HEISEI -> (y - 1988).toString()
                    BirthEra.REIWA -> (y - 2018).toString()
                    BirthEra.AD -> y.toString()
                }
            }
        )
    }
    var monthText by remember { mutableStateOf(if (person == null) "" else initialDate.monthValue.toString()) }
    var dayText by remember { mutableStateOf(if (person == null) "" else initialDate.dayOfMonth.toString()) }

    var eraExpanded by remember { mutableStateOf(false) }

    val yInput = yearText.toIntOrNull()
    val m = monthText.toIntOrNull()
    val d = dayText.toIntOrNull()

    // 西暦への変換ロジック
    val westernYear = yInput?.let {
        when (selectedEra) {
            BirthEra.SHOWA -> it + 1925
            BirthEra.HEISEI -> it + 1988
            BirthEra.REIWA -> it + 2018
            BirthEra.AD -> it
        }
    }

    val isYearError = yInput == null || when (selectedEra) {
        BirthEra.SHOWA -> yInput !in 1..64
        BirthEra.HEISEI -> yInput !in 1..31
        BirthEra.REIWA -> yInput !in 1..99
        BirthEra.AD -> yInput !in 1900..2100
    }
    
    val isMonthError = m == null || m !in 1..12
    val isDayError = try {
        if (westernYear != null && m != null && d != null) {
            d < 1 || d > YearMonth.of(westernYear, m).lengthOfMonth()
        } else {
            true
        }
    } catch (_: Exception) {
        true
    }

    val isInputValid = name.isNotBlank() && !isYearError && !isMonthError && !isDayError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (person == null) "利用者登録" else "登録情報の編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("氏名 (必須)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = furigana,
                    onValueChange = { furigana = it },
                    label = { Text("ふりがな") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("同姓同名識別用メモ (任意)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("例：302号室") }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("生年月日", style = MaterialTheme.typography.labelMedium)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 元号選択プルダウン (Compact風)
                    ExposedDropdownMenuBox(
                        expanded = eraExpanded,
                        onExpandedChange = { eraExpanded = !eraExpanded },
                        modifier = Modifier.weight(2.0f)
                    ) {
                        OutlinedTextField(
                            value = selectedEra.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = eraExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(
                            expanded = eraExpanded,
                            onDismissRequest = { eraExpanded = false }
                        ) {
                            BirthEra.entries.forEach { era ->
                                DropdownMenuItem(
                                    text = { Text(era.displayName, style = MaterialTheme.typography.bodyMedium) },
                                    onClick = {
                                        selectedEra = era
                                        eraExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 年月日は CompactTextField を再利用して余白を詰め、高さを揃える
                    CompactTextField(
                        value = yearText,
                        onValueChange = { 
                            val filtered = it.filter { char -> char.isDigit() }
                            val maxLength = if (selectedEra == BirthEra.AD) 4 else 2
                            if (filtered.length <= maxLength) {
                                yearText = filtered
                                if (filtered.length == maxLength) {
                                    monthFocusRequester.requestFocus()
                                }
                            }
                        },
                        modifier = Modifier.weight(1.2f).focusRequester(yearFocusRequester),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isYearError,
                        suffix = { Text("年", style = MaterialTheme.typography.labelSmall) }
                    )
                    CompactTextField(
                        value = monthText,
                        onValueChange = { 
                            val filtered = it.filter { char -> char.isDigit() }
                            if (filtered.length <= 2) {
                                monthText = filtered
                                if (filtered.length == 2) {
                                    dayFocusRequester.requestFocus()
                                }
                            }
                        },
                        modifier = Modifier.weight(0.9f).focusRequester(monthFocusRequester),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isMonthError,
                        suffix = { Text("月", style = MaterialTheme.typography.labelSmall) }
                    )
                    CompactTextField(
                        value = dayText,
                        onValueChange = { 
                            val filtered = it.filter { char -> char.isDigit() }
                            if (filtered.length <= 2) {
                                dayText = filtered
                            }
                        },
                        modifier = Modifier.weight(0.9f).focusRequester(dayFocusRequester),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isDayError,
                        suffix = { Text("日", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                
                // エラー時の補助テキスト (Rowの外に配置してレイアウト崩れを防ぐ)
                if (isYearError && yearText.isNotBlank()) {
                    Text(
                        text = when(selectedEra) {
                            BirthEra.SHOWA -> "昭和は1〜64年です"
                            BirthEra.HEISEI -> "平成は1〜31年です"
                            else -> "年の値が不正です"
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
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
                        onSave(
                            person?.copy(name = name, furigana = furigana, birthday = birthday, note = note)
                                ?: Person(name = name, furigana = furigana, birthday = birthday, note = note)
                        )
                    }
                },
                enabled = isInputValid
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val mockUserList = listOf(
        Person(
            id = 1,
            name = "山田 太郎",
            furigana = "ヤマダ タロウ",
            birthday = LocalDate.of(1950, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        ),
        Person(
            id = 2,
            name = "佐藤 花子",
            furigana = "サトウ ハナコ",
            birthday = LocalDate.of(1965, 5, 20).atStartOfDay(ZoneId.systemDefault()).toInstant()
        ),
        Person(
            id = 3,
            name = "鈴木 一郎",
            furigana = "スズキ イチロウ",
            birthday = LocalDate.of(1940, 11, 10).atStartOfDay(ZoneId.systemDefault()).toInstant()
        )
    )
    CareMemoTheme {
        MainScreenContent(
            userList = mockUserList, 
            snackbarHostState = remember { SnackbarHostState() },
            lazyListState = rememberLazyListState(),
            onUserClick = { }, 
            onEditUser = { },
            onAddClick = { },
            onInitClick = { },
            onDeleteUser = { },
            onNavigateToRestore = { }
        )
    }
}

fun calculateAge(birthday: Instant): Int {
    val birthDate = birthday.atZone(ZoneId.systemDefault()).toLocalDate()
    val currentDate = LocalDate.now()
    return Period.between(birthDate, currentDate).years
}

/**
 * Instant を和暦（例：昭和25年1月1日）の文字列に変換する
 */
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
        placeholder = { 
            Text(
                "ひらがなの読み名で入力してください",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    )
}
