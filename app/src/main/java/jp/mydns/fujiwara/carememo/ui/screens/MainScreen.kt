package jp.mydns.fujiwara.carememo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.chrono.JapaneseDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToDetail: (Int, Category) -> Unit,
) {
    // val userList by viewModel.userList.collectAsState(initial = emptyList())
    val userList = listOf(
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

    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

    MainScreenContent(
        userList = userList,
        onUserClick = { person ->
            selectedPerson = person
            showSheet = true
        }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    userList: List<Person>,
    onUserClick: (Person) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("健康管理メモ帳", fontWeight = FontWeight.Bold) }
            )
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
                items(userList) { user ->
                    val age = calculateAge(user.birthday)
                    val birthdayStr = formatToJapaneseEra(user.birthday)
                    
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
                        modifier = Modifier.clickable { onUserClick(user) }
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
    onCategorySelect: (Category) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 48.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "$personName さんの記録を選択",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
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
        MainScreenContent(userList = mockUserList, onUserClick = { })
    }
}

fun calculateAge(birthday: Instant): Int {
    val birthDate = LocalDate.ofInstant(birthday, ZoneId.systemDefault())
    val currentDate = LocalDate.now()
    return Period.between(birthDate, currentDate).years
}

/**
 * Instant を和暦（例：昭和25年1月1日）の文字列に変換する
 */
fun formatToJapaneseEra(instant: Instant): String {
    val localDate = LocalDate.ofInstant(instant, ZoneId.systemDefault())
    val japaneseDate = JapaneseDate.from(localDate)
    val formatter = DateTimeFormatter.ofPattern("Gy年M月d日").withLocale(Locale.JAPAN)
    return japaneseDate.format(formatter)
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = { onQueryChange(it) },
        label = { Text("利用者名検索") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}
