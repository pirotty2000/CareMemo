package jp.mydns.fujiwara.carememo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.components.CategoryBadges
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
    onNavigateToRestore: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val userList by viewModel.userList.collectAsState()
    val endedUserList by viewModel.deletedUserList.collectAsState()
    val categorySummaries by viewModel.categorySummaries.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }
    
    var showEditDialog by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<Person?>(null) }

    // 確認用ダイアログ
    var showImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showLegacyFolderUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showEraseConfirm by remember { mutableStateOf(false) }
    var showDevClearConfirm by remember { mutableStateOf(false) }

    val errorMsg by viewModel.errorFlow.collectAsState()
    val infoMsg by viewModel.infoFlow.collectAsState()

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.snackbarFlow.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // ファイル・フォルダ選択ランチャー
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportData(context, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { showImportUri = it } }

    val legacyFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { showLegacyFolderUri = it } }

    if (errorMsg != null) {
        AlertDialog(onDismissRequest = { viewModel.clearError() }, title = { Text("エラー") }, text = { Text(errorMsg!!) }, confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } })
    }
    if (infoMsg != null) {
        AlertDialog(onDismissRequest = { viewModel.clearInfo() }, title = { Text("通知") }, text = { Text(infoMsg!!) }, confirmButton = { TextButton(onClick = { viewModel.clearInfo() }) { Text("OK") } })
    }

    if (showImportUri != null) {
        AlertDialog(onDismissRequest = { showImportUri = null }, title = { Text("データの復元") }, text = { Text("現在のデータはすべて削除され、選択したバックアップファイルの内容に置き換わります。よろしいですか？") }, confirmButton = { Button(onClick = { viewModel.importData(context, showImportUri!!); showImportUri = null }) { Text("復元を実行") } }, dismissButton = { TextButton(onClick = { showImportUri = null }) { Text("キャンセル") } })
    }

    if (showLegacyFolderUri != null) {
        AlertDialog(onDismissRequest = { showLegacyFolderUri = null }, title = { Text("旧アプリデータの引き継ぎ", color = MaterialTheme.colorScheme.error) }, text = { Text("現在のデータはすべて削除され、選択したフォルダ内の旧アプリ用JSONデータで初期化されます。一度きりの移行処理として実行してください。", color = MaterialTheme.colorScheme.error) }, confirmButton = { Button(onClick = { viewModel.importLegacyDataFromFolder(context, showLegacyFolderUri!!); showLegacyFolderUri = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("削除して引き継ぐ") } }, dismissButton = { TextButton(onClick = { showLegacyFolderUri = null }) { Text("キャンセル") } })
    }

    if (showEraseConfirm) {
        AlertDialog(
            onDismissRequest = { showEraseConfirm = false },
            title = { Text("個人情報の完全抹消", color = MaterialTheme.colorScheme.error) },
            text = { Text("現在「利用終了」となっている ${endedUserList.size} 名分のデータを完全に抹消します。この操作を行うと記録は二度と復旧できません。よろしいですか？", color = MaterialTheme.colorScheme.error) },
            confirmButton = { Button(onClick = { viewModel.deleteEndedPersons(); showEraseConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("対象者 (${endedUserList.size}名) を抹消する") } },
            dismissButton = { TextButton(onClick = { showEraseConfirm = false }) { Text("キャンセル") } }
        )
    }

    if (showDevClearConfirm) {
        AlertDialog(onDismissRequest = { showDevClearConfirm = false }, title = { Text("(開発用) 全データ消去", color = MaterialTheme.colorScheme.error) }, text = { Text("データベース内のすべてのデータを物理削除します。この操作は取り消せません。", color = MaterialTheme.colorScheme.error) }, confirmButton = { Button(onClick = { viewModel.clearAllData(); showDevClearConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("実行する") } }, dismissButton = { TextButton(onClick = { showDevClearConfirm = false }) { Text("キャンセル") } })
    }

    MainScreenContent(
        userList = userList,
        categorySummaries = categorySummaries,
        isDatabaseEmpty = userList.isEmpty() && endedUserList.isEmpty(),
        isNameMaskingEnabled = isNameMaskingEnabled,
        snackbarHostState = snackbarHostState,
        lazyListState = lazyListState,
        onUserClick = { person -> selectedPerson = person; showSheet = true },
        onEditUser = { person -> editingPerson = person; showEditDialog = true },
        onAddClick = { editingPerson = null; showEditDialog = true },
        onEndUser = { person ->
            viewModel.logicalDeletePerson(person)
            scope.launch {
                val fullName = person.getMaskedName(isNameMaskingEnabled)
                val result = snackbarHostState.showSnackbar(message = "$fullName さんの利用を終了しました", actionLabel = "元に戻す", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) { viewModel.restorePerson(person); scope.launch { lazyListState.animateScrollToItem(0) } }
            }
        },
        onNavigateToRestore = onNavigateToRestore,
        onNavigateToSettings = onNavigateToSettings,
        onExportClick = { exportLauncher.launch("carememo_backup_${System.currentTimeMillis()}.json") },
        onImportClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream")) },
        onLegacyFolderClick = { legacyFolderLauncher.launch(null) },
        onDevAssetsClick = { viewModel.importLegacyDataFromAssets(context) },
        onDevClearClick = { showDevClearConfirm = true },
        onEraseClick = { showEraseConfirm = true }
    )

    if (showSheet && selectedPerson != null) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            CategorySelectionSheet(personName = selectedPerson!!.getMaskedName(isNameMaskingEnabled), onCategorySelect = { category -> showSheet = false; onNavigateToDetail(selectedPerson!!.id, category) })
        }
    }

    if (showEditDialog) {
        UserEditDialog(person = editingPerson, onDismiss = { showEditDialog = false }, onSave = { person -> if (editingPerson == null) viewModel.addPerson(person) else viewModel.updatePerson(person); showEditDialog = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    userList: List<Person>,
    categorySummaries: Map<Int, PersonCategorySummary>,
    isDatabaseEmpty: Boolean,
    isNameMaskingEnabled: Boolean,
    snackbarHostState: SnackbarHostState,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    onUserClick: (Person) -> Unit,
    onEditUser: (Person) -> Unit,
    onAddClick: () -> Unit,
    onEndUser: (Person) -> Unit,
    onNavigateToRestore: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onLegacyFolderClick: () -> Unit,
    onDevAssetsClick: () -> Unit,
    onDevClearClick: () -> Unit,
    onEraseClick: () -> Unit,
) {
    var searchText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val kanaGroups = listOf("全", "あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ", "他")
    val kanaMap = mapOf(
        "あ" to "あいうえお",
        "か" to "かきくけこがぎぐげご",
        "さ" to "さしすせそざじずぜぞ",
        "た" to "たちつてとだぢづでど",
        "な" to "なにぬねの",
        "は" to "はひふへほばびぶべぼぱぴぷぺぽ",
        "ま" to "まみむめも",
        "や" to "やゆよ",
        "ら" to "らりるれろ",
        "わ" to "わをん"
    )

    // 50音タブに基づいたフィルタリング処理 (検索テキストは将来機能のため現在は無視)
    val filteredList = remember(userList, selectedTabIndex) {
        userList.filter { user ->
            // 50音タブによる絞り込み
            val group = kanaGroups[selectedTabIndex]
            when (group) {
                "全" -> true
                "他" -> {
                    val firstChar = user.lastNameFurigana.firstOrNull()
                    firstChar == null || firstChar !in '\u3041'..'\u3096'
                }
                else -> {
                    val firstChar = user.lastNameFurigana.firstOrNull()
                    firstChar != null && kanaMap[group]?.contains(firstChar) == true
                }
            }
        }.sortedWith(compareBy({ it.lastNameFurigana }, { it.firstNameFurigana }))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CareMemo", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "メニュー") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("設定") }, onClick = { showMenu = false; onNavigateToSettings() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("利用終了者の復帰") }, onClick = { showMenu = false; onNavigateToRestore() })
                        DropdownMenuItem(text = { Text("利用終了者のデータを完全に抹消") }, onClick = { showMenu = false; onEraseClick() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("データのバックアップ (保存)") }, onClick = { showMenu = false; onExportClick() })
                        DropdownMenuItem(text = { Text("データの復元 (読込)") }, onClick = { showMenu = false; onImportClick() })

                        // データベースが空の時のみ表示
                        if (isDatabaseEmpty) {
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("初期データの読込 (旧アプリ移行)") }, onClick = { showMenu = false; onLegacyFolderClick() })
                        }

                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("(開発用) assets読込") }, onClick = { showMenu = false; onDevAssetsClick() })
                        DropdownMenuItem(text = { Text("(開発用) データクリア") }, onClick = { showMenu = false; onDevClearClick() })
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { FloatingActionButton(onClick = onAddClick) { Icon(Icons.Default.Add, contentDescription = "利用者登録") } }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchBar(query = searchText, onQueryChange = { searchText = it })

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Start
            ) {
                kanaGroups.forEachIndexed { index, title ->
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(48.dp)
                            .clickable { selectedTabIndex = index },
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
                                color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selectedTabIndex == index) {
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
                items(filteredList, key = { it.id }) { user ->
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
    // 利用者をクリックした際に表示される、記録カテゴリー選択用のボトムシート
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
                    OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("姓") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("名") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = lastNameFurigana, onValueChange = { lastNameFurigana = it }, label = { Text("せい") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = firstNameFurigana, onValueChange = { firstNameFurigana = it }, label = { Text("めい") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("同姓同名識別用メモ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                Text("生年月日", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExposedDropdownMenuBox(expanded = eraExpanded, onExpandedChange = { eraExpanded = !eraExpanded }, modifier = Modifier.weight(2.0f)) {
                        OutlinedTextField(value = selectedEra.displayName, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = eraExpanded) }, colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(), modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
                        ExposedDropdownMenu(expanded = eraExpanded, onDismissRequest = { eraExpanded = false }) { BirthEra.entries.forEach { era -> DropdownMenuItem(text = { Text(era.displayName, style = MaterialTheme.typography.bodyMedium) }, onClick = { selectedEra = era; eraExpanded = false }) } }
                    }
                    CompactTextField(value = yearText, onValueChange = { val filtered = it.filter { char -> char.isDigit() }; val maxLength = if (selectedEra == BirthEra.AD) 4 else 2; if (filtered.length <= maxLength) { yearText = filtered; if (filtered.length == maxLength) monthFocusRequester.requestFocus() } }, modifier = Modifier.weight(1.2f).focusRequester(yearFocusRequester), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = isYearError, suffix = { Text("年", style = MaterialTheme.typography.labelSmall) })
                    CompactTextField(value = monthText, onValueChange = { val filtered = it.filter { char -> char.isDigit() }; if (filtered.length <= 2) { monthText = filtered; if (filtered.length == 2) dayFocusRequester.requestFocus() } }, modifier = Modifier.weight(0.9f).focusRequester(monthFocusRequester), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = isMonthError, suffix = { Text("月", style = MaterialTheme.typography.labelSmall) })
                    CompactTextField(value = dayText, onValueChange = { val filtered = it.filter { char -> char.isDigit() }; if (filtered.length <= 2) dayText = filtered }, modifier = Modifier.weight(0.9f).focusRequester(dayFocusRequester), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = isDayError, suffix = { Text("日", style = MaterialTheme.typography.labelSmall) })
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

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val mockUserList = listOf(Person(id = 1, lastName = "山田", firstName = "太郎", lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ", birthday = LocalDate.of(1950, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant()))
    CareMemoTheme { MainScreenContent(userList = mockUserList, categorySummaries = emptyMap(), isDatabaseEmpty = false, isNameMaskingEnabled = false, snackbarHostState = remember { SnackbarHostState() }, lazyListState = rememberLazyListState(), onUserClick = { }, onEditUser = { }, onAddClick = { }, onEndUser = { }, onNavigateToRestore = { }, onNavigateToSettings = { }, onExportClick = { }, onImportClick = { }, onLegacyFolderClick = { }, onDevAssetsClick = { }, onDevClearClick = { }, onEraseClick = { }) }
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
        label = { Text("所見メモ検索(仮)") },
        placeholder = { Text("特定のキーワードを含む利用者を検索（将来機能）", style = MaterialTheme.typography.bodyMedium, color = Color.Gray) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    )
}
