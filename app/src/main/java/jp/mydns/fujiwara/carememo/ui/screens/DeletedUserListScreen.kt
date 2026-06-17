package jp.mydns.fujiwara.carememo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedUserListScreen(
    viewModel: PersonListViewModel,
    onBack: () -> Unit
) {
    val deletedUsers by viewModel.deletedUserList.collectAsState()
    val selectedUserIds = remember { mutableStateListOf<Int>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("削除データの復旧", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (deletedUsers.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (selectedUserIds.size == deletedUsers.size) {
                                    selectedUserIds.clear()
                                } else {
                                    selectedUserIds.clear()
                                    selectedUserIds.addAll(deletedUsers.map { it.id })
                                }
                            }
                        ) {
                            Text(if (selectedUserIds.size == deletedUsers.size) "全解除" else "全選択")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selectedUserIds.isNotEmpty()) {
                BottomAppBar {
                    Button(
                        onClick = {
                            selectedUserIds.forEach { id ->
                                deletedUsers.find { it.id == id }?.let { 
                                    viewModel.restorePerson(it)
                                }
                            }
                            selectedUserIds.clear()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Text("選択した利用者を復元する (${selectedUserIds.size}名)")
                    }
                }
            }
        }
    ) { paddingValues ->
        if (deletedUsers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("削除された利用者はいません", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {
                items(deletedUsers, key = { it.id }) { user ->
                    ListItem(
                        headlineContent = { Text(user.name) },
                        supportingContent = { Text(user.furigana ?: "") },
                        leadingContent = {
                            Checkbox(
                                checked = selectedUserIds.contains(user.id),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedUserIds.add(user.id)
                                    } else {
                                        selectedUserIds.remove(user.id)
                                    }
                                }
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
