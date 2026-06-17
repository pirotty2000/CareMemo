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
import androidx.compose.ui.platform.LocalContext
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
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
    onNavigateToRestore: () -> Unit
) {
    val userList by viewModel.userList.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }
    
    // 登録・編集ダイアログの状態
    var showEditDialog by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<Person?>(null) }

    MainScreenContent(
        userList = userList,
        snackbarHostState = snackbarHostState,
        onUserClick = { person ->
            selectedPerson = person
            showSheet = true
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
                },
                onEditPerson = {
                    showSheet = false
                    editingPerson = selectedPerson
                    showEditDialog = true
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
    onUserClick: (Person) -> Unit,
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
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredList, key = { it.id }) { user ->
                    val age = calculateAge(user.birthday)
                    val birthdayStr = formatToJapaneseEra(user.birthday)
                    
                    val currentOnDeleteUser by rememberUpdatedState(onDeleteUser)
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                currentOnDeleteUser(user)
                                true
                            } else {
                                false
                            }
                        }
                    )

                    // 項目が再表示された（復元された）ときに強制的にリセット
                    LaunchedEffect(user.id) {
                        dismissState.reset()
                    }

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val color = when (dismissState.dismissDirection) {
                                SwipeToDismissBoxValue.EndToStart -> Color.Gray.copy(alpha = 0.8f) // 論理削除はグレー
                                else -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Default.Inventory, // アーカイブアイコン
                                    contentDescription = "アーカイブ",
                                    tint = Color.White
                                )
                            }
                        },
                        content = {
                            ListItem(
                                headlineContent = {
                                    Column {
                                        Text(
                                            text = user.furigana ?: "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = user.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                supportingContent = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text("生年月日: $birthdayStr", style = MaterialTheme.typography.bodySmall)
                                        Text("年齢: ${age}歳", style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                trailingContent = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "カテゴリを選択"
                                    )
                                },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                    .clickable { onUserClick(user) }
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun CategorySelectionSheet(
    personName: String,
    onCategorySelect: (Category) -> Unit,
    onEditPerson: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 48.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$personName さんの記録を選択",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            IconButton(onClick = onEditPerson) {
                Icon(Icons.Default.Edit, contentDescription = "情報を編集")
            }
        }
        
        Category.entries.forEach { category ->
            Button(
                onClick = { onCategorySelect(category) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
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

@Composable
fun UserEditDialog(
    person: Person?,
    onDismiss: () -> Unit,
    onSave: (Person) -> Unit,
) {
    var name by remember { mutableStateOf(person?.name ?: "") }
    var furigana by remember { mutableStateOf(person?.furigana ?: "") }
    
    val initialDate = person?.birthday?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: LocalDate.now()
    var yearText by remember { mutableStateOf(initialDate.year.toString()) }
    var monthText by remember { mutableStateOf(initialDate.monthValue.toString()) }
    var dayText by remember { mutableStateOf(initialDate.dayOfMonth.toString()) }

    val y = yearText.toIntOrNull()
    val m = monthText.toIntOrNull()
    val d = dayText.toIntOrNull()

    val isYearError = y == null || y !in 1900..2100
    val isMonthError = m == null || m !in 1..12
    val isDayError = try {
        if (y != null && m != null && d != null) {
            d < 1 || d > YearMonth.of(y, m).lengthOfMonth()
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
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("生年月日", style = MaterialTheme.typography.labelMedium)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = { if (it.length <= 4) yearText = it },
                        modifier = Modifier.weight(1.5f),
                        label = { Text("年") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isYearError,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = monthText,
                        onValueChange = { if (it.length <= 2) monthText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("月") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isMonthError,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dayText,
                        onValueChange = { if (it.length <= 2) dayText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("日") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isDayError,
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isInputValid && y != null && m != null && d != null) {
                        val birthday = LocalDate.of(y, m, d)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                        onSave(
                            person?.copy(name = name, furigana = furigana, birthday = birthday)
                                ?: Person(name = name, furigana = furigana, birthday = birthday)
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
            onUserClick = { }, 
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
