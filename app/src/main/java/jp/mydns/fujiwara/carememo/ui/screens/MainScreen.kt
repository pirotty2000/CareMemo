package jp.mydns.fujiwara.carememo.ui.screens

/**
 * Screen : MainScreen
 *
 * 【画面名】
 * 利用者一覧（メイン画面）
 *
 * 【役割】
 * 登録された利用者（ケア対象者）の一覧を表示し、健康記録カテゴリへの橋渡しや、
 * 利用者情報の管理（登録・変更・サービス終了処理）を行うアプリのメインエントランス。。
 *
 * 【主な機能】
 * ・利用者一覧：名前（マスキング対応）、フリガナ、年齢、備考および最新の記録状況をバッジで表示。
 * ・絞り込み検索：五十音順インデックスによる絞り込みと、検索バーによるフリーワード検索。
 * ・利用者管理：ダイアログ形式での情報登録・編集、および論理削除（サービス終了）と復元（Undo）機能。
 * ・カテゴリ連携：利用者選択時に表示されるボトムシートから、バイタルや食事等の各記録画面へ遷移。
 * ・システムメニュー：アプリ設定、操作ヘルプ、バージョン情報の確認。
 *
 * 【遷移】
 * ← （アプリ起動）
 * → UnifiedRecordScreen / MedicationScreen（カテゴリ選択シート経由）
 * → SettingsScreen（オプションメニューより遷移）
 *
 * 【使用するViewModel】
 * PersonListViewModel
 *
 * 【備考】
 * UIの状態管理とイベント処理（Snackbar表示等）を担当。
 * データ操作および検索ロジックの本体は ViewModel に集約されている。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ModeEdit
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.EditNote
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.components.CategoryBadges
import jp.mydns.fujiwara.carememo.ui.components.CompactTextField
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel
import jp.mydns.fujiwara.carememo.BuildConfig
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PersonListViewModel,
    onNavigateToDetail: (Int, Category) -> Unit,
    onNavigateToBatchInput: (Int) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val userList by viewModel.userList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val categorySummaries by viewModel.categorySummaries.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()
    val selectedSection by viewModel.selectedSection.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val userEndedFormat = stringResource(R.string.snackbar_user_ended)
    val undoLabel = stringResource(R.string.undo)

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
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowInfoDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.SaveSuccess -> {
                    showEditDialog = false
                }
                jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.RequestPassword -> {
                    // MainScreenでは使用しない
                }
            }
        }
    }

    MainScreenContent(
        userList = userList,
        isLoading = isLoading,
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
                    message = userEndedFormat.format(fullName), 
                    actionLabel = undoLabel,
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
                    Text(stringResource(R.string.close))
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
                },
                onBatchInputSelect = {
                    showSheet = false
                    onNavigateToBatchInput(selectedPerson!!.id)
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
    isLoading: Boolean,
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
    var showHelpDialog by remember { mutableStateOf(false) }

    if (showVersionDialog) {
        AlertDialog(
            onDismissRequest = { showVersionDialog = false },
            title = { Text(stringResource(R.string.dialog_version_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("バージョン: ${BuildConfig.VERSION_NAME}")
                    HorizontalDivider()
                    Text("ターゲット環境:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Android 15 (API 35)")
                    Text("KYOCERA TORQUE G06 最適化済")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("(C) 2025-2026 pirotty.galaxy", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showVersionDialog = false }) { Text(stringResource(R.string.close)) } }
        )
    }

    if (showHelpDialog) {
        AlertDialog(onDismissRequest = { showHelpDialog = false }, title = { Text(stringResource(R.string.dialog_help_title)) }, text = { Text(stringResource(R.string.dialog_help_content)) }, confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text(stringResource(R.string.close)) } })
    }

    val kanaGroups = listOf("全", "あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ", "他")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.Menu, contentDescription = "メニュー") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_settings)) },
                            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            onClick = { showMenu = false; onNavigateToSettings() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_help)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Help, contentDescription = null) },
                            onClick = { showMenu = false; showHelpDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_version)) },
                            leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                            onClick = { showMenu = false; showVersionDialog = true }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { FloatingActionButton(onClick = onAddClick) { Icon(Icons.Rounded.PersonAddAlt1, contentDescription = stringResource(R.string.user_registration)) } }
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

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (userList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) stringResource(R.string.no_user_found) else stringResource(R.string.no_user_registered),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(userList, key = { it.id }) { user ->
                        val age = DateTimeUtils.calculateAge(user.birthday)
                        val birthdayStr = DateTimeUtils.formatDateJapaneseEra(user.birthday)
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
                                supportingContent = { Text(text = stringResource(R.string.birthday_summary_format, birthdayStr, stringResource(R.string.age_suffix, age)), style = MaterialTheme.typography.bodySmall) },
                                trailingContent = {
                                    Box {
                                        IconButton(onClick = { showItemMenu = true }) { Icon(Icons.Rounded.ModeEdit, contentDescription = "操作メニュー") }
                                        DropdownMenu(expanded = showItemMenu, onDismissRequest = { showItemMenu = false }) {
                                            DropdownMenuItem(text = { Text(stringResource(R.string.edit_user_info)) }, leadingIcon = { Icon(Icons.Rounded.ModeEdit, contentDescription = null) }, onClick = { showItemMenu = false; onEditUser(user) })
                                            DropdownMenuItem(text = { Text(stringResource(R.string.end_user_service), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }, onClick = { showItemMenu = false; onEndUser(user) })
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
}

@Composable
fun CategorySelectionSheet(
    personName: String,
    onCategorySelect: (Category) -> Unit,
    onBatchInputSelect: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.category_selection_title, personName), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp).align(Alignment.Start))
        
        // 【一括入力】ボタン
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

        Category.entries.forEach { category ->
            Button(onClick = { onCategorySelect(category) }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                Text(stringResource(category.displayNameRes), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

enum class BirthEra(val displayNameRes: Int) { 
    AD(R.string.era_ad), 
    SHOWA(R.string.era_showa), 
    HEISEI(R.string.era_heisei), 
    REIWA(R.string.era_reiwa) 
}

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
        title = { Text(if (person == null) stringResource(R.string.user_registration) else stringResource(R.string.user_edit)) },
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
                Text(stringResource(R.string.birthday), style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExposedDropdownMenuBox(expanded = eraExpanded, onExpandedChange = { eraExpanded = !eraExpanded }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(value = stringResource(selectedEra.displayNameRes), onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = eraExpanded) }, colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(), modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium)
                        ExposedDropdownMenu(expanded = eraExpanded, onDismissRequest = { eraExpanded = false }) { BirthEra.entries.forEach { era -> DropdownMenuItem(text = { Text(stringResource(era.displayNameRes), style = MaterialTheme.typography.bodyMedium) }, onClick = { selectedEra = era; eraExpanded = false }) } }
                    }
                    CompactTextField(value = yearText, onValueChange = { val filtered = it.filter { char -> char.isDigit() }; val maxLength = if (selectedEra == BirthEra.AD) 4 else 2; if (filtered.length <= maxLength) { yearText = filtered; if (filtered.length == maxLength) monthFocusRequester.requestFocus() } }, modifier = Modifier.weight(1f).focusRequester(yearFocusRequester), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), isError = isYearError, suffix = { Text(stringResource(R.string.year_suffix), style = MaterialTheme.typography.labelSmall) })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CompactTextField(value = monthText, onValueChange = { val filtered = it.filter { char -> char.isDigit() }; if (filtered.length <= 2) { monthText = filtered; if (filtered.length == 2) dayFocusRequester.requestFocus() } }, modifier = Modifier.weight(1f).focusRequester(monthFocusRequester), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), isError = isMonthError, suffix = { Text(stringResource(R.string.month_suffix), style = MaterialTheme.typography.labelSmall) })
                    CompactTextField(value = dayText, onValueChange = { val filtered = it.filter { char -> char.isDigit() }; if (filtered.length <= 2) dayText = filtered }, modifier = Modifier.weight(1f).focusRequester(dayFocusRequester), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), isError = isDayError, suffix = { Text(stringResource(R.string.day_suffix), style = MaterialTheme.typography.labelSmall) })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    westernYear?.let { y ->
                        m?.let { month ->
                            d?.let { day ->
                                keyboardController?.hide()
                                val birthday = LocalDate.of(y, month, day)
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
                        }
                    }
                },
                enabled = isInputValid
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}



@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = { onQueryChange(it) },
        label = { Text(stringResource(R.string.search_memo_placeholder)) },
        placeholder = { Text(stringResource(R.string.search_memo_hint), style = MaterialTheme.typography.bodyMedium, color = Color.Gray) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear))
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
            isLoading = false,
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
